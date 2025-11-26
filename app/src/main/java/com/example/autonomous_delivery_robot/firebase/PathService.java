package com.example.autonomous_delivery_robot.firebase;

import android.util.Log;
import com.example.autonomous_delivery_robot.models.Location;
import com.example.autonomous_delivery_robot.models.Path;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;

public class PathService {

    private static final String TAG = "PathService";
    private final DatabaseReference pathsRef;
    private final LocationService locationService;

    public interface PathsCallback {
        void onPathsLoaded(List<Path> paths);
        void onError(String errorMessage);
    }

    public interface PathCallback {
        void onPathSaved(Path path);
        void onError(String errorMessage);
    }

    public PathService() {
        FirebaseDatabase database = FirebaseDatabase.getInstance("https://autonomous-delivery-robo-53d40-default-rtdb.europe-west1.firebasedatabase.app/");
        pathsRef = database.getReference("paths");
        locationService = new LocationService();

        Log.d(TAG, "PathService initialized with reference: " + pathsRef.toString());
    }

    /**
     * Get all paths for a specific location
     * @param locationName Name of the location
     * @param callback Callback to receive paths
     */
    public void getPathsForLocation(String locationName, PathsCallback callback) {
        Log.d(TAG, "Getting paths for location: " + locationName);

        pathsRef.orderByChild("location/name").equalTo(locationName)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<Path> paths = new ArrayList<>();
                        for (DataSnapshot pathSnapshot : snapshot.getChildren()) {
                            Path path = pathSnapshot.getValue(Path.class);
                            if (path != null) {
                                paths.add(path);
                                Log.d(TAG, "Found path: " + path.getPathName() + " for location: " + locationName);
                            }
                        }

                        Log.d(TAG, "Found " + paths.size() + " paths for location: " + locationName);
                        callback.onPathsLoaded(paths);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error getting paths: " + error.getMessage(), error.toException());
                        callback.onError(error.getMessage());
                    }
                });
    }

    /**
     * Save a new path
     * @param pathName Path name
     * @param instructionSet Instructions for robot to follow
     * @param locationName Location name this path is for
     * @param callback Callback to handle result
     */
    public void savePath(String pathName, String instructionSet, String locationName, PathCallback callback) {
        // First, get the location
        locationService.getLocationByName(locationName, new LocationService.LocationCallback() {
            @Override
            public void onLocationSaved(Location location) {
                if (location == null) {
                    callback.onError("Location not found");
                    return;
                }

                // Create the path
                Path path = new Path(pathName, instructionSet, location);

                // Check if path with this name already exists
                pathsRef.orderByChild("pathName").equalTo(pathName)
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                if (snapshot.exists()) {
                                    callback.onError("A path with this name already exists");
                                    return;
                                }

                                // Path doesn't exist, save it
                                String pathId = pathsRef.push().getKey();
                                if (pathId != null) {
                                    pathsRef.child(pathId).setValue(path)
                                            .addOnSuccessListener(aVoid -> {
                                                Log.d(TAG, "Path saved successfully: " + pathName);
                                                callback.onPathSaved(path);
                                            })
                                            .addOnFailureListener(e -> {
                                                Log.e(TAG, "Error saving path: " + e.getMessage(), e);
                                                callback.onError("Failed to save path: " + e.getMessage());
                                            });
                                } else {
                                    callback.onError("Failed to generate path ID");
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                Log.e(TAG, "Error checking existing path: " + error.getMessage(), error.toException());
                                callback.onError("Database error: " + error.getMessage());
                            }
                        });
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    /**
     * Delete a path
     * @param pathName Name of the path to delete
     * @param callback Callback to handle result
     */
    public void deletePath(String pathName, PathCallback callback) {
        pathsRef.orderByChild("pathName").equalTo(pathName)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            callback.onError("Path not found");
                            return;
                        }

                        for (DataSnapshot pathSnapshot : snapshot.getChildren()) {
                            pathSnapshot.getRef().removeValue()
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d(TAG, "Path deleted successfully: " + pathName);
                                        callback.onPathSaved(null);
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Error deleting path: " + e.getMessage(), e);
                                        callback.onError("Failed to delete path: " + e.getMessage());
                                    });
                            break; // Only delete the first one if there are multiple (shouldn't happen)
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error finding path to delete: " + error.getMessage(), error.toException());
                        callback.onError("Database error: " + error.getMessage());
                    }
                });
    }
}