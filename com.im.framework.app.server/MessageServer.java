package com.im.framework.app.server;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class MessageServer {

  private byte[] serverAddress = new byte[4]; // ipv4 address;
  private int port; // bind port;
  private static final int DEFAULT_SERVER_LISTEN_QUEUE_SIZE = 128;
  private boolean running = true;

  private int readThreads;
  private Listener listener;

  private Map<String, Connection> connectionMap = Collections.synchronizedMap(new LinkedHashMap<String, Connection>());
  private Map<String, List<DriverRecord>> offlineRecords = new HashMap<String, List<DriverRecord>>();

  private MessageDriver messageDriver;

  private void bind(ServerSocket serverSocket, int backLog) throws IOException {
    IOException ioe = null;
    try {
      InetAddress ip = InetAddress.getByAddress(this.serverAddress);
      InetSocketAddress bindAddress = new InetSocketAddress(ip, this.port);
      serverSocket.bind(bindAddress, backLog <= 0 ? DEFAULT_SERVER_LISTEN_QUEUE_SIZE : backLog);
    } catch (UnknownHostException uhe) {
      ioe = new IOException(uhe.getCause());
    } catch (SocketException se) {
      ioe = new IOException(se.getCause());
    } finally {
      if (ioe != null) throw ioe;
    }
  }

  private class Listener extends Thread {

    private ServerSocketChannel acceptChannel = null; // the accept channel;
    private Selector selector = null; // the selector that we use for the server;
    private Reader[] readers = null;
    private int currentReader = 0;
    private Random cleanupRand = new Random();

    public Listener() throws IOException {
      // Create a new server socket and set to non blocking mode.
      acceptChannel = ServerSocketChannel.open();
      acceptChannel.configureBlocking(false);
      bind(acceptChannel.socket(), 0);
      port = acceptChannel.socket().getLocalPort(); // Could be an ephemeral port.
      // create a selector;
      selector = Selector.open();
      readers = new Reader[readThreads];
      for (int i = 0; i < readThreads; i++) {
        Reader reader = new Reader("Socket Reader #" + (i + 1) + " for port " + port);
        readers[i] = reader;
        reader.start();
      }
      // Register accepts on the server socket with the selector.
      acceptChannel.register(selector, SelectionKey.OP_ACCEPT);
      this.setName("Message Server listener on " + port);
      this.setDaemon(true);
    }

    Reader getReader() {
      currentReader = (currentReader + 1) % readers.length;
      return readers[currentReader];
    }

    private class Reader extends Thread {

      private volatile boolean adding = false;
      private final Selector readSelector;
      private BlockingQueue<Connection>[] readerQueues;
      private static final int DEFAULT_READER_QUEUE_SIZE = 10;
      private ThreadPoolExecutor readWorker;

      @SuppressWarnings("unchecked")
      Reader(String name) throws IOException {
        super(name);
        this.readSelector = Selector.open();
        readerQueues = new LinkedBlockingQueue[DEFAULT_READER_QUEUE_SIZE];
        readWorker = (ThreadPoolExecutor) Executors.newFixedThreadPool(DEFAULT_READER_QUEUE_SIZE);
        for (int i = 0; i < DEFAULT_READER_QUEUE_SIZE; i++) {
          readerQueues[i] = new LinkedBlockingQueue<Connection>();
          final BlockingQueue<Connection> currentQueue = readerQueues[i];
          readWorker.submit(new Runnable() {
            @Override
            public void run() {
              try {
                readRunnable(currentQueue);
              } catch (Throwable e) {
                e.printStackTrace();
                throw new RuntimeException(e);
              }
            }
          });
        }
      }

      @Override
      public void run() {
        try {
          doRunLoop();
        } finally {
          try {
            readSelector.close();
          } catch (IOException ioe) {

          }
        }
      }

      private synchronized void doRunLoop() {
        while (running) {
          SelectionKey key = null;
          try {
            readSelector.select();
            while (adding) {
              this.wait(1000);
            }

            Iterator<SelectionKey> iter = readSelector.selectedKeys().iterator();
            while (iter.hasNext()) {
              key = iter.next();
              iter.remove();
              if (key.isValid()) {
                if (key.isReadable()) {
                  doRead(key);
                }
              }
              key = null;
            }
          } catch (InterruptedException e) {
            if (!running) {
              Thread.currentThread().interrupt();
            }
          } catch (IOException ex) {

          }
        }
      }

      private void doRead(SelectionKey key) {
        Connection c = (Connection) key.attachment();
        // System.out.println("read "+key.toString());
        SocketChannel sc = c.getChannel();
        int index = sc.hashCode() % DEFAULT_READER_QUEUE_SIZE;
        readerQueues[index].add(c);
      }

      private void readRunnable(BlockingQueue<Connection> runnableQueue) throws Exception {
        final BlockingQueue<Connection> queue = runnableQueue;
        while (running) {
          Connection c = queue.take();
          if (c == null) {
            return;
          }
          c.readAndProcess();
        }
      }

      /**
       * This gets reader into the state that waits for the new channel to be registered with
       * readSelector. If it was waiting in select() the thread will be woken up, otherwise whenever
       * select() is called it will return even if there is nothing to read and wait in
       * while(adding) for finishAdd call
       */
      public void startAdd() {
        adding = true;
        readSelector.wakeup();
      }

      public synchronized SelectionKey registerChannel(SocketChannel channel) throws IOException {
        return channel.register(readSelector, SelectionKey.OP_READ);
      }

      public synchronized void finishAdd() {
        adding = false;
        this.notify();
      }

      void shutdown() {
        assert !running;
        readWorker.shutdownNow();
        readSelector.wakeup();
        try {
          join();
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
        }
      }
    }

    @Override
    public void run() {
      while (running) {
        SelectionKey key = null;
        try {
          selector.select();
          Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
          while (iter.hasNext()) {
            key = iter.next();
            iter.remove();
            try {
              if (key.isValid()) {
                if (key.isAcceptable()) {
                  doAccept(key);
                }
              }
            } catch (IOException e) {}
            key = null;
          }
        } catch (OutOfMemoryError e) {
          // we can run out of memory if we have too many threads
          // so sleep for a minute and give
          // some threads a chance to finish.
          cleanupConnections(true);
          try {
            Thread.sleep(60000);
          } catch (Exception ie) {}
        } catch (Exception e) {
          System.out.println("exception " + e.getMessage());
          if (key != null) {
            Connection c = (Connection) key.attachment();
            if (c != null) {
              c.close();
              c = null;
            }
          }
        }
        cleanupConnections(false);
      }
      // the server stop.
      synchronized (this) {
        try {
          acceptChannel.close();
          selector.close();
        } catch (IOException e) {}
        selector = null;
        acceptChannel = null;
        // clean up all connections.
        for (String user : connectionMap.keySet()) {
          Connection c = connectionMap.remove(user);
          c.close();
          c = null;
        }
      }
    }

    private void cleanupConnections(boolean force) {
      Set<String> users = connectionMap.keySet();
      if (users.isEmpty()) return;
      String[] us = users.toArray(new String[users.size()]);
      if (force) {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage memoryUsage = memoryBean.getHeapMemoryUsage();
        for (int i = us.length - 1; i >= 0; i--) {
          Connection connect = connectionMap.remove(us[i]);
          connect.close();
          connect = null;
          if ((float) memoryUsage.getUsed() / (float) memoryUsage.getMax() < 0.8) {
            break;
          }
        }
      } else {
        int numConnections = us.length;
        System.out.println(numConnections);
        int start = cleanupRand.nextInt() % numConnections;
        int end = cleanupRand.nextInt() % numConnections;
        int temp;
        if (end < start) {
          temp = start;
          start = end;
          end = temp;
        }
        int i = start;
        while (i <= end) {
          Connection connect = connectionMap.get(us[i]);
          if (!connect.getChannel().isOpen() || System.currentTimeMillis() - connect.lastPing > 3600 * 1000) {
            connectionMap.remove(us[i]);
            connect.close();
            connect = null;
          }
          i++;
        }
      }
    }

    private void doAccept(SelectionKey key) throws IOException, OutOfMemoryError {
      Connection c = null;
      ServerSocketChannel server = (ServerSocketChannel) key.channel();
      SocketChannel channel;
      while ((channel = server.accept()) != null) {
        channel.configureBlocking(false);
        channel.socket().setTcpNoDelay(true);
        Reader reader = getReader();
        try {
          reader.startAdd();
          SelectionKey readKey = reader.registerChannel(channel);
          System.out.println("reg " + readKey.toString());
          c = new Connection(readKey, channel);
          readKey.attach(c);
        } finally {
          reader.finishAdd();
        }
      }
    }

    synchronized void doStop() {
      if (selector != null) {
        selector.wakeup();
        Thread.yield();
      }
      if (acceptChannel != null) {
        try {
          acceptChannel.socket().close();
        } catch (IOException e) {

        }
      }
      for (Reader r : readers) {
        r.shutdown();
      }
    }
  }

  class Connection {

    private SocketChannel sc;

    private String owner;
    private long lastPing;

    private ByteBuffer readBuffer;
    private Map<Integer, List<DriverRecord>> blockBuffer = new ConcurrentHashMap<Integer, List<DriverRecord>>();
    private ExecutorService blockSender = Executors.newCachedThreadPool();;
    // TODO the blockBuffer and pendingRecords may consume many memory,so in the future will use
    // secondary cache
    // or big data storage solution such as hbase to replace this simple cache.
    private NavigableMap<Integer, DriverRecord> pendingRecords = new ConcurrentSkipListMap<Integer, DriverRecord>();
    private AtomicInteger recordId = new AtomicInteger();
    private int lastMaxReplyId;

    public Connection(SelectionKey sk, SocketChannel sc) {
      this.sc = sc;
    }

    public SocketChannel getChannel() {
      return this.sc;
    }

    public long getLastPing() {
      return this.lastPing;
    }

    public void readAndProcess() throws IOException {
      readBuffer = ByteBuffer.allocate(1);
      SMessage.channelIO(sc, readBuffer, false);
      readBuffer.rewind();
      MessageType magic = MessageType.values()[readBuffer.get()];
      prepareMessage();
      switch (magic) {
        case TEXT: {
          processPlainMessage(readBuffer);
          break;
        }
        case MULTIPART_HEAD: {
          processMultipartHead(readBuffer);
          break;
        }
        case MULTIPART_BLOCK: {
          processMultipartBlock(readBuffer);
          break;
        }
        case PING: {
          if (owner == null) {
            byte[] contents = new byte[readBuffer.remaining()];
            readBuffer.get(contents);
            owner = new String(contents);
            System.out.println(owner + " is online.");
            Connection current = connectionMap.get(owner);
            if (current != this) {
              // this is the new connection.
              connectionMap.put(owner, this);
              if (current != null) {
                this.blockBuffer = current.blockBuffer;
                this.pendingRecords = current.pendingRecords;
                this.recordId = current.recordId;
                this.lastMaxReplyId = current.lastMaxReplyId;
                for (DriverRecord cloneRecord : pendingRecords.values()) {
                  handleRecord(cloneRecord, this);
                }
              } else {
                // check offline records,if have some relate with owner,handle them.
                List<DriverRecord> records = offlineRecords.get(owner);
                if (records != null && records.size() > 0) {
                  synchronized (records) {
                    while (!records.isEmpty()) {
                      handleRecord(records.remove(0), this);
                    }
                  }
                }
              }
            }
          }
          lastPing = System.currentTimeMillis();
          break;
        }
        case REPLY: {
          int replyId = readBuffer.getInt();
          if (replyId != -1) {
            lastMaxReplyId = Math.max(replyId, lastMaxReplyId);
            ReplyStatus status = ReplyStatus.values()[readBuffer.get()];
            if (status == ReplyStatus.R_SUCCESS) {
              DriverRecord record = pendingRecords.remove(replyId);
              System.out.println("reply " + replyId);
              if (record.getMagic() == MessageType.MULTIPART_HEAD) {
                int multipartId = record.getMultipartId();
                Connection owner = connectionMap.get(record.owner);
                List<DriverRecord> blockList = owner.blockBuffer.remove(multipartId);
                blockList.clear();
                blockList = null;
                System.out.println("block clear " + multipartId);
              }
              record = null;
            } else if (status == ReplyStatus.R_FAILURE) {
              handleRecord(pendingRecords.get(replyId), this);
            }
          } else {
            for (DriverRecord record : pendingRecords.headMap(lastMaxReplyId).values()) {
              handleRecord(record, this);
            }
          }
        }
        case IMAGE:
        case AUDIO:
        case VEDIO: {
          // not happen in this case,handle this in multipart code.
          break;
        }
      }
    }

    private void prepareMessage() throws IOException {
      readBuffer = ByteBuffer.allocate(4);
      SMessage.channelIO(sc, readBuffer, false);
      readBuffer.rewind();
      int length = readBuffer.getInt();
      readBuffer = ByteBuffer.allocate(length);
      SMessage.channelIO(sc, readBuffer, false);
      readBuffer.rewind();
    }

    private void processPlainMessage(ByteBuffer message) {
      int length = message.getInt();
      byte[] who = new byte[length];
      message.get(who);
      length = message.getInt();
      byte[] texts = new byte[length];
      message.get(texts);
      DriverRecord dr = new DriverRecord(owner, new String(who));
      System.out.print(owner + " send a text to " + new String(who) + " :");
      dr.setMagic(MessageType.TEXT);
      dr.setContents(texts);
      messageDriver.addInDriver(dr);
    }

    private void processMultipartHead(ByteBuffer head) {
      int multipartId = head.getInt();
      MessageType type = MessageType.values()[head.get()];
      int length = head.getInt();
      byte[] who = new byte[length];
      head.get(who);
      length = head.getInt();
      byte[] format = new byte[length];
      head.get(format);
      DriverRecord dr = new DriverRecord(owner, new String(who));
      dr.setMagic(MessageType.MULTIPART_HEAD);
      dr.setMultiparType(type);
      dr.setMultipartId(multipartId);
      dr.setFormat(new String(format));
      blockBuffer.put(multipartId, new CopyOnWriteArrayList<DriverRecord>());
      messageDriver.addInDriver(dr);
      System.out.println(owner + " send multipart" + multipartId + " to " + new String(who));
    }

    private void processMultipartBlock(ByteBuffer block) {
      int multipartId = block.getInt();
      byte[] buffer = new byte[block.remaining() - 1];
      block.get(buffer);
      byte last = block.get();
      DriverRecord dr = new DriverRecord();
      dr.setMagic(MessageType.MULTIPART_BLOCK);
      dr.setMultipartId(multipartId);
      dr.setContents(buffer);
      dr.setLastBlock(last == 1 ? true : false);
      List<DriverRecord> blockList = blockBuffer.get(multipartId);
      if (blockList == null) {
        return;
      }
      blockList.add(dr);
      System.out.println("receive block " + multipartId + " " + last);
    }

    public void addRecord(DriverRecord dr) {
      int id = recordId.getAndIncrement();
      if (id == Integer.MAX_VALUE - 1) {
        recordId = new AtomicInteger();
      }
      dr.setId(id);
      pendingRecords.put(id, dr);
    }

    public void runNewBlockTask(DriverRecord head, Connection reference) {
      if (reference == null) return;
      List<DriverRecord> blockList = new ArrayList<DriverRecord>();
      if (reference.blockBuffer != null) {
        blockList = reference.blockBuffer.get(head.getMultipartId());
      }
      blockSender.execute(new BlockTask(head, blockList, reference));
    }

    private class BlockTask implements Runnable {
      private DriverRecord blockHead;
      private List<DriverRecord> blockList;
      private Connection ref;

      BlockTask(DriverRecord blockHead, List<DriverRecord> blockList, Connection ref) {
        this.blockHead = blockHead;
        this.blockList = blockList;
        this.ref = ref;
      }

      @Override
      public void run() {
        try {
          // first send the head.
          SMessage head = new SMessage(blockHead.getId(), blockHead.owner, (byte) blockHead.getMultiparType().ordinal());
          head.setFileFormat(blockHead.getFormat());
          synchronized (Connection.this) {
            head.writeMultipartHead(sc);
            System.out.println("send head " + blockHead.getId());
          }
          // then write block one by one.
          int seqNum = 0;
          for (;;) {
            if (blockList.size() - seqNum > 0) {
              SMessage block = new SMessage();
              block.setId(blockHead.getId());
              DriverRecord blockRecord = blockList.get(seqNum++);
              block.writeMultipartBlock(sc, blockRecord.getContents(), blockRecord.isLastBlock());
              System.out.println("send block " + seqNum + " " + blockRecord.isLastBlock());
              if (blockRecord.isLastBlock()) break;
            } else {
              try {
                Thread.sleep(100);
              } catch (InterruptedException ie) {

              }
              if (ref == null || System.currentTimeMillis() - ref.getLastPing() > 60100 || !ref.getChannel().isOpen()) {
                // means the source client has disconnected.
                SMessage fail = new SMessage();
                fail.setId(blockHead.getId());
                fail.writeMultipartFailure(sc);
                break;
              }
            }
          }
        } catch (IOException cause) {
          throw new RuntimeException(cause);
        }
      }
    }

    public void close() {
      readBuffer = null;
      blockBuffer.clear();
      blockBuffer = null;
      pendingRecords.clear();
      pendingRecords = null;
      blockSender.shutdown();
      List<Runnable> taskList = blockSender.shutdownNow();
      if (taskList.size() > 0) {
        try {
          blockSender.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {}
      }
      if (sc.isOpen()) {
        try {
          sc.close();
        } catch (IOException ioe) {}
      }
    }
  }

  class MessageDriver {
    private static final int DEFAULT_DRIVER_QUEUE_SIZE = 100;
    private int size;
    private BlockingQueue<DriverRecord>[] driverQueues;
    private DriverHandler[] handlers;
    private ReentrantLock[] stealLocks;
    private volatile boolean[] hasSteal;
    private BlockingQueue<PlainRecord> plainQueue = new LinkedBlockingQueue<PlainRecord>();
    private PlainSender[] plainSenders;

    @SuppressWarnings("unchecked")
    public MessageDriver(int initSize) {
      this.size = initSize <= 0 ? DEFAULT_DRIVER_QUEUE_SIZE : initSize;
      driverQueues = new LinkedBlockingQueue[size];
      stealLocks = new ReentrantLock[size];
      hasSteal = new boolean[size];
      handlers = new DriverHandler[size];
      plainSenders = new PlainSender[size];
      for (int i = 0; i < size; i++) {
        driverQueues[i] = new LinkedBlockingQueue<DriverRecord>();
        stealLocks[i] = new ReentrantLock();
        handlers[i] = new DriverHandler(driverQueues[i], i);
        handlers[i].start();
        plainSenders[i] = new PlainSender(i);
        plainSenders[i].start();
      }
    }

    private class DriverHandler extends Thread {
      private int queueIndex;
      private BlockingQueue<DriverRecord> queue;
      private List<DriverRecord> drainList = new LinkedList<DriverRecord>();

      DriverHandler(BlockingQueue<DriverRecord> queue, int index) {
        super();
        this.queue = queue;
        this.queueIndex = index;
        this.setDaemon(true);
      }

      @Override
      public void run() {
        try {
          while (running) {
            // block at this.
            DriverRecord record = queue.take();
            drainList.add(0, record);
            // before drain our queue,first check our queue size,
            // if too small,we can steal rest queue which the size is max.
            int sum = 0, maxSize = 0, max = 0;
            for (int i = 0; i < driverQueues.length; i++) {
              if (driverQueues[i].size() > maxSize) {
                maxSize = driverQueues[i].size();
                max = i;
              }
              sum += driverQueues[i].size();
            }
            int avgSize = sum / driverQueues.length;
            if (queue.size() < avgSize / 2) {
              // means too small,just steal half of the max queue.
              boolean acquired = stealLocks[max].tryLock();
              try {
                if (acquired) {
                  hasSteal[max] = true;
                  synchronized (driverQueues[max]) {
                    driverQueues[max].drainTo(drainList, maxSize / 2);
                    hasSteal[max] = false;
                    driverQueues[max].notifyAll();
                  }
                }
              } finally {
                if (acquired) {
                  stealLocks[max].unlock();
                }
              }
            }
            // if our queue has steal by another handler,wait here.
            synchronized (queue) {
              while (hasSteal[queueIndex]) {
                queue.wait();
              }
              if (max == queueIndex) {
                queue.drainTo(drainList, maxSize / 2);
              } else {
                queue.drainTo(drainList);
              }
            }
            doDrain(drainList);
          }
        } catch (Throwable e) {
          e.printStackTrace();
          if (!running) Thread.currentThread().interrupt();
        }
      }

      private void doDrain(List<DriverRecord> recordList) {
        while (recordList.size() > 0) {
          DriverRecord record = recordList.remove(0);
          Connection sender = connectionMap.get(record.who);
          if (sender != null) {
            handleRecord(record, sender);
          } else {
            // the user is in offline state.
            List<DriverRecord> records = offlineRecords.get(record.who);
            if (records == null) {
              records = new ArrayList<DriverRecord>();
              offlineRecords.put(record.who, records);
            }
            synchronized (records) {
              records.add(record);
            }
          }
        }
      }
    }

    private class PlainSender extends Thread {

      PlainSender(int index) {
        super("Plain Sender " + index);
        this.setDaemon(true);
      }

      @Override
      public void run() {
        while (running) {
          try {
            PlainRecord plainRecord = plainQueue.take();
            synchronized (plainRecord.sender) {
              plainRecord.writePlain();
            }
          } catch (Throwable e) {
            if (e instanceof InterruptedException && !running) return;
          }
        }
      }

    }

    class PlainRecord {
      private SMessage plain;
      private Connection sender;

      PlainRecord(SMessage plain, Connection sender) {
        this.plain = plain;
        this.sender = sender;
      }

      void writePlain() throws IOException {
        plain.writePlain(sender.getChannel());
      }

    }

    public void addInDriver(DriverRecord record) {
      int index = record.hashCode() % size;
      driverQueues[index].add(record);
    }

    public void addPlainRecord(SMessage plainMessage, Connection sender) {
      plainQueue.add(new PlainRecord(plainMessage, sender));
    }

    public void doStop() {
      for (int i = 0; i < size; i++) {
        handlers[i].interrupt();
        plainSenders[i].interrupt();
      }
    }
  }

  void handleRecord(DriverRecord record, Connection sender) {
    sender.addRecord(record);
    MessageType magic = record.getMagic();
    if (magic == MessageType.MULTIPART_HEAD) {
      sender.runNewBlockTask(record, connectionMap.get(record.owner));
    } else if (magic == MessageType.TEXT) {
      SMessage plainMessage = new SMessage(record.getId(), record.owner, (byte) record.getMagic().ordinal());
      plainMessage.setContents(record.getContents());
      messageDriver.addPlainRecord(plainMessage, sender);
    }
  }

  public MessageServer(byte[] ip, int port, int readThreads, int driverInitSize) throws IOException {
    if (ip.length != 4) throw new IllegalArgumentException();
    System.arraycopy(ip, 0, this.serverAddress, 0, this.serverAddress.length);
    this.port = port;
    this.readThreads = readThreads;
    this.listener = new Listener();
    this.messageDriver = new MessageDriver(driverInitSize);
  }

  public synchronized void start() {
    listener.start();
    System.out.println("server started!");
  }

  public synchronized void stop() {
    running = false;
    listener.interrupt();
    listener.doStop();
    messageDriver.doStop();
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 4) throw new IllegalArgumentException();
    String[] ips = args[0].split("/");
    byte[] ip = new byte[ips.length];
    for (int i = 0; i < ip.length; i++) {
      ip[i] = Integer.valueOf(ips[i]).byteValue();
    }
    int port = Integer.valueOf(args[1]).intValue();
    int readThreads = Integer.valueOf(args[2]).intValue();
    int driverInitSize = Integer.valueOf(args[3]).intValue();
    MessageServer server = new MessageServer(ip, port, readThreads, driverInitSize);
    server.start();
  }

  static class DriverRecord {
    private int id;
    private String owner;
    private String who;
    private MessageType magic;
    private MessageType multiparType; // used in multipart messag;
    private int multipartId;
    private byte[] contents;
    private String format;
    private boolean lastBlock;

    DriverRecord() {

    }

    DriverRecord(String owner, String who) {
      this.owner = owner;
      this.who = who;
    }

    public void setId(int id) {
      this.id = id;
    }

    public int getId() {
      return id;
    }

    public void setMagic(MessageType magic) {
      this.magic = magic;
    }

    public MessageType getMagic() {
      return magic;
    }

    public void setMultiparType(MessageType multiparType) {
      this.multiparType = multiparType;
    }

    public MessageType getMultiparType() {
      return multiparType;
    }

    public void setContents(byte[] contents) {
      this.contents = contents;
    }

    public byte[] getContents() {
      return contents;
    }

    public void setMultipartId(int multipartId) {
      this.multipartId = multipartId;
    }

    public int getMultipartId() {
      return multipartId;
    }

    public void setFormat(String format) {
      this.format = format;
    }

    public String getFormat() {
      return format;
    }

    public boolean isLastBlock() {
      return lastBlock;
    }

    public void setLastBlock(boolean lastBlock) {
      this.lastBlock = lastBlock;
    }

    @Override
    public int hashCode() {
      return this.owner.hashCode() + this.who.hashCode();
    }

  }

  enum ReplyStatus { // used in connection reply.
    R_SUCCESS, R_FAILURE
  }

}
