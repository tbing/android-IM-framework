package com.im.framework.app.server;

public enum MessageType {
  TEXT((byte) (1 << 0)), IMAGE((byte) (1 << 1)), AUDIO((byte) (1 << 2)), VEDIO((byte) (1 << 3)), REPLY((byte) (1 << 4)), PING((byte) (1 << 5)), MULTIPART_HEAD((byte) (1 << 6)), MULTIPART_BLOCK(
    (byte) (1 << 7));

  MessageType(byte value) {}
}
