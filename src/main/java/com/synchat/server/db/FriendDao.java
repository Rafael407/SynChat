package com.synchat.server.db;

import com.synchat.common.dto.FriendRequestDto;
import com.synchat.common.dto.UserDto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class FriendDao {

    public record Answered(int senderId, String senderName, int receiverId, String receiverName) {
    }

    public boolean areFriends(int a, int b) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM friends WHERE user_id = ? AND friend_id = ?")) {
            ps.setInt(1, a);
            ps.setInt(2, b);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean hasPendingBetween(int a, int b) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM friend_requests WHERE status = 'PENDING' "
                             + "AND ((sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?))")) {
            ps.setInt(1, a);
            ps.setInt(2, b);
            ps.setInt(3, b);
            ps.setInt(4, a);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }


    public long createRequest(int senderId, int receiverId) throws SQLException {
        try (Connection c = Database.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO friend_requests(sender_id, receiver_id, status) VALUES (?,?,'PENDING') "
                            + "ON CONFLICT(sender_id, receiver_id) "
                            + "DO UPDATE SET status = 'PENDING', created_at = datetime('now','localtime')")) {
                ps.setInt(1, senderId);
                ps.setInt(2, receiverId);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id FROM friend_requests WHERE sender_id = ? AND receiver_id = ?")) {
                ps.setInt(1, senderId);
                ps.setInt(2, receiverId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : -1;
                }
            }
        }
    }


    public List<FriendRequestDto> pendingFor(int userId) throws SQLException {
        List<FriendRequestDto> out = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT r.id, u.username, r.created_at FROM friend_requests r "
                             + "JOIN users u ON u.id = r.sender_id "
                             + "WHERE r.receiver_id = ? AND r.status = 'PENDING' ORDER BY r.id DESC")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new FriendRequestDto(rs.getLong(1), rs.getString(2), rs.getString(3)));
                }
            }
        }
        return out;
    }

    public Answered respond(long requestId, int userId, boolean accept) throws SQLException {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                int senderId;
                String senderName;
                String receiverName;

                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT r.sender_id, s.username, v.username FROM friend_requests r "
                                + "JOIN users s ON s.id = r.sender_id "
                                + "JOIN users v ON v.id = r.receiver_id "
                                + "WHERE r.id = ? AND r.receiver_id = ? AND r.status = 'PENDING'")) {
                    ps.setLong(1, requestId);
                    ps.setInt(2, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            c.rollback();
                            return null;
                        }
                        senderId = rs.getInt(1);
                        senderName = rs.getString(2);
                        receiverName = rs.getString(3);
                    }
                }

                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE friend_requests SET status = ? WHERE id = ?")) {
                    ps.setString(1, accept ? "ACCEPTED" : "REJECTED");
                    ps.setLong(2, requestId);
                    ps.executeUpdate();
                }

                if (accept) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT OR IGNORE INTO friends(user_id, friend_id) VALUES (?,?)")) {
                        ps.setInt(1, senderId);
                        ps.setInt(2, userId);
                        ps.addBatch();
                        ps.setInt(1, userId);
                        ps.setInt(2, senderId);
                        ps.addBatch();
                        ps.executeBatch();
                    }
                }

                c.commit();
                return new Answered(senderId, senderName, userId, receiverName);
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public List<UserDto> friendsOf(int userId) throws SQLException {
        List<UserDto> out = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT u.id, u.username FROM friends f JOIN users u ON u.id = f.friend_id "
                             + "WHERE f.user_id = ? ORDER BY u.username")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new UserDto(rs.getInt(1), rs.getString(2), false));
                }
            }
        }
        return out;
    }

    public Set<Integer> friendIdsOf(int userId) throws SQLException {
        Set<Integer> ids = new HashSet<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT friend_id FROM friends WHERE user_id = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        return ids;
    }
}
