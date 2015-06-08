package com.im.framework.app.service;

import com.im.framework.app.service.CMessage;
import com.im.framework.app.service.DispatcherInterface;

 interface MessageServiceInterface {
    
   oneway void sendMessage( in CMessage message);
    
    CMessage[] attachService(DispatcherInterface dispatcherService);
}
