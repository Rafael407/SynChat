package com.synchat.common;

/**
 * SynChat application protocol.
 *
 * TRANSPORT
 *   Plain TCP. One JSON document per line, UTF-8, terminated by '\n'.
 *   A line is called a "packet" and is modelled by {@link Packet}:
 *
 *       { "type": "<TYPE>", "id": "<correlation-id or null>", "data": { ... } }
 *
 * REQUEST / RESPONSE
 *   Every packet the client sends carries a unique "id". The server answers
 *   with a packet of type RESPONSE carrying the *same* id, so the client can
 *   match an answer to the request that produced it. A response always has
 *   data.ok (boolean) and, when ok == false, data.message (String reason).
 *
 * SERVER PUSH (events)
 *   Packets whose id is null are unsolicited server events (a new message, an
 *   incoming friend request, a friend going online/offline...). The client's
 *   listener thread dispatches those to registered event handlers.
 */
public final class Protocol {

    private Protocol() {
    }

    /* ----------------------------------------------------------- transport */

    public static final String HOST = "127.0.0.1";   // localhost only
    public static final int PORT = 5555;

    /* ------------------------------------------------ client -> server ---- */

    /** data: username, password */
    public static final String REQ_REGISTER = "REGISTER";
    /** data: username, password  -> data: username */
    public static final String REQ_LOGIN = "LOGIN";
    /** data: -                    */
    public static final String REQ_LOGOUT = "LOGOUT";
    /** data: username             -> data: available (boolean) */
    public static final String REQ_CHECK_USERNAME = "CHECK_USERNAME";
    /** data: oldPassword, newPassword */
    public static final String REQ_CHANGE_PASSWORD = "CHANGE_PASSWORD";

    /** data: query               -> data: users (UserDto[]) */
    public static final String REQ_SEARCH_USER = "SEARCH_USER";
    /** data: username            */
    public static final String REQ_FRIEND_ADD = "FRIEND_ADD";
    /** data: requestId, accept   */
    public static final String REQ_FRIEND_RESPOND = "FRIEND_RESPOND";
    /** data: -                   -> data: friends (UserDto[]) */
    public static final String REQ_FRIEND_LIST = "FRIEND_LIST";
    /** data: -                   -> data: requests (FriendRequestDto[]) */
    public static final String REQ_REQUEST_LIST = "REQUEST_LIST";

    /** data: to, content         -> data: message (ChatMessageDto) */
    public static final String REQ_SEND_MESSAGE = "SEND_MESSAGE";
    /** data: peer               -> data: messages (ChatMessageDto[]) */
    public static final String REQ_HISTORY = "HISTORY";

    /* ------------------------------------------------ server -> client ---- */

    /** Correlated answer to any request. data: ok, message, ...payload */
    public static final String RESPONSE = "RESPONSE";

    /** push. data: message (ChatMessageDto) */
    public static final String EVT_MESSAGE = "EVT_MESSAGE";
    /** push. data: request (FriendRequestDto) */
    public static final String EVT_FRIEND_REQUEST = "EVT_FRIEND_REQUEST";
    /** push. data: username, accepted (boolean) */
    public static final String EVT_FRIEND_RESULT = "EVT_FRIEND_RESULT";
    /** push. data: username, online (boolean) */
    public static final String EVT_PRESENCE = "EVT_PRESENCE";
    /** push. data: message  (server is shutting the session down) */
    public static final String EVT_SERVER_NOTICE = "EVT_SERVER_NOTICE";
}
