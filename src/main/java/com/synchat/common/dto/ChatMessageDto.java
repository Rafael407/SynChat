package com.synchat.common.dto;

/** One private message. */
public class ChatMessageDto {

    public long id;
    public String from;      // sender username
    public String to;        // receiver username
    public String content;
    public String sentAt;    // "yyyy-MM-dd HH:mm:ss"

    public ChatMessageDto() {
    }

    public ChatMessageDto(long id, String from, String to, String content, String sentAt) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.content = content;
        this.sentAt = sentAt;
    }

    /** "HH:mm" part of the timestamp, for the chat view. */
    public String shortTime() {
        if (sentAt != null && sentAt.length() >= 16) {
            return sentAt.substring(11, 16);
        }
        return "";
    }
}
