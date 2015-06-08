package com.im.framework.app.service;

import android.os.Parcel;
import android.os.Parcelable;

public class CMessage implements Parcelable {

  private int mId;
  private String mSource;
  private String mDest;
  private byte mType;
  private byte[] mContents;


  public int getmId() {
    return mId;
  }

  public void setmId(int mId) {
    this.mId = mId;
  }

  public String getmSource() {
    return mSource;
  }

  public void setmSource(String mSource) {
    this.mSource = mSource;
  }

  public String getmDest() {
    return mDest;
  }

  public void setmDest(String mDest) {
    this.mDest = mDest;
  }

  public byte getmType() {
    return mType;
  }

  public void setmType(byte mType) {
    this.mType = mType;
  }

  public byte[] getmContents() {
    return mContents;
  }

  public void setmContents(byte[] mContents) {
    this.mContents = mContents;
  }

  public CMessage() {
      this.mType=-1;
  }

  @Override
  public int describeContents() {
    return CONTENTS_FILE_DESCRIPTOR;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(mId);
    dest.writeString(mSource);
    dest.writeString(mDest);
    dest.writeByte(mType);
    dest.writeByteArray(mContents);
  }

  public void readFromParcel(Parcel source) {
    mId = source.readInt();
    mSource = source.readString();
    mDest = source.readString();
    mType = source.readByte();
    mContents = source.createByteArray();
  }

  public static final Creator<CMessage> CREATOR = new Creator<CMessage>() {
    public CMessage createFromParcel(Parcel source) {
      return new CMessage(source);
    }

    public CMessage[] newArray(int size) {
      return new CMessage[size];
    }
  };

  private CMessage(Parcel source) {
    readFromParcel(source);
  }

  public enum MessageType {
    TEXT  ((byte) (1 << 0)), 
    IMAGE ((byte) (1 << 1)), 
    AUDIO ((byte) (1 << 2)), 
    VEDIO ((byte) (1 << 3)), 
    STATUS ((byte) (1 << 4));

    MessageType(byte value) {}

  }

  public enum MessageStatus {
    SUCCESS, FAILURE, PENDING
  }

  public static class MessageItem {
    public static final String ID = "mId";
    public static final String SOURCE = "mSource";
    public static final String DEST = "mDest";
    public static final String TYPE = "mType";
    public static final String CONTENTS = "mContents";
  }
}
