package com.im.framework.app.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.im.framework.app.service.CMessage;

public class SMessage {

  private static final int DEFAULT_BLOCK_SIZE = 64 * 1024;
  private long reallyContentsLength; // used in file;
  private String fileFormat = null;
  private Map<Integer, FileRecord> multipartRef;

  private int id;
  private String who;
  private byte type;
  private byte[] contents;

  private Context environment;
  private static SMessage singleInstance;
  private Map<Integer, Result> multipartResult;
  private boolean isHead;
  private boolean isBlock;
  private static boolean notifyMultipartSuccess;
  private static int successId;

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public String getWho() {
    return who;
  }

  public void setWho(String who) {
    this.who = who;
  }

  public byte getType() {
    return type;
  }

  public void setType(byte type) {
    this.type = type;
  }

  public byte[] getContents() {
    return contents;
  }

  public void setContents(byte[] contents) {
    this.contents = contents;
  }

  public Context getEnvironment() {
    return environment;
  }

  public boolean isHead() {
    return isHead;
  }

  public static SMessage getInstance(byte type, Context context) {
    if (type == 0) {
      return new SMessage();
    } else {
      if (singleInstance == null) {
        singleInstance = new SMessage(context);
      }
      return singleInstance;
    }
  }

  @SuppressLint("UseSparseArrays")
  // used in multipart message.
  SMessage(Context context) {
    this.environment = context;
    multipartRef = new HashMap<Integer, FileRecord>();
    multipartResult = new HashMap<Integer, Result>();
  }

  public SMessage() {

  }

  public SMessage(String who, byte type, byte[] contents) {
    this.who = who;
    this.type = type;
    this.contents = contents;
  }

  public ByteBuffer write() {
    if (MessageType.values()[type] != MessageType.TEXT) {
      File file = new File(new String(contents));
      reallyContentsLength = file.length();
      String fileName = file.getName();
      fileFormat = fileName.substring(fileName.lastIndexOf('.') + 1);
      return null;
    } else {
      return writePlain();
    }
  }

  private ByteBuffer writePlain() {
    int totalLength = who.length() + contents.length + 8;
    ByteBuffer writeBuffer = ByteBuffer.allocate(totalLength + 5);
    writeBuffer.put(type).putInt(totalLength).putInt(who.length()).put(who.getBytes()).putInt(contents.length).put(contents);
    writeBuffer.position(0);
    return writeBuffer;
  }

  public void writeMultiPart(SocketChannel client, int multipartId) throws IOException {
    writeMultipartHead(client, multipartId);
    writeMultipartBlock(client);
  }

  private void writeMultipartHead(SocketChannel channel, int multipartId) throws IOException {
    this.id = multipartId;
    int headLength = 13 + who.length() + fileFormat.length();
    ByteBuffer headBuffer = ByteBuffer.allocate(headLength + 5);
    headBuffer.put((byte) MessageType.MULTIPART_HEAD.ordinal()).putInt(headLength).putInt(id).put(type).putInt(who.length()).put(who.getBytes()).putInt(fileFormat.length()).put(fileFormat.getBytes());
    headBuffer.position(0);
    channelIO(channel, headBuffer, true);
    Log.i("writehead", "done");
    Log.i("length", reallyContentsLength + "");
  }

