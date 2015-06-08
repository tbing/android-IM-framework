package com.im.framework.app.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class SMessage {
  private String fileFormat = null;

  private int id;
  private String who;
  private byte type;
  private byte[] contents;

  public SMessage() {

  }

  public SMessage(int id, String who, byte type) {
    this.id = id;
    this.who = who;
    this.type = type;
  }

  public void setId(int id) {
    this.id = id;
  }

  public void setFileFormat(String fileFormat) {
    this.fileFormat = fileFormat;
  }


  public void setContents(byte[] contents) {
    this.contents = contents;
  }

  public void writePlain(SocketChannel channel) throws IOException {
    int totalLength = who.length() + contents.length + 12;
    ByteBuffer writeBuffer = ByteBuffer.allocate(totalLength + 5);
    writeBuffer.put(type).putInt(totalLength).putInt(id).putInt(who.length()).put(who.getBytes()).putInt(contents.length).put(contents);
    writeBuffer.position(0);
    channelIO(channel, writeBuffer, true);
  }

  public void writeMultipartHead(SocketChannel channel) throws IOException {
    int headLength = 13 + who.length() + fileFormat.length();
    ByteBuffer headBuffer = ByteBuffer.allocate(headLength + 5);
    headBuffer.put((byte) MessageType.MULTIPART_HEAD.ordinal()).putInt(headLength).putInt(id).put(type).putInt(who.length()).put(who.getBytes()).putInt(fileFormat.length()).put(fileFormat.getBytes());
    headBuffer.position(0);
    channelIO(channel, headBuffer, true);
  }

  public void writeMultipartBlock(SocketChannel channel, byte[] contents, boolean last) throws IOException {
    int blockSize = contents.length;
    ByteBuffer writeBuffer = ByteBuffer.allocate(10 + blockSize);
    writeBuffer.put((byte) MessageType.MULTIPART_BLOCK.ordinal()).putInt(blockSize + 5).putInt(id).put(contents);
    if (last) {
      writeBuffer.put((byte) 1);
    } else {
      writeBuffer.put((byte) 0);
    }
    writeBuffer.position(0);
    channelIO(channel, writeBuffer, true);
  }

  public void writeMultipartFailure(SocketChannel channel) throws IOException {
    ByteBuffer failureBuffer = ByteBuffer.allocate(13);
    failureBuffer.put((byte) MessageType.MULTIPART_BLOCK.ordinal()).putInt(8).putInt(-1).putInt(id);
    failureBuffer.position(0);
    channelIO(channel, failureBuffer, true);
  }

  protected static void channelIO(SocketChannel channel, ByteBuffer buffer, boolean write) throws IOException {
    int origSize = buffer.capacity();
    while (buffer.hasRemaining()) {
      int ioSize = write ? channel.write(buffer) : channel.read(buffer);
      if (ioSize == origSize) break;
    }
  }
}
