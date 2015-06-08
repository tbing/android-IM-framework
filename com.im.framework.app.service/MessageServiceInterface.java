/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: D:\\Program Files-2\\eclipse_android_n\\workspace\\IM\\src\\com\\im\\framework\\app\\service\\MessageServiceInterface.aidl
 */
package com.im.framework.app.service;
public interface MessageServiceInterface extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements com.im.framework.app.service.MessageServiceInterface
{
private static final java.lang.String DESCRIPTOR = "com.im.framework.app.service.MessageServiceInterface";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an com.im.framework.app.service.MessageServiceInterface interface,
 * generating a proxy if needed.
 */
public static com.im.framework.app.service.MessageServiceInterface asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof com.im.framework.app.service.MessageServiceInterface))) {
return ((com.im.framework.app.service.MessageServiceInterface)iin);
}
return new com.im.framework.app.service.MessageServiceInterface.Stub.Proxy(obj);
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
case TRANSACTION_sendMessage:
{
data.enforceInterface(DESCRIPTOR);
com.im.framework.app.service.CMessage _arg0;
if ((0!=data.readInt())) {
_arg0 = com.im.framework.app.service.CMessage.CREATOR.createFromParcel(data);
}
else {
_arg0 = null;
}
this.sendMessage(_arg0);
return true;
}
case TRANSACTION_attachService:
{
data.enforceInterface(DESCRIPTOR);
com.im.framework.app.service.DispatcherInterface _arg0;
_arg0 = com.im.framework.app.service.DispatcherInterface.Stub.asInterface(data.readStrongBinder());
com.im.framework.app.service.CMessage[] _result = this.attachService(_arg0);
reply.writeNoException();
reply.writeTypedArray(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements com.im.framework.app.service.MessageServiceInterface
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
@Override public void sendMessage(com.im.framework.app.service.CMessage message) throws android.os.RemoteException
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
mRemote.transact(Stub.TRANSACTION_sendMessage, _data, null, android.os.IBinder.FLAG_ONEWAY);
}
finally {
_data.recycle();
}
}
@Override public com.im.framework.app.service.CMessage[] attachService(com.im.framework.app.service.DispatcherInterface dispatcherService) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
com.im.framework.app.service.CMessage[] _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStrongBinder((((dispatcherService!=null))?(dispatcherService.asBinder()):(null)));
mRemote.transact(Stub.TRANSACTION_attachService, _data, _reply, 0);
_reply.readException();
_result = _reply.createTypedArray(com.im.framework.app.service.CMessage.CREATOR);
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
}
static final int TRANSACTION_sendMessage = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_attachService = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
}
public void sendMessage(com.im.framework.app.service.CMessage message) throws android.os.RemoteException;
public com.im.framework.app.service.CMessage[] attachService(com.im.framework.app.service.DispatcherInterface dispatcherService) throws android.os.RemoteException;
}
