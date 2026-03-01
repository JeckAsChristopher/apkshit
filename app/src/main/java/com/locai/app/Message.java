package com.locai.app;

/**
 * Represents a single chat message.
 * role: "user" | "assistant"
 */
public class Message {

    public static final String ROLE_USER      = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    private long   id;
    private String role;
    private String content;
    private long   timestamp;

    public Message(String role, String content) {
        this.role      = role;
        this.content   = content;
        this.timestamp = System.currentTimeMillis();
    }

    public Message(long id, String role, String content, long timestamp) {
        this.id        = id;
        this.role      = role;
        this.content   = content;
        this.timestamp = timestamp;
    }

    public long   getId()        { return id; }
    public String getRole()      { return role; }
    public String getContent()   { return content; }
    public long   getTimestamp() { return timestamp; }

    public void setContent(String content) { this.content = content; }

    public boolean isUser() { return ROLE_USER.equals(role); }
}
