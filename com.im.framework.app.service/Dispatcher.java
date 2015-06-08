package com.im.framework.app.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.im.framework.app.common.Command;
import com.im.framework.app.common.Command.CommandType;

import static com.im.framework.app.common.Command.COMMAND_BODY;
import static com.im.framework.app.common.Command.COMMAND_TYPE;
import static com.im.framework.app.common.Command.HOME_ACTIVITY;
import static com.im.framework.app.common.Command.MESSAGE_ACTIVITY;

import com.im.framework.app.service.CMessage.MessageItem;
import com.im.framework.app.service.CMessage.MessageStatus;
import com.im.framework.app.service.CMessage.MessageType;
import com.im.framework.app.ui.HomeActivity;
import com.im.framework.app.ui.MessageActivity;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;

public class Dispatcher extends Service {

  private static DispatcherInterface dispatcherService;

  private static final String bindServiceAction = "BIND_MESSAGE_SERVICE_FROM_DISPATCHER";
  private MessageServiceInterface messageServiceProxy;
  private final ReentrantLock messageServiceLock = new ReentrantLock();
  private Condition messageServiceProxyExist = messageServiceLock.newCondition();
  private MessageServiceConnection currentConnection;

  private Map<String, Handler> handlerMap = new HashMap<String, Handler>();

