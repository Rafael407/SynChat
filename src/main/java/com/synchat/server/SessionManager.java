package com.synchat.server;

import com.synchat.common.Packet;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public class SessionManager {

    private final Map<Integer, ClientHandler> online = new ConcurrentHashMap<>();

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


    public boolean sendTo(int userId, Packet packet) {
        ClientHandler h = online.get(userId);
        if (h == null) {
            return false;
        }
        h.send(packet);
        return true;
    }
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
