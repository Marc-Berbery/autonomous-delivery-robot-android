package com.example.autonomous_delivery_robot.firebase;

import android.util.Log;
import com.example.autonomous_delivery_robot.models.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import androidx.annotation.NonNull;

public class UserService {
    
    private static final String TAG = "UserService";
    private final DatabaseReference usersRef;
    private final FirebaseAuth firebaseAuth;
    
    // Constants for user roles
    public static final String ROLE_USER = "user";
    public static final String ROLE_ADMIN = "admin";
    
    public interface UserRoleCallback {
        void onRoleReceived(String role);
        void onError(String errorMessage);
    }
    
    public UserService() {
        // Use the European database URL - replace with your actual Firebase database URL
        FirebaseDatabase database = FirebaseDatabase.getInstance("https://autonomous-delivery-robo-53d40-default-rtdb.europe-west1.firebasedatabase.app/");
        usersRef = database.getReference("users");
        firebaseAuth = FirebaseAuth.getInstance();
        
        Log.d(TAG, "UserService initialized with database URL: " + database.getReference().toString());
    }
    
    /**
     * Save user data to Firebase Database
     * @param email User's email
     * @param role User's role (admin or user)
     */
    public void saveUser(String email, String role) {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            String uid = currentUser.getUid();
            User user = new User(email, role);
            
            Log.d(TAG, "Attempting to save user with email: " + email + ", role: " + role + ", uid: " + uid);
            
            usersRef.child(uid).setValue(user)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User data saved successfully for uid: " + uid);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving user data: " + e.getMessage(), e);
                });
        } else {
            Log.e(TAG, "Cannot save user data: Current user is null");
        }
    }
    
    /**
     * Get the current user's role from Firebase
     * @param callback Callback to receive the user role
     */
    public void getCurrentUserRole(UserRoleCallback callback) {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            String uid = currentUser.getUid();
            Log.d(TAG, "Getting role for user: " + uid);
            getUserRole(uid, callback);
        } else {
            Log.e(TAG, "No user is signed in");
            callback.onError("No user is signed in");
        }
    }
    
    /**
     * Get user role by user ID
     * @param userId Firebase user ID
     * @param callback Callback to receive the user role
     */
    private void getUserRole(String userId, UserRoleCallback callback) {
        usersRef.child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    User user = snapshot.getValue(User.class);
                    if (user != null && user.role != null) {
                        Log.d(TAG, "Retrieved role for user " + userId + ": " + user.role);
                        callback.onRoleReceived(user.role);
                    } else {
                        Log.d(TAG, "User exists but role is null, defaulting to USER role");
                        callback.onRoleReceived(ROLE_USER);
                    }
                } else {
                    Log.d(TAG, "User data doesn't exist in database for " + userId + ", defaulting to USER role");
                    callback.onRoleReceived(ROLE_USER);
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to read user data: " + error.getMessage(), error.toException());
                callback.onError("Failed to read user data: " + error.getMessage());
            }
        });
    }
    
    /**
     * Set a user as admin
     * @param userId Firebase user ID
     */
    public void setUserAsAdmin(String userId) {
        Log.d(TAG, "Setting user " + userId + " as admin");
        usersRef.child(userId).child("role").setValue(ROLE_ADMIN)
            .addOnSuccessListener(aVoid -> Log.d(TAG, "User " + userId + " set as admin successfully"))
            .addOnFailureListener(e -> Log.e(TAG, "Error setting user as admin: " + e.getMessage(), e));
    }
    
    /**
     * Set a user as regular user
     * @param userId Firebase user ID
     */
    public void setUserAsRegular(String userId) {
        Log.d(TAG, "Setting user " + userId + " as regular user");
        usersRef.child(userId).child("role").setValue(ROLE_USER)
            .addOnSuccessListener(aVoid -> Log.d(TAG, "User " + userId + " set as regular user successfully"))
            .addOnFailureListener(e -> Log.e(TAG, "Error setting user as regular: " + e.getMessage(), e));
    }
}