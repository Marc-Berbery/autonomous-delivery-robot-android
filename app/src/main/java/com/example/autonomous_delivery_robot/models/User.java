package com.example.autonomous_delivery_robot.models;

import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.PropertyName;

@IgnoreExtraProperties
public class User {
    @PropertyName("email")
    public String email;
    
    @PropertyName("role")
    public String role;

    // Required by Firebase for deserialization
    public User() {}

    public User(String email, String role) {
        this.email = email;
        this.role = role;
    }
    
    @PropertyName("email")
    public String getEmail() {
        return email;
    }
    
    @PropertyName("email")
    public void setEmail(String email) {
        this.email = email;
    }
    
    @PropertyName("role")
    public String getRole() {
        return role;
    }
    
    @PropertyName("role")
    public void setRole(String role) {
        this.role = role;
    }
    
    @Override
    public String toString() {
        return "User{email='" + email + "', role='" + role + "'}";
    }
}
