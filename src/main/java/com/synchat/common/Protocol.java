package com.synchat.common;

public final class Protocol {

    private Protocol() {
    }
    public static final String HOST = "127.0.0.1";   // localhost only
    public static final int PORT = 5555;
    public static final String REQ_REGISTER = "REGISTER";
    public static final String REQ_LOGIN = "LOGIN";
    public static final String REQ_LOGOUT = "LOGOUT";
    public static final String REQ_CHECK_USERNAME = "CHECK_USERNAME";
    public static final String REQ_CHANGE_PASSWORD = "CHANGE_PASSWORD";
    public static final String REQ_DELETE_ACCOUNT = "DELETE_ACCOUNT";
    public static final String REQ_SEARCH_USER = "SEARCH_USER";
    public static final String REQ_FRIEND_ADD = "FRIEND_ADD";
    public static final String REQ_FRIEND_RESPOND = "FRIEND_RESPOND";
    public static final String REQ_FRIEND_LIST = "FRIEND_LIST";
    public static final String REQ_REQUEST_LIST = "REQUEST_LIST";
    public static final String REQ_SEND_MESSAGE = "SEND_MESSAGE";
    public static final String REQ_HISTORY = "HISTORY";
    public static final String RESPONSE = "RESPONSE";
    public static final String EVT_MESSAGE = "EVT_MESSAGE";
    public static final String EVT_FRIEND_REQUEST = "EVT_FRIEND_REQUEST";
    public static final String EVT_FRIEND_RESULT = "EVT_FRIEND_RESULT";
    public static final String EVT_FRIEND_REMOVED = "EVT_FRIEND_REMOVED";
    public static final String EVT_PRESENCE = "EVT_PRESENCE";
    public static final String EVT_SERVER_NOTICE = "EVT_SERVER_NOTICE";
}