  private void writeMultipartBlock(SocketChannel channel) throws IOException {
    int blockNum = reallyContentsLength % DEFAULT_BLOCK_SIZE == 0 ? (int) (reallyContentsLength / DEFAULT_BLOCK_SIZE) : (int) (reallyContentsLength / DEFAULT_BLOCK_SIZE) + 1;
    long size = reallyContentsLength;
    FileInputStream fis = new FileInputStream(new String(contents));
    FileChannel fc = fis.getChannel();
    try {
      for (int i = 0; i < blockNum; i++) {
        int blockSize = size < DEFAULT_BLOCK_SIZE ? (int) size : DEFAULT_BLOCK_SIZE;
        ByteBuffer blockBuffer = ByteBuffer.allocate(blockSize);
        while (blockBuffer.hasRemaining()) {
          fc.read(blockBuffer);
        }
        blockBuffer.rewind();
        ByteBuffer writeBuffer = ByteBuffer.allocate(10 + blockSize);
        writeBuffer.put((byte) MessageType.MULTIPART_BLOCK.ordinal()).putInt(blockSize + 5).putInt(id).put(blockBuffer);
        // indicate whether this block is the last.
        if (i == blockNum - 1) {
          writeBuffer.put((byte) 1);
        } else {
          writeBuffer.put((byte) 0);
        }
        writeBuffer.position(0);
        channelIO(channel, writeBuffer, true);
        size = size - blockSize;
        Log.i("writeblock", i + "");
      }
    } finally {
      fc.close();
      fis.close();
    }
  }

  public void read(byte type, ByteBuffer buffer) {
    switch (MessageType.values()[type]) {
      case TEXT: {
        readPlainMessage(buffer);
        break;
      }
      case MULTIPART_HEAD: {
        isHead = true;
        readMultipartHead(buffer);
        break;
      }
      case MULTIPART_BLOCK: {
        isBlock = true;
        readMultipartBlock(buffer);
        break;
      }
      case PING:
      case REPLY:
      case IMAGE:
      case AUDIO:
      case VEDIO:
        break;
    }
  }

  private void readPlainMessage(ByteBuffer buffer) {
    setType((byte) 0);
    setId(buffer.getInt());
    int length = buffer.getInt();
    byte[] who = new byte[length];
    buffer.get(who);
    setWho(new String(who));
    length = buffer.getInt();
    byte[] texts = new byte[length];
    buffer.get(texts);
    setContents(texts);
  }

  private void readMultipartHead(ByteBuffer buffer) {
    setId(buffer.getInt());
    setType(buffer.get());
    int length = buffer.getInt();
    byte[] who = new byte[length];
    buffer.get(who);
    setWho(new String(who));
    length = buffer.getInt();
    byte[] format = new byte[length];
    buffer.get(format);
    this.fileFormat = new String(format);
    File basePath = environment.getFilesDir();
    MessageType mt = MessageType.values()[getType()];
    String fileDir = null;
    if (mt == MessageType.AUDIO) {
      fileDir = "AUDIO";
    } else if (mt == MessageType.IMAGE) {
      fileDir = "IMAGE";
    } else if (mt == MessageType.VEDIO) {
      fileDir = "VEDIO";
    }
    if (!createDir(basePath, fileDir)) return;
    basePath = new File(basePath, fileDir);
    String fileName = Long.toString(System.currentTimeMillis());
    fileName = fileName.substring(fileName.length() - 10) + "." + fileFormat;
    File newFile = new File(basePath, fileName);
    RandomAccessFile raf = null;
    try {
      raf = new RandomAccessFile(newFile, "rwd");
    } catch (FileNotFoundException e) {
      return;
    }
    setContents(newFile.getAbsolutePath().getBytes());
    FileRecord fr = new FileRecord(newFile, raf);
    multipartRef.put(getId(), fr);
    multipartResult.put(getId(), new Result(getType(), false));
    Log.i("readhead", "done");
  }

  private boolean createDir(File base, String dirName) {
    File dir = new File(base, dirName);
    if (dir.mkdir() || dir.isDirectory()) return true;
    return false;
  }

  private void readMultipartBlock(ByteBuffer buffer) {
    try {
      int multipartId = buffer.getInt();
      FileRecord fr;
      if (multipartId == -1) {
        multipartId = buffer.getInt();
        fr = multipartRef.remove(multipartId);
        if (fr != null) {
          fr.writer.close();
          fr.file.delete();
        }
        fr = null;
      } else {
        byte[] contents = new byte[buffer.remaining() - 1];
        buffer.get(contents);
        fr = multipartRef.get(multipartId);
        if (fr != null) {
          fr.writer.write(contents);
          boolean isLast = buffer.get() == 1 ? true : false;
          if (isLast) {
            fr.writer.close();
            Result result = multipartResult.get(multipartId);
            if (result != null) {
              result.result = true;
            }
            notifyMultipartSuccess = true;
            successId = multipartId;
            Log.i("read", "done");
          }
        }
        contents = null;
      }
    } catch (Exception e) {

    }
  }

