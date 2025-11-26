package com.example.autonomous_delivery_robot.models;

public class Path {
    private String pathName;
    private String instructionSet; // JSON formatted string
    private Location location;

    public Path() {
        // Required empty constructor for Firebase
    }

    public Path(String pathName, String instructionSet, Location location) {
        this.pathName = pathName;
        this.instructionSet = instructionSet;
        this.location = location;
    }

    // Getters and setters
    public String getPathName() {
        return pathName;
    }

    public void setPathName(String pathName) {
        this.pathName = pathName;
    }

    public String getInstructionSet() {
        return instructionSet;
    }

    public void setInstructionSet(String instructionSet) {
        this.instructionSet = instructionSet;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    // Get the user from the location if available
    public User getLocationUser() {
        return location != null ? location.getUser() : null;
    }

    @Override
    public String toString() {
        return pathName + " (" + (location != null ? location.getName() : "unknown location") + ")";
    }
}