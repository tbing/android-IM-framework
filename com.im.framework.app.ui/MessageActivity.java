package com.im.framework.app.ui;

import static com.im.framework.app.common.Command.MESSAGE_ACTIVITY;

import android.os.Bundle;
import android.os.Handler;

public class MessageActivity extends BaseActivity {

  private static Handler selfHandler;

  private void initMessageHandler() {
    selfHandler = new MHandler(this);
  }

  public static Handler getMessageHandler() {
    return selfHandler;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    initMessageHandler();
    registerHandler(MESSAGE_ACTIVITY);
  }

  @Override
  protected void onDestroy() {
    unregisterHandler(MESSAGE_ACTIVITY);
    super.onDestroy();
  }


}
