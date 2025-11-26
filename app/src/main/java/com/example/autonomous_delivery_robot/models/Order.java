package com.example.autonomous_delivery_robot.models;

import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.PropertyName;

@IgnoreExtraProperties
public class Order {
    private Location location;
    private Path path;
    private User user;
    private String status;
    private String item;
    private String startDate;
    private String endDate;

    // Required empty constructor for Firebase
    public Order() {}

    public Order(Location location, Path path, User user, String status, String item, String startDate, String endDate) {
        this.location = location;
        this.path = path;
        this.user = user;
        this.status = status;
        this.item = item;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    // Getters and setters
    @PropertyName("location")
    public Location getLocation() {
        return location;
    }

    @PropertyName("location")
    public void setLocation(Location location) {
        this.location = location;
    }

    @PropertyName("path")
    public Path getPath() {
        return path;
    }

    @PropertyName("path")
    public void setPath(Path path) {
        this.path = path;
    }

    @PropertyName("user")
    public User getUser() {
        return user;
    }

    @PropertyName("user")
    public void setUser(User user) {
        this.user = user;
    }

    // Convenience method to get the location's user
    public User getLocationUser() {
        return location != null ? location.getUser() : null;
    }

    @PropertyName("status")
    public String getStatus() {
        return status;
    }

    @PropertyName("status")
    public void setStatus(String status) {
        this.status = status;
    }

    @PropertyName("item")
    public String getItem() {
        return item;
    }

    @PropertyName("item")
    public void setItem(String item) {
        this.item = item;
    }

    @PropertyName("startDate")
    public String getStartDate() {
        return startDate;
    }

    @PropertyName("startDate")
    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    @PropertyName("endDate")
    public String getEndDate() {
        return endDate;
    }

    @PropertyName("endDate")
    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    @Override
    public String toString() {
        return "Order{" +
                "location=" + (location != null ? location.getName() : "null") +
                ", locationUser=" + (getLocationUser() != null ? getLocationUser().getEmail() : "null") +
                ", path=" + (path != null ? path.getPathName() : "null") +
                ", user=" + (user != null ? user.getEmail() : "null") +
                ", status='" + status + '\'' +
                ", item='" + item + '\'' +
                ", startDate='" + startDate + '\'' +
                ", endDate='" + endDate + '\'' +
                '}';
    }
}