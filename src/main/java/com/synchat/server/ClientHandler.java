package com.synchat.server;

import com.synchat.common.Packet;
import com.synchat.common.Protocol;
import com.synchat.common.dto.ChatMessageDto;
import com.synchat.common.dto.FriendRequestDto;
import com.synchat.common.dto.UserDto;
import com.synchat.server.db.FriendDao;
import com.synchat.server.db.MessageDao;
import com.synchat.server.db.UserDao;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/**
 * Serves exactly one client socket. An instance is submitted to the server's
 * thread pool, so the run() method *is* the thread of that client.
 *
 * Lifecycle: read a line -> parse a {@link Packet} -> dispatch -> write the
 * correlated RESPONSE back. Unsolicited events (incoming chat messages,
 * friend requests, presence) are written by *other* handler threads through
 * {@link #send(Packet)}, which is why that method is synchronized.
 */
public class ClientHandler implements Runnable {

    private static final int HISTORY_LIMIT = 200;
    private static final int MAX_MESSAGE_LENGTH = 2000;

    private final Socket socket;
    private final SessionManager sessions;

    private final UserDao userDao = new UserDao();
    private final FriendDao friendDao = new FriendDao();
    private final MessageDao messageDao = new MessageDao();

    private PrintWriter out;

    /** -1 until the client authenticates. */
    private volatile int userId = -1;
    private volatile String username;

    public ClientHandler(Socket socket, SessionManager sessions) {
        this.socket = socket;
        this.sessions = sessions;
    }

    /* ==================================================================== */
    /*  thread body                                                         */
    /* ==================================================================== */