  static class FileRecord {
    File file;
    RandomAccessFile writer;

    FileRecord(File file, RandomAccessFile writer) {
      this.file = file;
      this.writer = writer;
    }
  }

  static class Result {
    byte type;
    boolean result;

    Result(byte type, boolean result) {
      this.type = type;
      this.result = result;
    }
  }

  private void clearMultipartState() {
    isHead = false;
    isBlock = false;
  }

  public CMessage convertToReceive() {
    CMessage message = new CMessage();
    try {
      if (environment == null || (environment != null && isHead)) {
        message.setmId(id);
        message.setmSource(who);
        message.setmType(type);
        message.setmContents(contents);
        return message;
      } else if (environment != null && isBlock) {
        for (Map.Entry<Integer, Result> entry : multipartResult.entrySet()) {
          int id = entry.getKey();
          Result result = entry.getValue();
          if (result.result) {
            message.setmId(id);
            message.setmType(result.type);
            multipartResult.remove(id);
            return message;
          }
        }
      }
      return null;
    } finally {
      if (environment != null) {
        clearMultipartState();
      }
    }
  }

  public static SMessage createReply(SMessage orig, boolean success) {
    SMessage reply = new SMessage();
    reply.setType((byte) MessageType.REPLY.ordinal());
    if (orig != null) {
      if (orig.environment == null) {
        reply.setId(orig.getId());
      } else {
        if (!notifyMultipartSuccess && success) return null;
        if (notifyMultipartSuccess && success) {
          reply.setId(successId);
          notifyMultipartSuccess = false;
        } else {
          reply.setId(orig.getId());
        }
      }
    } else {
      reply.setId(-1);
    }
    byte[] status = new byte[1];
    if (success) {
      status[0] = (byte) (ReplyStatus.R_SUCCESS.ordinal());
    } else {
      status[0] = (byte) (ReplyStatus.R_FAILURE.ordinal());
    }
    reply.setContents(status);
    return reply;
  }

  public ByteBuffer writeReply() {
    ByteBuffer writeBuffer = ByteBuffer.allocate(10);
    writeBuffer.put(type).putInt(5).putInt(id).put(contents);
    writeBuffer.position(0);
    return writeBuffer;
  }

  public static SMessage createPing(String user) {
    SMessage ping = new SMessage();
    ping.setType((byte) MessageType.PING.ordinal());
    ping.setContents(user.getBytes());
    return ping;
  }

  public ByteBuffer writePing() {
    ByteBuffer buffer = ByteBuffer.allocate(5 + contents.length);
    buffer.put(type);
    buffer.putInt(contents.length);
    buffer.put(contents);
    buffer.position(0);
    return buffer;
  }

  public static void channelIO(SocketChannel channel, ByteBuffer buffer, boolean write) throws IOException {
    int origSize = buffer.capacity();
    Log.i("os", String.valueOf(origSize));
    while (buffer.hasRemaining()) {
      int ioSize = write ? channel.write(buffer) : channel.read(buffer);
      Log.i("io", String.valueOf(ioSize));
      if (ioSize == origSize) break;
    }
  }

  public enum MessageType {
    TEXT((byte) (1 << 0)), IMAGE((byte) (1 << 1)), AUDIO((byte) (1 << 2)), VEDIO((byte) (1 << 3)), REPLY((byte) (1 << 4)), PING((byte) (1 << 5)), MULTIPART_HEAD((byte) (1 << 6)), MULTIPART_BLOCK(
        (byte) (1 << 7));

    MessageType(byte value) {}

  }

  public enum ReplyStatus {
    R_SUCCESS, R_FAILURE
  }
}
