package com.synchat.common.dto;


public class FriendRequestDto {

    public long id;
    public String from;      // username of the sender
    public String sentAt;

    public FriendRequestDto() {
    }

    public FriendRequestDto(long id, String from, String sentAt) {
        this.id = id;
        this.from = from;
        this.sentAt = sentAt;
    }

    @Override
    public String toString() {
        return from + "  wants to be your friend";
    }
}
