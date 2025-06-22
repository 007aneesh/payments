package com.example.multipaymentgateway.webhook;

import java.util.Map;

public class WebhookEvent {

    private String eventId;
    private String eventType;
    private Map<String, Object> data; // Using a Map for flexible data structure

    // Constructors
    public WebhookEvent() {
    }

    public WebhookEvent(String eventId, String eventType, Map<String, Object> data) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.data = data;
    }

    // Getters and Setters
    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "WebhookEvent{" +
                "eventId='" + eventId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", data=" + data +
                '}';
    }
}
