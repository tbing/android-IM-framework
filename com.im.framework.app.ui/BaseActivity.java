package com.im.framework.app.ui;

import java.lang.ref.WeakReference;

import com.im.framework.app.common.Command;
import com.im.framework.app.common.Command.CommandType;
import com.im.framework.app.service.CMessage.MessageType;

import static com.im.framework.app.common.Command.COMMAND_TYPE;
import static com.im.framework.app.common.Command.COMMAND_BODY;
import static com.im.framework.app.common.Command.EMPTY_BYTE_ARRAY;
import static com.im.framework.app.common.Command.NO_INT_FIELD;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

public abstract class BaseActivity extends Activity {

  private static final String serviceAction = "START_DISPATCHER_FROM_ACTIVITY";

  protected ViewObserver viewObserver;

  protected void registerViewObserver(ViewObserver viewObserver) {
    this.viewObserver = viewObserver;
  }

  interface ViewObserver {

    public void handleTextView(Bundle data);

    public void handleImageView(Bundle data, int id);

    public void handleAudioView(Bundle data, int id);

    public void handleVedioView(Bundle data, int id);

    public void handleSendingStatus(int id, int status);
  }

  protected void handleAnotherMessage(Message another) {

  }

  static class MHandler extends Handler {

    protected WeakReference<BaseActivity> holder;

    MHandler(BaseActivity activity) {
      super();
      holder = new WeakReference<BaseActivity>(activity);
    }

    @Override
    public void handleMessage(Message msg) {
      MessageType type = MessageType.values()[msg.what];
      switch (type) {
        case TEXT: {
          holder.get().viewObserver.handleTextView(msg.getData());
          break;
        }
        case IMAGE: {
          holder.get().viewObserver.handleImageView(msg.getData(), msg.arg2);
          break;
        }
        case AUDIO: {
          holder.get().viewObserver.handleAudioView(msg.getData(), msg.arg2);
          break;
        }
        case VEDIO: {
          holder.get().viewObserver.handleVedioView(msg.getData(), msg.arg2);
          break;
        }
        case STATUS: {
          holder.get().viewObserver.handleSendingStatus(msg.arg1, msg.arg2);;
          break;
        }
        default: {
          holder.get().handleAnotherMessage(msg);
          break;
        }
      }
    }

  }

  protected void dispatchCommand(CommandType type, Bundle data) {
    Intent command = new Intent(serviceAction);
    command.putExtra(COMMAND_TYPE, type.ordinal());
    command.putExtra(COMMAND_BODY, data);
    this.startService(command);
  }

  protected Bundle buildCommandBody(String arg1, int arg2, int arg3, byte[] arg4) {
    Bundle body = new Bundle();
    if (arg1 != null) {
      body.putString(Command.BODY_FIELD_ARG1, arg1);
    }
    if (arg2 >= 0) {
      body.putInt(Command.BODY_FIELD_ARG2, arg2);
    }
    if (arg3 >= 0) {
      body.putInt(Command.BODY_FIELD_ARG3, arg3);
    }
    if (arg4.length > 0) {
      body.putByteArray(Command.BODY_FIELD_ARG4, arg4);
    }
    return body;
  }

  protected void registerHandler(String activityName) {
    Bundle registerHandlerBody = buildCommandBody(activityName, NO_INT_FIELD, NO_INT_FIELD, EMPTY_BYTE_ARRAY);
    dispatchCommand(CommandType.REGISTER_HANDLER, registerHandlerBody);
  }

  protected void sendMessage(String who, int id, int type, byte[] contents) {
    Bundle messageBody = buildCommandBody(who, id, type, contents);
    dispatchCommand(CommandType.SEND_MESSAGE, messageBody);
  }

  protected void unregisterHandler(String activityName) {
    Bundle unregisterHandlerBody = buildCommandBody(activityName, NO_INT_FIELD, NO_INT_FIELD, EMPTY_BYTE_ARRAY);
    dispatchCommand(CommandType.UNREGISTER_HANDLER, unregisterHandlerBody);
  }
}
