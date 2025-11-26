package com.example.autonomous_delivery_robot.models;

public class Location {

    private String Name;
    private User user;
    public Location() {
    }
    public Location(String name, User user) {
        this.Name = name;
        this.user = user;
    }
    public String getName() {
        return Name;
    }
    public void setName(String name) {
        this.Name = name;
    }
    public User getUser() {
        return user;
    }
    public void setUser(User user) {
        this.user = user;
    }

}
