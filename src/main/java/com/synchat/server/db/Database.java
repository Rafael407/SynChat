package com.synchat.server.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Owns the SQLite file and creates the schema on first start.
 *
 * Each DAO call opens a short lived {@link Connection}. SQLite serialises
 * writers itself through file locking, and WAL mode keeps readers from
 * blocking, which is exactly what a pool of client handler threads needs.
 */
public final class Database {

    private static String url;

    private Database() {
    }

    public static void init(String filePath) {
        url = "jdbc:sqlite:" + filePath;
        try (Connection c = getConnection(); Statement s = c.createStatement()) {

            s.execute("PRAGMA journal_mode = WAL");

            s.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id            INTEGER PRIMARY KEY AUTOINCREMENT,
                        username      TEXT NOT NULL UNIQUE COLLATE NOCASE,
                        password_hash TEXT NOT NULL,
                        salt          TEXT NOT NULL,
                        created_at    TEXT NOT NULL DEFAULT (datetime('now','localtime'))
                    )""");

            s.execute("""
                    CREATE TABLE IF NOT EXISTS friend_requests (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT,
                        sender_id   INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                        receiver_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                        status      TEXT NOT NULL DEFAULT 'PENDING',
                        created_at  TEXT NOT NULL DEFAULT (datetime('now','localtime')),
                        UNIQUE (sender_id, receiver_id)
                    )""");

            // friendship is stored twice (a->b and b->a) so lookups stay trivial
            s.execute("""
                    CREATE TABLE IF NOT EXISTS friends (
                        user_id   INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                        friend_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                        since     TEXT NOT NULL DEFAULT (datetime('now','localtime')),
                        PRIMARY KEY (user_id, friend_id)
                    )""");

            s.execute("""
                    CREATE TABLE IF NOT EXISTS messages (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT,
                        sender_id   INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                        receiver_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                        content     TEXT NOT NULL,
                        sent_at     TEXT NOT NULL DEFAULT (datetime('now','localtime')),
                        delivered   INTEGER NOT NULL DEFAULT 0
                    )""");

            s.execute("CREATE INDEX IF NOT EXISTS idx_msg_pair ON messages(sender_id, receiver_id)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_req_receiver ON friend_requests(receiver_id, status)");

            System.out.println("[DB ] schema ready -> " + filePath);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot initialise database", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        Connection c = DriverManager.getConnection(url);
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys = ON");
            s.execute("PRAGMA busy_timeout = 5000");
        }
        return c;
    }
}
