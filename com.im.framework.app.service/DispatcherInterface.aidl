package com.im.framework.app.service;


import com.im.framework.app.service.CMessage ;
  
interface DispatcherInterface{
     
     oneway void receiveMessage(in CMessage message);
     
     oneway void notifySendingStatus(int id,int status);
 }
