package com.im.framework.app.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class DispatcherReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent != null) {
      context.startService(new Intent("START_DISPATCHER_FROM_RECEIVER"));
    }

  }

}
