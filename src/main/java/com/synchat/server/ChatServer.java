package com.synchat.server;

import com.synchat.common.Packet;
import com.synchat.common.Protocol;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SERVER ARCHITECTURE
 *
 *   main thread            accept loop, one blocking accept() per client
 *        |
 *        +--> ExecutorService (fixed pool of MAX_CONCURRENT_CLIENTS threads)
 *                 |
 *                 +--> ClientHandler  (one per socket, owns that session)
 *                 +--> ClientHandler
 *                 ...
 *
 *   SessionManager   shared, concurrent map  userId -> ClientHandler
 *   DAOs             stateless, one short lived JDBC connection per call
 *
 * The pool is deliberately *bounded*: connection number MAX+1 is queued and
 * only starts being served once a thread frees up. That is what keeps a local
 * simulation from spawning an unbounded number of threads.
 */
public class ChatServer {

    /** Size of the bounded worker pool. */
    public static final int MAX_CONCURRENT_CLIENTS = 20;

    private final int port;
    private final SessionManager sessions = new SessionManager();
    private final ExecutorService pool;

    private ServerSocket serverSocket;
    private volatile boolean running;

    public ChatServer(int port) {
        this.port = port;
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger n = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "client-worker-" + n.getAndIncrement());
                t.setDaemon(false);
                return t;
            }
        };
        this.pool = Executors.newFixedThreadPool(MAX_CONCURRENT_CLIENTS, factory);
    }

    public void start() throws IOException {
        // bound to the loopback address: the server is reachable from this machine only
        serverSocket = new ServerSocket(port, 50, InetAddress.getByName(Protocol.HOST));
        running = true;

        System.out.println("[SRV] SynChat server listening on " + Protocol.HOST + ":" + port);
        System.out.println("[SRV] worker pool size = " + MAX_CONCURRENT_CLIENTS);

        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "shutdown-hook"));

        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);      // chat messages are small, do not buffer them
                pool.submit(new ClientHandler(socket, sessions));
            } catch (IOException e) {
                if (running) {
                    System.err.println("[SRV] accept failed: " + e.getMessage());
                }
            }
        }
    }

    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        System.out.println("[SRV] shutting down, " + sessions.onlineCount() + " user(s) online");

        for (ClientHandler h : sessions.all()) {
            h.send(Packet.of(Protocol.EVT_SERVER_NOTICE).put("message", "Server is shutting down"));
            h.closeSocket();
        }
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
        pool.shutdown();
        try {
            if (!pool.awaitTermination(3, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("[SRV] stopped");
    }

    public SessionManager sessions() {
        return sessions;
    }
}