  private List<CMessage> pendingMessages = new ArrayList<CMessage>();
  private boolean reallyReceive = false;

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  class MessageServiceConnection implements ServiceConnection {

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
      messageServiceProxy = MessageServiceInterface.Stub.asInterface(service);
      if (messageServiceProxy != null) {
        messageServiceLock.lock();
        try {
          messageServiceProxyExist.signalAll();
        } finally {
          messageServiceLock.unlock();
        }
        CMessage[] receiveMessages;
        try {
          receiveMessages = messageServiceProxy.attachService(getDispatcherService());
        } catch (RemoteException re) {
          return;
        }
        if (receiveMessages != null && receiveMessages.length > 0) {
          doReceiveMessage(receiveMessages);
        }
      }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      messageServiceProxy = null;
      // remove current connection,then rebind the message service.
      if (currentConnection != this) return;
      Dispatcher.this.unbindService(this);
      currentConnection = new MessageServiceConnection();
      Dispatcher.this.bindService(new Intent(bindServiceAction), currentConnection, Context.BIND_AUTO_CREATE);
    }

  }

  @Override
  public void onCreate() {
    currentConnection = new MessageServiceConnection();
    this.bindService(new Intent(bindServiceAction), currentConnection, Context.BIND_AUTO_CREATE);
  }

  @Override
  public void onDestroy() {
    this.unbindService(currentConnection);
  }

  private void doReceiveMessage(CMessage[] messages) {
    Handler homeHandler = handlerMap.get(HOME_ACTIVITY);
    Handler messageHandler = handlerMap.get(MESSAGE_ACTIVITY);
    if (homeHandler != null || messageHandler != null) {
      if (homeHandler != null) {
        // home activity only show the latest message.
        Map<String, List<CMessage>> sourceMessage = new HashMap<String, List<CMessage>>();
        for (CMessage cm : messages) {
          if (filterMessageInHome(cm)) {
            List<CMessage> mList = sourceMessage.get(cm.getmSource());
            if (mList == null) {
              mList = new ArrayList<CMessage>();
              sourceMessage.put(cm.getmSource(), mList);
            }
            mList.add(cm);
          }
        }
        for (List<CMessage> valueList : sourceMessage.values()) {
          int lastIndex = valueList.size() - 1;
          Message msg = obtainMessageInHandler(valueList.get(lastIndex), homeHandler);
          msg.arg1 = valueList.size();
          homeHandler.sendMessage(msg);
        }
        reallyReceive = false;
      }
      if (messageHandler != null) {
        for (int i = 0; i < messages.length; i++) {
          Message msg = obtainMessageInHandler(messages[i], messageHandler);
          messageHandler.sendMessage(msg);
        }
        reallyReceive = true;
      }
      if (!reallyReceive) {
        for (CMessage cm : messages) {
          pendingMessages.add(cm);
        }
      }
    } else {
      for (CMessage cm : messages) {
        pendingMessages.add(cm);
      }
    }
  }

  private boolean filterMessageInHome(CMessage cm) {
    if (cm.getmSource() != null && cm.getmType() != -1 && cm.getmContents() != null) return true;
    return false;
  }

  private Message obtainMessageInHandler(CMessage cm, Handler handler) {
    Message message = handler.obtainMessage();
    message.what = cm.getmType();
    message.arg2 = cm.getmId();
    if (cm.getmSource() != null && cm.getmContents() != null) {
      Bundle data = new Bundle();
      data.putString(MessageItem.SOURCE, cm.getmSource());
      data.putByteArray(MessageItem.CONTENTS, cm.getmContents());
      message.setData(data);
    }
    return message;
  }

  private final class DispatcherService extends DispatcherInterface.Stub {

    @Override
    public void receiveMessage(CMessage message) throws RemoteException {
      doReceiveMessage(new CMessage[] {message});
    }

    @Override
    public void notifySendingStatus(int id, int status) throws RemoteException {
      Handler messageHandler = handlerMap.get(MESSAGE_ACTIVITY);
      if (messageHandler != null) {
        Message msg = messageHandler.obtainMessage(MessageType.STATUS.ordinal(), id, status);
        messageHandler.sendMessage(msg);
      }
    }

  }

  private DispatcherInterface getDispatcherService() {
    if (dispatcherService == null) {
      dispatcherService = new DispatcherService();
    }
    return dispatcherService;
  }

  private void checkProxyExist() {
    if (messageServiceProxy == null) {
      messageServiceLock.lock();
      try {
        while (messageServiceProxy == null) {
          messageServiceProxyExist.await();
        }
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      } finally {
        messageServiceLock.unlock();
      }
    }
  }

  private void doSendMessage(CMessage message) {
    final int sendNum = 3;
    int i;
    for (i = 0; i < sendNum;) {
      checkProxyExist();
      try {
        messageServiceProxy.sendMessage(message);
        break;
      } catch (RemoteException re) {
        i++;
      }
      try {
        Thread.sleep(100);
      } catch (InterruptedException ie) {}
    }
    if (i == sendNum) {
      Handler messageHandler = handlerMap.get(MESSAGE_ACTIVITY);
      if (messageHandler != null) {
        Message msg = messageHandler.obtainMessage(MessageType.STATUS.ordinal(), message.getmId(), MessageStatus.FAILURE.ordinal());
        messageHandler.sendMessage(msg);
      }
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent != null && intent.getAction().equals("START_DISPATCHER_FROM_ACTIVITY")) {
      int type = intent.getIntExtra(COMMAND_TYPE, 0);
      Bundle data = intent.getBundleExtra(COMMAND_BODY);
      switch (CommandType.values()[type]) {
        case REGISTER_HANDLER: {
          String activityName = data.getString(Command.BODY_FIELD_ARG1);
          if (HOME_ACTIVITY.equals(activityName)) {
            handlerMap.put(activityName, HomeActivity.getHomeHandler());
          }
          if (MESSAGE_ACTIVITY.equals(activityName)) {
            handlerMap.put(activityName, MessageActivity.getMessageHandler());
          }
          if (pendingMessages.size() > 0) {
            doReceiveMessage(pendingMessages.toArray(new CMessage[pendingMessages.size()]));
            if (reallyReceive) {
              pendingMessages.clear();
            }
          }
          break;
        }
        case SEND_MESSAGE: {
          CMessage cm = new CMessage();
          cm.setmDest(data.getString(Command.BODY_FIELD_ARG1));
          cm.setmId(data.getInt(Command.BODY_FIELD_ARG2));
          cm.setmType((byte) data.getInt(Command.BODY_FIELD_ARG3));
          cm.setmContents(data.getByteArray(Command.BODY_FIELD_ARG4));
          doSendMessage(cm);
          break;
        }
        case UNREGISTER_HANDLER: {
          String activityName = data.getString(Command.BODY_FIELD_ARG1);
          Handler handler = handlerMap.remove(activityName);
          for (MessageType mt : MessageType.values()) {
            handler.removeMessages(mt.ordinal());
          }
          handler = null;
        }
        default:
          break;
      }
    }
    return super.onStartCommand(intent, flags, startId);
  }

}
