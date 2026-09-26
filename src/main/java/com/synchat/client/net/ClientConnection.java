package com.synchat.client.net;

import com.synchat.common.Packet;
import com.synchat.common.Protocol;
import javafx.application.Platform;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/*
 CLIENT ARCHITECTURE

 JavaFX Application Thread      draws the UI, never blocks on I/O
      |  request(packet) -> CompletableFuture
      v
 ClientConnection  (socket, writer)
      ^
      |
  listener thread

*/
public class ClientConnection {

    private Socket socket;
    private PrintWriter out;
    private Thread listenerThread;

    private final Map<String, CompletableFuture<Packet>> pending = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<Packet>>> listeners = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    private volatile boolean connected;
    private Runnable onDisconnect = () -> {
    };

    public void connect(String host, int port) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        socket.setTcpNoDelay(true);

        out = new PrintWriter(new OutputStreamWriter(
                socket.getOutputStream(), StandardCharsets.UTF_8), true);
        connected = true;

        listenerThread = new Thread(this::listenLoop, "server-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public boolean isConnected() {
        return connected;
    }

    public void setOnDisconnect(Runnable action) {
        this.onDisconnect = action;
    }


    private void listenLoop() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                Packet packet;
                try {
                    packet = Packet.fromJson(line);
                } catch (Exception e) {
                    System.err.println("[NET] dropped malformed packet: " + line);
                    continue;
                }
                route(packet);
            }
        } catch (IOException e) {

        } finally {
            connected = false;
            failAllPending("Connection to the server was lost");
            Platform.runLater(onDisconnect);
        }
    }

    private void route(Packet packet) {
        String id = packet.getId();
        if (id != null) {
            CompletableFuture<Packet> future = pending.remove(id);
            if (future != null) {
                future.complete(packet);
                return;
            }
        }
        List<Consumer<Packet>> handlers = listeners.get(packet.getType());
        if (handlers != null) {
            Platform.runLater(() -> handlers.forEach(h -> h.accept(packet)));
        }
    }

    private void failAllPending(String reason) {
        pending.values().forEach(f -> f.complete(Packet.error(reason)));
        pending.clear();
    }


    public CompletableFuture<Packet> request(Packet packet) {
        CompletableFuture<Packet> future = new CompletableFuture<>();
        if (!connected) {
            future.complete(Packet.error("Not connected to the server"));
            return future;
        }
        String id = "c-" + sequence.incrementAndGet();
        packet.setId(id);
        pending.put(id, future);
        synchronized (this) {
            out.println(packet.toJson());
        }
        return future;
    }

    public void send(Packet packet, Consumer<Packet> callback) {
        request(packet).thenAccept(response -> Platform.runLater(() -> callback.accept(response)));
    }

    public void on(String eventType, Consumer<Packet> handler) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    public void clearListeners(String eventType) {
        listeners.remove(eventType);
    }

    public void clearAllListeners() {
        listeners.clear();
    }

    public void close() {
        connected = false;
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }
}
