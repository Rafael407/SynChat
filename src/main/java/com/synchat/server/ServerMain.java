package com.synchat.server;

import com.synchat.common.Protocol;
import com.synchat.server.db.Database;


public class ServerMain {

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : Protocol.PORT;
        String dbFile = args.length > 1 ? args[1] : "synchat.db";

        Database.init(dbFile);
        new ChatServer(port).start();
    }
}
