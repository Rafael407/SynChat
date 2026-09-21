package com.synchat.common.dto;

/** A user as seen by another user. */
public class UserDto {

    public int id;
    public String username;
    public boolean online;

    public UserDto() {
    }

    public UserDto(int id, String username, boolean online) {
        this.id = id;
        this.username = username;
        this.online = online;
    }

    @Override
    public String toString() {
        return username + (online ? "  (online)" : "  (offline)");
    }
}
