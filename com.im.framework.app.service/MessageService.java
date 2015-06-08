package com.im.framework.app.service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.im.framework.app.common.BackStorage;
import com.im.framework.app.common.SMessage;
import com.im.framework.app.service.CMessage.MessageStatus;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class MessageService extends Service {

  private ConnectivityManager cm;

  private volatile boolean created = false;
  private volatile boolean appProcessDead = true;
  private boolean initError = false;
  private int attachNum;

  private Connection connect;

  private BackStorage backStorage;
  private AtomicLong seqNum;

  private static MessageServiceInterface messageService;
  private DispatcherInterface dispatcherServiceProxy; // the dispatcher service use in the main app
                                                      // process;

  private Timer pingApp = new Timer("ping app process", true);

  private boolean checkNetworkAvailable() {
    if (cm != null) {
      NetworkInfo net = cm.getActiveNetworkInfo();
      if (net == null) {
        return false;
      } else {
        if (net.isConnected()) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void onCreate() {
    if (created) return;
    Log.d("create", "true");
    cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    backStorage = new BackStorage(this);
    SharedPreferences preference = getSharedPreferences("IM_PREFERENCE", Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
    String user = preference.getString("user", "HOT");
    seqNum = new AtomicLong(backStorage.getMaxSeq(user));
    String host = preference.getString("host", "192/168/1/105");
    int port = preference.getInt("port", 9090);
    connect = new Connection(user, host, port);
    if (checkNetworkAvailable()) {
      connect.start();
    } else {
      created = false;
      // wait network become available and broadcast receiver receive an
      // intent to create service in onReceive();
    }
  }


  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent != null && intent.getAction().equals("START_MESSAGE_SERVICE_FROM_RECEIVER")) {
      if (initError || !created) {
        connect = new Connection(connect.user, connect.host, connect.serverPort);
        connect.start();
      }
    }
    return super.onStartCommand(intent, flags, startId);
  }


  private class Connection extends Thread {

    private final String user;
    private final String host;
    private byte[] serverHost = new byte[4]; // ipv4 address;
    private final int serverPort;
    private SocketChannel client;
    private Selector selector;
    private Receiver mReceiver;
    private boolean registering;

    private AtomicInteger multipartId = new AtomicInteger();

    private Map<Integer, SMessage> pendingSendMessages = new ConcurrentHashMap<Integer, SMessage>();
    private List<CMessage> pendingReceiveMessages = new ArrayList<CMessage>();
    private List<ByteBuffer> replyMessages = new LinkedList<ByteBuffer>();

    private boolean pingScheduled = false;
    private ScheduledExecutorService pingExecutor = Executors.newScheduledThreadPool(1);

    private void schedulePing(long initialDelay) {
      if (pingScheduled && !((ScheduledThreadPoolExecutor) pingExecutor).getQueue().isEmpty()) return;

      pingExecutor.scheduleAtFixedRate(new Runnable() {
        private boolean serverError = false;

        @Override
        public void run() {
          if (serverError) return;
          try {
            Log.i("ping", "done");
            sendPing();
          } catch (Throwable e) {
            Log.i("sp", e.getMessage());
            /**
             * connection error, repair the connection. 1.if current client channel has closed or
             * not; 2.device network is not available; 3.remote server does not run.
             */
            if (!checkNetworkAvailable()) {
              return;
            } else {
              try {
                try {
                  if (client.isOpen()) {
                    client.close();
                  }
                } finally {
                  connnetToServer(true);
                }
              } catch (Throwable exceptions) {
                if (exceptions instanceof IOException) {
                  IOException ioes = (IOException) exceptions;
                  if (!(ioes instanceof ClosedChannelException)) {
                    // means other IOException,we don't know.
                    // remove current runnable in the pingExecutor,
                    // reschedule,then throw the runtime exception.
                    serverError = true;
                    ((ScheduledThreadPoolExecutor) pingExecutor).getQueue().clear();
                    schedulePing(10);
                    RuntimeException re = new RuntimeException(ioes);
                    throw re;
                  }
                }
              }
            }
          }
        }
      }, initialDelay, 60, TimeUnit.SECONDS);
      pingScheduled = true;
    }

    private void setChannel(SocketChannel channel) {
      this.client = channel;
    }

    public Connection(String user, String host, int port) {
      super();
      this.user = user;
      this.host = host;
      int i = 0;
      for (String ip : host.split("/")) {
        serverHost[i++] = Integer.valueOf(ip).byteValue();
      }
      this.serverPort = port;
    }

    @Override
    public void run() {
      try {
        init();
        created = true;
        initError = false;
      } catch (IOException ioe) {
        Log.e("init", ioe.getMessage());
        initError = true;
      }
    }

    public void init() throws IOException {
      this.selector = Selector.open();
      connnetToServer(false);
      mReceiver = new Receiver();
      mReceiver.start();
    }

    private void connnetToServer(boolean reconnect) throws IOException {
      InetAddress ip = InetAddress.getByAddress(this.serverHost);
      SocketAddress server = new InetSocketAddress(ip, serverPort);
      // StrictMode.setThreadPolicy(new
      // StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build());
      SocketChannel sc = SocketChannel.open();
      sc.configureBlocking(false);
      if (reconnect) {
        registering = true;
        selector.wakeup();
      }
      sc.register(selector, SelectionKey.OP_CONNECT);
      if (reconnect) {
        synchronized (selector) {
          registering = false;
          selector.notifyAll();
        }
      }
      sc.connect(server);
      if (!reconnect) {
        setChannel(sc);
      }
    }

    private void sendPing() throws IOException {
      SMessage pm = SMessage.createPing(user);
      ByteBuffer ping = pm.writePing();
      SMessage.channelIO(client, ping, true);
      Log.i("ping", user);
    }

    private void finishConnect() throws IOException {
      boolean connected = false;
      try {
        while (client.isConnectionPending()) {
          Log.i("cp", String.valueOf(client.isConnectionPending()));
          connected = client.finishConnect();
        }
      } finally {
        if (!pingScheduled) {
          Log.i("ping", "true");
          schedulePing(0);
        }
      }
      if (connected || client.isConnected()) {
        Log.i("connect", "finish");
        if (pingScheduled) {
          // only send ping once,let server know the channel belong to current user.
          sendPing();
        }
        // check if the pending messages map is not empty,just resend.
        if (pendingSendMessages.size() > 0) {
          for (Map.Entry<Integer, SMessage> entry : pendingSendMessages.entrySet()) {
            int id = entry.getKey();
            SMessage message = entry.getValue();
            MessageStatus status = null;
            ByteBuffer messageBuffer = message.write();
            try {
              if (messageBuffer != null) {
                SMessage.channelIO(client, messageBuffer, true);
              } else {
                message.writeMultiPart(client, multipartId.incrementAndGet());
              }
              status = MessageStatus.SUCCESS;
              backStorage.putWithRetry(message, seqNum.incrementAndGet(), System.currentTimeMillis(), user, true);
            } catch (Exception e) {
              status = MessageStatus.FAILURE;
            } finally {
              pendingSendMessages.remove(id);
              try {
                if (dispatcherServiceProxy != null) {
                  dispatcherServiceProxy.notifySendingStatus(id, status.ordinal());
                }
              } catch (RemoteException re) {

              }
            }
          }
        }
        // check if has reply messages don't send at before.
        if (!replyMessages.isEmpty()) {
          while (replyMessages.size() > 0) {
            ByteBuffer reply = replyMessages.remove(0);
            try {
              SMessage.channelIO(client, reply, true);
            } catch (Exception e) {

            }
          }
        }
      }
    }

    class Receiver extends Thread {

      Receiver() {
        super("MessageReceiver Thread");
        this.setDaemon(true);
      }

      @Override
      public void run() {
        try {
          while (true) {
            try {
              selector.select();
              synchronized (selector) {
                while (registering) {
                  selector.wait(1000);
                }
              }
              Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
              while (keyIterator.hasNext()) {
                SelectionKey sk = keyIterator.next();
                keyIterator.remove();
                if (sk.isConnectable()) {
                  SocketChannel channel = (SocketChannel) sk.channel();
                  Log.i("connect", channel.toString());
                  channel.register(selector, SelectionKey.OP_READ);
                  if (client != channel) { // this is the reconnect case.
                    SocketChannel last = client;
                    setChannel(channel);
                    for (Iterator<SelectionKey> iter = selector.keys().iterator(); iter.hasNext();) {
                      if (iter.next().channel().equals(last)) {
                        iter.remove();
                      }
                    }
                  }
                  finishConnect();
                } else if (sk.isValid() && sk.isReadable()) {
                  boolean readSuccess = false;
                  SocketChannel current = (SocketChannel) sk.channel();
                  if (!current.isConnected()) {
                    sk.cancel();
                    continue;
                  }
                  Log.i("reads", current.toString());
                  ByteBuffer buffer = ByteBuffer.allocate(5); // type + length;
                  byte messageType = 0;
                  try {
                    SMessage.channelIO(current, buffer, false);
                    buffer.rewind();
                    messageType = buffer.get();
                    int messageLength = buffer.getInt();
                    buffer = ByteBuffer.allocate(messageLength);
                    SMessage.channelIO(current, buffer, false);
                    buffer.rewind();
                    Log.i("read", buffer.toString());
                    readSuccess = true;
                  } catch (Exception re) {
                    readSuccess = false;
                    SMessage reply;
                    try {
                      int replyId = buffer.getInt();
                      SMessage orig = new SMessage();
                      orig.setId(replyId);
                      reply = SMessage.createReply(orig, false);
                    } catch (BufferUnderflowException bue) {
                      reply = SMessage.createReply(null, false);
                    }
                    if (reply != null) {
                      ByteBuffer replyBuffer = reply.writeReply();
                      replyMessages.add(replyBuffer);
                    }
                  } finally {
                    if (readSuccess) {
                      processMessage(messageType, buffer, current);
                    }
                    if (client != current) {
                      // the current is the old channel,just close.
                      if (current.isOpen()) {
                        current.close();
                      }
                    }
                  }
                }
              }
            } catch (Throwable e) {
              if (e instanceof InterruptedException) {
                throw (InterruptedException) e;
              }
            }
          }
        } catch (InterruptedException ie) {
          return;
        }
      }

    }

    protected void sendMessageInternal(int mId, String dest, byte type, byte[] contents) {
      SMessage message = new SMessage(dest, type, contents);
      ByteBuffer writeBuffer = message.write();
      MessageStatus status = null;
      try {
        if (writeBuffer != null) {
          SMessage.channelIO(client, writeBuffer, true);
        } else {
          // this means we will write multipart block.
          message.writeMultiPart(client, multipartId.incrementAndGet());
        }
        status = MessageStatus.SUCCESS;
        backStorage.putWithRetry(message, seqNum.incrementAndGet(), System.currentTimeMillis(), user, true);
      } catch (Throwable e) {
        pendingSendMessages.put(mId, message);
        status = MessageStatus.PENDING;
      } finally {
        try {
          if (dispatcherServiceProxy != null) {
            dispatcherServiceProxy.notifySendingStatus(mId, status.ordinal());
          }
        } catch (RemoteException re) {

        }
      }
    }

    protected void close() {
      if (selector != null) {
        selector.wakeup();
      }
      if (mReceiver != null) {
        mReceiver.interrupt();
      }
      pingExecutor.shutdownNow();
      if (!pingExecutor.isTerminated()) {
        try {
          pingExecutor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {

        }
      }
      try {
        if (created) {
          client.close();
          selector.close();
        }
      } catch (IOException ioe) {

      }
    }
  }

  protected void processMessage(byte type, ByteBuffer message, SocketChannel channel) {
    SMessage sm = SMessage.getInstance(type, this);
    sm.read(type, message);
    if (sm.getEnvironment() == null || (sm.getEnvironment() != null && sm.isHead())) {
      backStorage.putWithRetry(sm, seqNum.incrementAndGet(), System.currentTimeMillis(), connect.user, false);
    }
    CMessage cm = sm.convertToReceive();
    if (cm != null) {
      if (dispatcherServiceProxy != null) {
        try {
          if (!appProcessDead) {
            dispatcherServiceProxy.receiveMessage(cm);
          } else {
            // check app process again.
            if (dispatcherServiceProxy.asBinder().isBinderAlive()) {
              dispatcherServiceProxy.receiveMessage(cm);
            } else {
              synchronized (connect.pendingReceiveMessages) {
                connect.pendingReceiveMessages.add(cm);
              }
            }
          }
        } catch (RemoteException re) {
          synchronized (connect.pendingReceiveMessages) {
            connect.pendingReceiveMessages.add(cm);
          }
        }
      } else {
        // main app process has doesn't start,send notification to user.
      }
    }
    SMessage reply = SMessage.createReply(sm, true);
    if (reply != null) {
      Log.i("reply", reply.getId() + "");
      ByteBuffer replyBuffer = reply.writeReply();
      try {
        SMessage.channelIO(channel, replyBuffer, true);
      } catch (Throwable e) {
        connect.replyMessages.add(replyBuffer);
      }
    }
  }

  protected void doAttach(DispatcherInterface dispatcherService) throws RemoteException {
    this.dispatcherServiceProxy = dispatcherService;
    this.appProcessDead = false;
    if (++attachNum <= 1) {
      pingApp.schedule(new TimerTask() {

        private IBinder getBinder() {
          return MessageService.this.dispatcherServiceProxy.asBinder();
        }

        @Override
        public void run() {
          IBinder binderProxy = getBinder();
          MessageService.this.appProcessDead = !binderProxy.pingBinder();
        }
      }, 0, 1000);
    }
    IBinder bp = this.dispatcherServiceProxy.asBinder();
    bp.linkToDeath(new IBinder.DeathRecipient() {

      @Override
      public void binderDied() {
        MessageService.this.appProcessDead = true;
        MessageService.this.sendBroadcast(new Intent("START_DISPATCHER_ACTION"), "RECEIVE_START_FROM_REMOTE");
      }
    }, 0);
    int pid = Binder.getCallingPid();
    if (initError) {
      // if this service init encounter an error,we should stop the main
      // app process.
      Process.killProcess(pid);
    }
  }

  private final class MessageServiceImpl extends MessageServiceInterface.Stub {

    @Override
    public void sendMessage(CMessage message) throws RemoteException {
      int mId = message.getmId();
      String mDest = message.getmDest();
      byte mType = message.getmType();
      byte[] mContents = message.getmContents();
      if (connect != null) {
        connect.sendMessageInternal(mId, mDest, mType, mContents);
      }
    }

    @Override
    public CMessage[] attachService(DispatcherInterface dispatcherService) throws RemoteException {
      doAttach(dispatcherService);
      if (dispatcherService.asBinder().isBinderAlive()) {
        if (connect.pendingReceiveMessages.isEmpty()) {
          return new CMessage[0];
        } else {
          synchronized (connect.pendingReceiveMessages) {
            int size = connect.pendingReceiveMessages.size();
            CMessage[] receiveMessages = connect.pendingReceiveMessages.toArray(new CMessage[size]);
            connect.pendingReceiveMessages.clear();
            return receiveMessages;
          }
        }
      }
      return null;
    }

  }

  private MessageServiceInterface getMessageService() {
    if (messageService == null) {
      messageService = new MessageServiceImpl();
    }
    return messageService;
  }

  @Override
  public IBinder onBind(Intent intent) {

    return getMessageService().asBinder();
  }

  @Override
  public void onDestroy() {
    messageService = null;
    if (connect != null) {
      connect.close();
    }
    connect = null;
    cm = null;
    backStorage.close();
    backStorage = null;
    pingApp.cancel();
    pingApp.purge();
    pingApp = null;
    dispatcherServiceProxy = null;
  }

  @Override
  public boolean onUnbind(Intent intent) {
    messageService = null;
    return super.onUnbind(intent);
  }

}
