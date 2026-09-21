package com.synchat.server.db;

import com.synchat.common.dto.UserDto;
import com.synchat.server.util.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Everything that touches the {@code users} table. */
public class UserDao {

    /** Minimal row holder used internally for authentication. */
    public record UserRow(int id, String username, String hash, String salt) {
    }

    public boolean usernameExists(String username) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** @return the new user id, or -1 if the username was taken. */
    public int create(String username, String password) throws SQLException {
        String salt = PasswordUtil.newSalt();
        String hash = PasswordUtil.hash(password, salt);
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO users(username, password_hash, salt) VALUES (?,?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, hash);
            ps.setString(3, salt);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        } catch (SQLException e) {
            if (String.valueOf(e.getMessage()).contains("UNIQUE")) {
                return -1;   // race with another registration
            }
            throw e;
        }
    }

    public UserRow findByUsername(String username) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, username, password_hash, salt FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new UserRow(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4));
            }
        }
    }

    public UserRow findById(int id) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, username, password_hash, salt FROM users WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new UserRow(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4));
            }
        }
    }

    /** @return the user row when the password is correct, otherwise null. */
    public UserRow authenticate(String username, String password) throws SQLException {
        UserRow row = findByUsername(username);
        if (row == null) {
            return null;
        }
        return PasswordUtil.matches(password, row.salt(), row.hash()) ? row : null;
    }

    public void updatePassword(int userId, String newPassword) throws SQLException {
        String salt = PasswordUtil.newSalt();
        String hash = PasswordUtil.hash(newPassword, salt);
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE users SET password_hash = ?, salt = ? WHERE id = ?")) {
            ps.setString(1, hash);
            ps.setString(2, salt);
            ps.setInt(3, userId);
            ps.executeUpdate();
        }
    }

    /** Substring search, excluding the caller. */
    public List<UserDto> search(String query, int excludeUserId) throws SQLException {
        List<UserDto> out = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, username FROM users WHERE username LIKE ? AND id <> ? "
                             + "ORDER BY username LIMIT 25")) {
            ps.setString(1, "%" + query + "%");
            ps.setInt(2, excludeUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new UserDto(rs.getInt(1), rs.getString(2), false));
                }
            }
        }
        return out;
    }
}
