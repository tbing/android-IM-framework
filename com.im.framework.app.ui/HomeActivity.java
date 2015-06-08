package com.im.framework.app.ui;

import static com.im.framework.app.common.Command.HOME_ACTIVITY;

import android.os.Bundle;
import android.os.Handler;

public class HomeActivity extends BaseActivity {

  private static Handler selfHandler;

  private void initHomeHandler() {
    selfHandler = new MHandler(this);
  }

  public static Handler getHomeHandler() {
    return selfHandler;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    initHomeHandler();
    registerHandler(HOME_ACTIVITY);
  }

  @Override
  protected void onDestroy() {
    unregisterHandler(HOME_ACTIVITY);
    super.onDestroy();
  }

}
