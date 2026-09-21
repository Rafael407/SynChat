package com.synchat.server;

import com.synchat.common.Packet;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The routing table of the server: userId -> the {@link ClientHandler} that
 * currently owns that user's socket.
 *
 * This is the piece that lets the server work out "from whom, to whom": the
 * handler knows the sender (it authenticated them), looks the receiver up here
 * and writes straight into the receiver's socket.
 *
 * Backed by a ConcurrentHashMap because handler threads register, look up and
 * unregister concurrently.
 */
public class SessionManager {

    private final Map<Integer, ClientHandler> online = new ConcurrentHashMap<>();

    /** @return false when that account already has a live session. */
    public boolean register(int userId, ClientHandler handler) {
        return online.putIfAbsent(userId, handler) == null;
    }

    public void unregister(int userId, ClientHandler handler) {
        online.remove(userId, handler);
    }

    public boolean isOnline(int userId) {
        return online.containsKey(userId);
    }

    public ClientHandler get(int userId) {
        return online.get(userId);
    }

    /** @return true when the packet was handed to a live socket. */
    public boolean sendTo(int userId, Packet packet) {
        ClientHandler h = online.get(userId);
        if (h == null) {
            return false;
        }
        h.send(packet);
        return true;
    }

    /** Fan a packet out to every user in {@code userIds} that is online. */
    public void sendToAll(Set<Integer> userIds, Packet packet) {
        for (Integer id : userIds) {
            sendTo(id, packet);
        }
    }

    public int onlineCount() {
        return online.size();
    }

    public Collection<ClientHandler> all() {
        return online.values();
    }
}
