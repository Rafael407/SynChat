package com.synchat.server.db;

import com.synchat.common.dto.ChatMessageDto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MessageDao {


    public ChatMessageDto save(int senderId, String senderName,
                               int receiverId, String receiverName,
                               String content, boolean delivered) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO messages(sender_id, receiver_id, content, delivered) VALUES (?,?,?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, senderId);
            ps.setInt(2, receiverId);
            ps.setString(3, content);
            ps.setInt(4, delivered ? 1 : 0);
            ps.executeUpdate();

            long id = -1;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    id = keys.getLong(1);
                }
            }
            String sentAt = readSentAt(c, id);
            return new ChatMessageDto(id, senderName, receiverName, content, sentAt);
        }
    }

    private String readSentAt(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT sent_at FROM messages WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "";
            }
        }
    }

    public List<ChatMessageDto> history(int userA, int userB, int limit) throws SQLException {
        List<ChatMessageDto> out = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT m.id, s.username, r.username, m.content, m.sent_at FROM messages m "
                             + "JOIN users s ON s.id = m.sender_id "
                             + "JOIN users r ON r.id = m.receiver_id "
                             + "WHERE (m.sender_id = ? AND m.receiver_id = ?) "
                             + "   OR (m.sender_id = ? AND m.receiver_id = ?) "
                             + "ORDER BY m.id DESC LIMIT ?")) {
            ps.setInt(1, userA);
            ps.setInt(2, userB);
            ps.setInt(3, userB);
            ps.setInt(4, userA);
            ps.setInt(5, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ChatMessageDto(rs.getLong(1), rs.getString(2),
                            rs.getString(3), rs.getString(4), rs.getString(5)));
                }
            }
        }
        Collections.reverse(out);
        return out;
    }

    public List<ChatMessageDto> undeliveredFor(int userId) throws SQLException {
        List<ChatMessageDto> out = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT m.id, s.username, r.username, m.content, m.sent_at FROM messages m "
                             + "JOIN users s ON s.id = m.sender_id "
                             + "JOIN users r ON r.id = m.receiver_id "
                             + "WHERE m.receiver_id = ? AND m.delivered = 0 ORDER BY m.id")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ChatMessageDto(rs.getLong(1), rs.getString(2),
                            rs.getString(3), rs.getString(4), rs.getString(5)));
                }
            }
        }
        return out;
    }

    public void markDelivered(int userId) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE messages SET delivered = 1 WHERE receiver_id = ? AND delivered = 0")) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }
}
