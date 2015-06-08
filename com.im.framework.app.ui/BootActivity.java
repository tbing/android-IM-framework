package com.im.framework.app.ui;

import java.util.List;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

public class BootActivity extends Activity {

  private ActivityManager am;
  private static final String DISPATCHER_CLASS = "com.im.framework.app.service.Dispatcher";
  private static final String MESSAGESERVICE_CLASS = "com.im.framework.app.service.MessageService";
  private ComponentName dispatcherName;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    am = (ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE);
    TextView view = new TextView(this);
    view.setText("boot . . .");
    this.setContentView(view);
  }

  @Override
  protected void onStart() {
    super.onStart();
    dispatcherName = this.startService(new Intent("INIT_DISPATCHER_IN_BOOT"));
  }

  @Override
  protected void onResume() {
    super.onResume();
    startCheckInitDone();
  }

  private void startCheckInitDone() {
    new Thread() {
      public void run() {
        if (dispatcherName == null) return;
        boolean dispatcherInit = false, msInit = false;
        LOOP: while (true) {
          List<RunningServiceInfo> serviceInfos = am.getRunningServices(Integer.MAX_VALUE);
          for (RunningServiceInfo rs : serviceInfos) {
            if (rs.service.getClassName().equals(DISPATCHER_CLASS)) {
              dispatcherInit = true;
            } else if (rs.service.getClassName().equals(MESSAGESERVICE_CLASS)) {
              msInit = true;
            }
            if (dispatcherInit && msInit) break LOOP;
          }
          try {
            Thread.sleep(10);
          } catch (InterruptedException ie) {}
        }
        initDone();
      }
    }.start();
  }

  private void initDone() {
    this.startActivity(new Intent("START_MESSAGE_ACTIVITY_FROM_BOOT"));
    this.finish();
  }
}
