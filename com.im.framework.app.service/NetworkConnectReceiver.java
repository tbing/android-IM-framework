package com.im.framework.app.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;

public class NetworkConnectReceiver extends BroadcastReceiver {


  @Override
  public void onReceive(Context context, Intent intent) {
    ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    Intent action = new Intent("START_MESSAGE_SERVICE_FROM_RECEIVER");
    if (intent != null) {
      NetworkInfo info = null;
      int sdkVersion = Build.VERSION.SDK_INT;
      if (sdkVersion < Build.VERSION_CODES.JELLY_BEAN_MR1) {
        info = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
      } else {
        int type = intent.getIntExtra(ConnectivityManager.EXTRA_NETWORK_TYPE, -1);
        if (type != -1) {
          info = cm.getNetworkInfo(type);
        }
      }
      if (info.isConnected()) {
        context.startService(action);
      }
    }
  }
}
