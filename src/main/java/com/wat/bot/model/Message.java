package com.wat.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;

public class Message {

    @JsonIgnore
    private Instant time;
    private String role;
    private String content;

    public Message() {
        this.time = Instant.now();
    }

    public Message(String role, String content) {
        this.role = role;
        this.content = content;
        this.time = Instant.now();
    }

    public Message(String role, String content, Instant time) {
        this.role = role;
        this.content = content;
        this.time = time;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getTime() {
        return time;
    }
}


