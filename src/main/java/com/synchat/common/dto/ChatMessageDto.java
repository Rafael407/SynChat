package com.synchat.common.dto;

/** One private message. */
public class ChatMessageDto {

    public long id;
    public String from;
    public String to;
    public String content;
    public String sentAt;

    public ChatMessageDto() {
    }

    public ChatMessageDto(long id, String from, String to, String content, String sentAt) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.content = content;
        this.sentAt = sentAt;
    }

    //timestamp
    public String shortTime() {
        if (sentAt != null && sentAt.length() >= 16) {
            return sentAt.substring(11, 16);
        }
        return "";
    }
}
