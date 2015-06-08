package com.im.framework.app.common;

public interface Command {

  public enum CommandType {

    REGISTER_HANDLER, SEND_MESSAGE,UNREGISTER_HANDLER
  }

  String COMMAND_TYPE = "COMMAND_TYPE";

  String COMMAND_BODY = "COMMAND_BODY";

  String BODY_FIELD_ARG1 = "ARG1";

  String BODY_FIELD_ARG2 = "ARG2";

  String BODY_FIELD_ARG3 = "ARG3";

  String BODY_FIELD_ARG4 = "ARG4";

  int NO_INT_FIELD = -1;

  byte[] EMPTY_BYTE_ARRAY = new byte[0];

  String HOME_ACTIVITY = "HOME_ACTIVITY";

  String MESSAGE_ACTIVITY = "MESSAGE_ACTIVITY";

}