    @Override
    public void run() {
        String remote = socket.getRemoteSocketAddress().toString();
        log("connected " + remote);
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            out = new PrintWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8), true);

            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                Packet response;
                Packet request = null;
                try {
                    request = Packet.fromJson(line);
                    response = dispatch(request);
                } catch (SQLException e) {
                    response = Packet.error("Database error: " + e.getMessage());
                    e.printStackTrace();
                } catch (Exception e) {
                    response = Packet.error("Malformed request");
                }
                if (response != null) {
                    response.setId(request == null ? null : request.getId());
                    send(response);
                }
            }
        } catch (IOException e) {
            // client vanished; normal shutdown path
        } finally {
            cleanup();
            log("disconnected " + remote);
        }
    }

    /** Writes one packet. Synchronized: several threads push into this socket. */
    public synchronized void send(Packet packet) {
        if (out != null && !socket.isClosed()) {
            out.println(packet.toJson());
        }
    }

    public void closeSocket() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private void cleanup() {
        if (userId != -1) {
            int leaving = userId;
            sessions.unregister(leaving, this);
            broadcastPresence(leaving, username, false);
            userId = -1;
            username = null;
        }
        closeSocket();
    }

    /* ==================================================================== */
    /*  dispatch                                                            */
    /* ==================================================================== */

    private Packet dispatch(Packet req) throws SQLException {
        String type = req.getType();
        if (type == null) {
            return Packet.error("Missing packet type");
        }

        // requests that do not need a session
        switch (type) {
            case Protocol.REQ_REGISTER:
                return handleRegister(req);
            case Protocol.REQ_LOGIN:
                return handleLogin(req);
            case Protocol.REQ_CHECK_USERNAME:
                return handleCheckUsername(req);
            default:
                break;
        }

        if (userId == -1) {
            return Packet.error("Not logged in");
        }

        switch (type) {
            case Protocol.REQ_LOGOUT:
                return handleLogout();
            case Protocol.REQ_CHANGE_PASSWORD:
                return handleChangePassword(req);
            case Protocol.REQ_SEARCH_USER:
                return handleSearch(req);
            case Protocol.REQ_FRIEND_ADD:
                return handleFriendAdd(req);
            case Protocol.REQ_FRIEND_RESPOND:
                return handleFriendRespond(req);
            case Protocol.REQ_FRIEND_LIST:
                return handleFriendList();
            case Protocol.REQ_REQUEST_LIST:
                return handleRequestList();
            case Protocol.REQ_SEND_MESSAGE:
                return handleSendMessage(req);
            case Protocol.REQ_HISTORY:
                return handleHistory(req);
            default:
                return Packet.error("Unknown request type: " + type);
        }
    }

    /* ------------------------------------------------ authentication ---- */

    private Packet handleRegister(Packet req) throws SQLException {
        String name = trim(req.getString("username"));
        String pass = req.getString("password");

        String problem = validateCredentials(name, pass);
        if (problem != null) {
            return Packet.error(problem);
        }
        if (userDao.usernameExists(name)) {
            return Packet.error("Username '" + name + "' is already taken");
        }
        int id = userDao.create(name, pass);
        if (id == -1) {
            return Packet.error("Username '" + name + "' is already taken");
        }
        log("registered user '" + name + "' (id=" + id + ")");
        return Packet.ok().put("message", "Account created. You can log in now.");
    }

    private Packet handleLogin(Packet req) throws SQLException {
        if (userId != -1) {
            return Packet.error("Already logged in as " + username);
        }
        String name = trim(req.getString("username"));
        String pass = req.getString("password");
        if (name.isEmpty() || pass == null || pass.isEmpty()) {
            return Packet.error("Username and password are required");
        }

        UserDao.UserRow row = userDao.authenticate(name, pass);
        if (row == null) {
            return Packet.error("Wrong username or password");
        }
        if (!sessions.register(row.id(), this)) {
            return Packet.error("This account is already logged in somewhere else");
        }

        this.userId = row.id();
        this.username = row.username();
        log("'" + username + "' logged in");

        broadcastPresence(userId, username, true);
        flushOfflineMessages();

        return Packet.ok().put("username", username).put("userId", userId);
    }

    private Packet handleLogout() {
        int leaving = userId;
        String leavingName = username;
        sessions.unregister(leaving, this);
        userId = -1;
        username = null;
        broadcastPresenceFor(leaving, leavingName, false);
        log("'" + leavingName + "' logged out");
        return Packet.ok().put("message", "Logged out");
    }

    private Packet handleCheckUsername(Packet req) throws SQLException {
        String name = trim(req.getString("username"));
        if (name.isEmpty()) {
            return Packet.error("Username is required");
        }
        boolean taken = userDao.usernameExists(name);
        return Packet.ok()
                .put("available", !taken)
                .put("message", taken ? "'" + name + "' is taken" : "'" + name + "' is available");
    }

    private Packet handleChangePassword(Packet req) throws SQLException {
        String oldPass = req.getString("oldPassword");
        String newPass = req.getString("newPassword");
        if (newPass == null || newPass.length() < 4) {
            return Packet.error("New password must be at least 4 characters");
        }
        if (userDao.authenticate(username, oldPass == null ? "" : oldPass) == null) {
            return Packet.error("Current password is wrong");
        }
        userDao.updatePassword(userId, newPass);
        log("'" + username + "' changed password");
        return Packet.ok().put("message", "Password updated");
    }

    /* ------------------------------------------------- friend system ---- */

    private Packet handleSearch(Packet req) throws SQLException {
        String query = trim(req.getString("query"));
        if (query.isEmpty()) {
            return Packet.error("Type something to search for");
        }
        List<UserDto> users = userDao.search(query, userId);
        for (UserDto u : users) {
            u.online = sessions.isOnline(u.id);
        }
        return Packet.ok().putJson("users", users);
    }

    private Packet handleFriendAdd(Packet req) throws SQLException {
        String target = trim(req.getString("username"));
        if (target.isEmpty()) {
            return Packet.error("Username is required");
        }
        if (target.equalsIgnoreCase(username)) {
            return Packet.error("You cannot add yourself");
        }
        UserDao.UserRow other = userDao.findByUsername(target);
        if (other == null) {
            return Packet.error("No user named '" + target + "'");
        }
        if (friendDao.areFriends(userId, other.id())) {
            return Packet.error("You are already friends with " + other.username());
        }
        if (friendDao.hasPendingBetween(userId, other.id())) {
            return Packet.error("A request between you two is already pending");
        }

        long requestId = friendDao.createRequest(userId, other.id());

        // push it live if the receiver happens to be online
        FriendRequestDto dto = new FriendRequestDto(requestId, username, "just now");
        sessions.sendTo(other.id(), Packet.of(Protocol.EVT_FRIEND_REQUEST).putJson("request", dto));

        log("'" + username + "' -> friend request -> '" + other.username() + "'");
        return Packet.ok().put("message", "Friend request sent to " + other.username());
    }

    private Packet handleFriendRespond(Packet req) throws SQLException {
        long requestId = req.getLong("requestId");
        boolean accept = req.getBoolean("accept");

        FriendDao.Answered answered = friendDao.respond(requestId, userId, accept);
        if (answered == null) {
            return Packet.error("That request is no longer pending");
        }

        // tell the original sender what happened
        sessions.sendTo(answered.senderId(), Packet.of(Protocol.EVT_FRIEND_RESULT)
                .put("username", answered.receiverName())
                .put("accepted", accept));

        if (accept) {
            // both sides should now see each other's presence
            sessions.sendTo(answered.senderId(), Packet.of(Protocol.EVT_PRESENCE)
                    .put("username", answered.receiverName())
                    .put("online", true));
            sessions.sendTo(userId, Packet.of(Protocol.EVT_PRESENCE)
                    .put("username", answered.senderName())
                    .put("online", sessions.isOnline(answered.senderId())));
        }

        log("'" + username + "' " + (accept ? "accepted" : "rejected")
                + " request from '" + answered.senderName() + "'");
        return Packet.ok().put("message",
                (accept ? "You are now friends with " : "Rejected request from ") + answered.senderName());
    }

    private Packet handleFriendList() throws SQLException {
        List<UserDto> friends = friendDao.friendsOf(userId);
        for (UserDto f : friends) {
            f.online = sessions.isOnline(f.id);
        }
        return Packet.ok().putJson("friends", friends);
    }

    private Packet handleRequestList() throws SQLException {
        return Packet.ok().putJson("requests", friendDao.pendingFor(userId));
    }

    /* ---------------------------------------------- private messaging --- */

    /**
     * The routing core. The sender is whoever owns this handler; the receiver
     * is resolved by username, checked to be a friend, persisted, and then
     * pushed into the receiver's own handler if that user is online.
     */
    private Packet handleSendMessage(Packet req) throws SQLException {
        String to = trim(req.getString("to"));
        String content = req.getString("content");

        if (to.isEmpty()) {
            return Packet.error("Receiver is required");
        }
        if (content == null || content.isBlank()) {
            return Packet.error("Cannot send an empty message");
        }
        if (content.length() > MAX_MESSAGE_LENGTH) {
            return Packet.error("Message is too long (max " + MAX_MESSAGE_LENGTH + " characters)");
        }

        UserDao.UserRow receiver = userDao.findByUsername(to);
        if (receiver == null) {
            return Packet.error("No user named '" + to + "'");
        }
        if (!friendDao.areFriends(userId, receiver.id())) {
            return Packet.error("You can only message friends");
        }

        boolean receiverOnline = sessions.isOnline(receiver.id());
        ChatMessageDto dto = messageDao.save(userId, username,
                receiver.id(), receiver.username(), content, receiverOnline);

        if (receiverOnline) {
            sessions.sendTo(receiver.id(), Packet.of(Protocol.EVT_MESSAGE).putJson("message", dto));
        }

        log(username + " -> " + receiver.username() + " : " + content);
        return Packet.ok().putJson("message", dto);
    }

    private Packet handleHistory(Packet req) throws SQLException {
        String peer = trim(req.getString("peer"));
        UserDao.UserRow other = userDao.findByUsername(peer);
        if (other == null) {
            return Packet.error("No user named '" + peer + "'");
        }
        return Packet.ok().putJson("messages",
                messageDao.history(userId, other.id(), HISTORY_LIMIT));
    }

    /** Drains anything that arrived while this user was offline. */
    private void flushOfflineMessages() throws SQLException {
        List<ChatMessageDto> pending = messageDao.undeliveredFor(userId);
        for (ChatMessageDto m : pending) {
            send(Packet.of(Protocol.EVT_MESSAGE).putJson("message", m));
        }
        if (!pending.isEmpty()) {
            messageDao.markDelivered(userId);
            log("delivered " + pending.size() + " queued message(s) to '" + username + "'");
        }
    }

    /* ------------------------------------------------------- helpers ---- */

    private void broadcastPresence(int id, String name, boolean online) {
        broadcastPresenceFor(id, name, online);
    }

    private void broadcastPresenceFor(int id, String name, boolean online) {
        if (id == -1 || name == null) {
            return;
        }
        try {
            Set<Integer> friendIds = friendDao.friendIdsOf(id);
            sessions.sendToAll(friendIds, Packet.of(Protocol.EVT_PRESENCE)
                    .put("username", name)
                    .put("online", online));
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static String validateCredentials(String name, String pass) {
        if (name == null || name.isEmpty()) {
            return "Username is required";
        }
        if (name.length() < 3 || name.length() > 20) {
            return "Username must be 3-20 characters";
        }
        if (!name.matches("[A-Za-z0-9_.]+")) {
            return "Username may only contain letters, digits, '_' and '.'";
        }
        if (pass == null || pass.length() < 4) {
            return "Password must be at least 4 characters";
        }
        return null;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private void log(String msg) {
        System.out.println("[" + Thread.currentThread().getName() + "] " + msg);
    }

    public String getUsername() {
        return username;
    }
}
