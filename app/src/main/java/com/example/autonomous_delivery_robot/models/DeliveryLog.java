package com.example.autonomous_delivery_robot.models;

import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.PropertyName;

@IgnoreExtraProperties
public class DeliveryLog {
    private String orderId;
    private String timestamp;
    private String topic;
    private String message;

    // Required empty constructor for Firebase
    public DeliveryLog() {}

    public DeliveryLog(String orderId, String timestamp, String topic, String message) {
        this.orderId = orderId;
        this.timestamp = timestamp;
        this.topic = topic;
        this.message = message;
    }

    // Getters and setters
    @PropertyName("orderId")
    public String getOrderId() {
        return orderId;
    }

    @PropertyName("orderId")
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    @PropertyName("timestamp")
    public String getTimestamp() {
        return timestamp;
    }

    @PropertyName("timestamp")
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    @PropertyName("topic")
    public String getTopic() {
        return topic;
    }

    @PropertyName("topic")
    public void setTopic(String topic) {
        this.topic = topic;
    }

    @PropertyName("message")
    public String getMessage() {
        return message;
    }

    @PropertyName("message")
    public void setMessage(String message) {
        this.message = message;
    }
}