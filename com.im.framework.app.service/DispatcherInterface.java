/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: D:\\Program Files-2\\eclipse_android_n\\workspace\\IM\\src\\com\\im\\framework\\app\\service\\DispatcherInterface.aidl
 */
package com.im.framework.app.service;
public interface DispatcherInterface extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements com.im.framework.app.service.DispatcherInterface
{
private static final java.lang.String DESCRIPTOR = "com.im.framework.app.service.DispatcherInterface";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an com.im.framework.app.service.DispatcherInterface interface,
 * generating a proxy if needed.
 */
public static com.im.framework.app.service.DispatcherInterface asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof com.im.framework.app.service.DispatcherInterface))) {
return ((com.im.framework.app.service.DispatcherInterface)iin);
}
return new com.im.framework.app.service.DispatcherInterface.Stub.Proxy(obj);
}
@Override public android.os.IBinder asBinder()
{
return this;
}
@Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
{
switch (code)
{
case INTERFACE_TRANSACTION:
{
reply.writeString(DESCRIPTOR);
return true;
}
case TRANSACTION_receiveMessage:
{
data.enforceInterface(DESCRIPTOR);
com.im.framework.app.service.CMessage _arg0;
if ((0!=data.readInt())) {
_arg0 = com.im.framework.app.service.CMessage.CREATOR.createFromParcel(data);
}
else {
_arg0 = null;
}
this.receiveMessage(_arg0);
return true;
}
case TRANSACTION_notifySendingStatus:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
int _arg1;
_arg1 = data.readInt();
this.notifySendingStatus(_arg0, _arg1);
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements com.im.framework.app.service.DispatcherInterface
{
private android.os.IBinder mRemote;
Proxy(android.os.IBinder remote)
{
mRemote = remote;
}
@Override public android.os.IBinder asBinder()
{
return mRemote;
}
public java.lang.String getInterfaceDescriptor()
{
return DESCRIPTOR;
}
@Override public void receiveMessage(com.im.framework.app.service.CMessage message) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
if ((message!=null)) {
_data.writeInt(1);
message.writeToParcel(_data, 0);
}
else {
_data.writeInt(0);
}
mRemote.transact(Stub.TRANSACTION_receiveMessage, _data, null, android.os.IBinder.FLAG_ONEWAY);
}
finally {
_data.recycle();
}
}
@Override public void notifySendingStatus(int id, int status) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(id);
_data.writeInt(status);
mRemote.transact(Stub.TRANSACTION_notifySendingStatus, _data, null, android.os.IBinder.FLAG_ONEWAY);
}
finally {
_data.recycle();
}
}
}
static final int TRANSACTION_receiveMessage = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_notifySendingStatus = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
}
public void receiveMessage(com.im.framework.app.service.CMessage message) throws android.os.RemoteException;
public void notifySendingStatus(int id, int status) throws android.os.RemoteException;
}
