package com.example.autonomous_delivery_robot.firebase;

import android.util.Log;
import com.example.autonomous_delivery_robot.models.Location;
import com.example.autonomous_delivery_robot.models.Path;
import com.example.autonomous_delivery_robot.models.User;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;

public class LocationService {

    private static final String TAG = "LocationService";
    private final DatabaseReference locationsRef;

    public interface LocationsCallback {
        void onLocationsLoaded(List<Location> locations);
        void onError(String errorMessage);
    }

    public interface LocationCallback {
        void onLocationSaved(Location location);
        void onError(String errorMessage);
    }

    public LocationService() {
        FirebaseDatabase database = FirebaseDatabase.getInstance("https://autonomous-delivery-robo-53d40-default-rtdb.europe-west1.firebasedatabase.app/");
        locationsRef = database.getReference("locations");

        Log.d(TAG, "LocationService initialized with reference: " + locationsRef.toString());
    }

    /**
     * Get all locations for a specific user
     * @param userEmail Email of the user
     * @param callback Callback to receive locations
     */
    public void getUserLocations(String userEmail, LocationsCallback callback) {
        Log.d(TAG, "Getting locations for user: " + userEmail);

        locationsRef.orderByChild("user/email").equalTo(userEmail)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<Location> locations = new ArrayList<>();
                        for (DataSnapshot locSnapshot : snapshot.getChildren()) {
                            Location location = locSnapshot.getValue(Location.class);
                            if (location != null) {
                                locations.add(location);
                                Log.d(TAG, "Found location: " + location.getName());
                            }
                        }

                        Log.d(TAG, "Found " + locations.size() + " locations for user: " + userEmail);
                        callback.onLocationsLoaded(locations);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error getting locations: " + error.getMessage(), error.toException());
                        callback.onError(error.getMessage());
                    }
                });
    }

    /**
     * Save a new location
     * @param name Location name
     * @param user User who owns this location
     * @param callback Callback to handle result
     */
    public void saveLocation(String name, User user, LocationCallback callback) {
        Location location = new Location(name, user);

        // Check if location with this name already exists
        locationsRef.orderByChild("name").equalTo(name)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            callback.onError("A location with this name already exists");
                            return;
                        }

                        // Location doesn't exist, save it
                        String locationId = locationsRef.push().getKey();
                        if (locationId != null) {
                            locationsRef.child(locationId).setValue(location)
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d(TAG, "Location saved successfully: " + name);
                                        callback.onLocationSaved(location);
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Error saving location: " + e.getMessage(), e);
                                        callback.onError("Failed to save location: " + e.getMessage());
                                    });
                        } else {
                            callback.onError("Failed to generate location ID");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error checking existing location: " + error.getMessage(), error.toException());
                        callback.onError("Database error: " + error.getMessage());
                    }
                });
    }

    /**
     * Delete a location
     * @param locationName Name of the location to delete
     * @param callback Callback to handle result
     */
    public void deleteLocation(String locationName, LocationCallback callback) {
        locationsRef.orderByChild("name").equalTo(locationName)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            callback.onError("Location not found");
                            return;
                        }

                        for (DataSnapshot locSnapshot : snapshot.getChildren()) {
                            locSnapshot.getRef().removeValue()
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d(TAG, "Location deleted successfully: " + locationName);
                                        callback.onLocationSaved(null);
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Error deleting location: " + e.getMessage(), e);
                                        callback.onError("Failed to delete location: " + e.getMessage());
                                    });
                            break; // Only delete the first one if there are multiple (shouldn't happen)
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error finding location to delete: " + error.getMessage(), error.toException());
                        callback.onError("Database error: " + error.getMessage());
                    }
                });
    }

    /**
     * Get a location by name
     * @param locationName Name of the location
     * @param callback Callback to receive the location
     */
    public void getLocationByName(String locationName, LocationCallback callback) {
        locationsRef.orderByChild("name").equalTo(locationName)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            callback.onError("Location not found");
                            return;
                        }

                        for (DataSnapshot locSnapshot : snapshot.getChildren()) {
                            Location location = locSnapshot.getValue(Location.class);
                            if (location != null) {
                                Log.d(TAG, "Location found: " + locationName);
                                callback.onLocationSaved(location);
                            } else {
                                callback.onError("Error parsing location data");
                            }
                            return; // Only return the first one if there are multiple (shouldn't happen)
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error getting location: " + error.getMessage(), error.toException());
                        callback.onError("Database error: " + error.getMessage());
                    }
                });
    }

    // Add this to LocationService.java
    public void updateLocationName(String oldName, String newName, User user, LocationCallback callback) {
        // First check if the new name already exists
        locationsRef.orderByChild("name").equalTo(newName)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            callback.onError("A location with this name already exists");
                            return;
                        }

                        // Now find the location with the old name
                        locationsRef.orderByChild("name").equalTo(oldName)
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                                        if (!snapshot.exists()) {
                                            callback.onError("Original location not found");
                                            return;
                                        }

                                        // Get the location key and update it
                                        for (DataSnapshot locSnapshot : snapshot.getChildren()) {
                                            Location location = locSnapshot.getValue(Location.class);
                                            // Update only if this location belongs to the current user
                                            if (location != null &&
                                                    location.getUser() != null &&
                                                    location.getUser().getEmail().equals(user.getEmail())) {

                                                // Create updated location
                                                Location updatedLocation = new Location(newName, user);

                                                // Update in database
                                                locSnapshot.getRef().setValue(updatedLocation)
                                                        .addOnSuccessListener(aVoid -> {
                                                            // Now we need to update all paths that use this location
                                                            updatePathsForRenamedLocation(oldName, updatedLocation);
                                                            callback.onLocationSaved(updatedLocation);
                                                        })
                                                        .addOnFailureListener(e -> {
                                                            callback.onError("Failed to update location: " + e.getMessage());
                                                        });
                                                return;
                                            }
                                        }

                                        callback.onError("Could not update: Location not owned by current user");
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {
                                        callback.onError("Database error: " + error.getMessage());
                                    }
                                });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError("Database error: " + error.getMessage());
                    }
                });
    }

    // Helper method to update all paths that use the renamed location
    private void updatePathsForRenamedLocation(String oldLocationName, Location updatedLocation) {
        // Get reference to paths
        DatabaseReference pathsRef = FirebaseDatabase.getInstance()
                .getReference("paths");

        // Find all paths that use this location
        pathsRef.orderByChild("location/name").equalTo(oldLocationName)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot pathSnapshot : snapshot.getChildren()) {
                            Path path = pathSnapshot.getValue(Path.class);
                            if (path != null) {
                                // Update the location reference in this path
                                path.setLocation(updatedLocation);
                                pathSnapshot.getRef().setValue(path);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error updating paths for renamed location: " + error.getMessage());
                    }
                });
    }
}