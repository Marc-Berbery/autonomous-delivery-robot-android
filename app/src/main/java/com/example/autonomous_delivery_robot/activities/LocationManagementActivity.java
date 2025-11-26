package com.example.autonomous_delivery_robot.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.autonomous_delivery_robot.R;
import com.example.autonomous_delivery_robot.adapters.LocationAdapter;
import com.example.autonomous_delivery_robot.firebase.LocationService;
import com.example.autonomous_delivery_robot.models.Location;
import com.example.autonomous_delivery_robot.models.User;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.List;
import android.widget.EditText;

public class LocationManagementActivity extends BaseActivity {

    private static final String TAG = "LocationManagement";
    private EditText editTextLocationName;
    private Button buttonAddLocation;
    private ListView listViewLocations;
    private ProgressBar progressLoading;

    private LocationService locationService;
    private LocationAdapter locationAdapter;
    private List<Location> locations = new ArrayList<>();
    private User currentUser;

    private FirebaseAuth mAuth;
    private DatabaseReference dbRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_management);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        FirebaseDatabase database = FirebaseDatabase.getInstance("https://autonomous-delivery-robo-53d40-default-rtdb.europe-west1.firebasedatabase.app/");
        dbRef = database.getReference();

        // Initialize service
        locationService = new LocationService();

        // UI elements
        editTextLocationName = findViewById(R.id.editTextLocationName);
        buttonAddLocation = findViewById(R.id.buttonAddLocation);
        listViewLocations = findViewById(R.id.listViewLocations);
        progressLoading = findViewById(R.id.progressLoading);
        setupBottomNavigation();

        // Set up location adapter
        locationAdapter = new LocationAdapter(this, locations);
        listViewLocations.setAdapter(locationAdapter);

        // Check if user is logged in
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Get current user
        getCurrentUser(firebaseUser.getEmail());

        // Add location button click
        buttonAddLocation.setOnClickListener(v -> addLocation());

        // Set up item click listener for edit/delete options
        listViewLocations.setOnItemClickListener((parent, view, position, id) -> {
            Location location = locations.get(position);
            showLocationOptionsDialog(location);
        });
    }
    @Override
    protected int getBottomNavSelectedItemId() {
        return R.id.nav_locations;
    }
    private void getCurrentUser(String email) {
        showLoading(true);

        // Get the UID of the current signed-in user
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser != null) {
            String uid = firebaseUser.getUid();

            // Directly access the user data using the UID instead of querying by email
            dbRef.child("users").child(uid).get()
                    .addOnSuccessListener(dataSnapshot -> {
                        if (dataSnapshot.exists()) {
                            currentUser = dataSnapshot.getValue(User.class);
                            if (currentUser != null) {
                                Log.d(TAG, "User found: " + currentUser.getEmail() + " Role: " + currentUser.getRole());
                                loadLocations();
                            } else {
                                Toast.makeText(LocationManagementActivity.this, "User data is invalid", Toast.LENGTH_SHORT).show();
                                showLoading(false);
                            }
                        } else {
                            Toast.makeText(LocationManagementActivity.this, "User not found", Toast.LENGTH_SHORT).show();
                            showLoading(false);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error getting user: ", e);
                        Toast.makeText(LocationManagementActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        showLoading(false);
                    });
        } else {
            Toast.makeText(LocationManagementActivity.this, "Not logged in", Toast.LENGTH_SHORT).show();
            showLoading(false);
            finish();
        }
    }

    private void loadLocations() {
        locationService.getUserLocations(currentUser.getEmail(), new LocationService.LocationsCallback() {
            @Override
            public void onLocationsLoaded(List<Location> userLocations) {
                locations.clear();
                locations.addAll(userLocations);
                locationAdapter.notifyDataSetChanged();
                showLoading(false);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error loading locations: " + error);
                Toast.makeText(LocationManagementActivity.this, "Error loading locations: " + error, Toast.LENGTH_SHORT).show();
                showLoading(false);
            }
        });
    }

    private void addLocation() {
        String locationName = editTextLocationName.getText().toString().trim();
        if (locationName.isEmpty()) {
            editTextLocationName.setError("Please enter a location name");
            return;
        }

        showLoading(true);
        locationService.saveLocation(locationName, currentUser, new LocationService.LocationCallback() {
            @Override
            public void onLocationSaved(Location location) {
                editTextLocationName.setText("");
                Toast.makeText(LocationManagementActivity.this, "Location added successfully", Toast.LENGTH_SHORT).show();
                loadLocations(); // Reload the locations
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(LocationManagementActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                showLoading(false);
            }
        });
    }

    private void showLocationOptionsDialog(Location location) {
        String[] options = {"Edit Location", "Delete Location"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(location.getName())
                .setItems(options, (dialog, which) -> {
                    // Handle option selection
                    if (which == 0) { // Edit
                        showEditLocationDialog(location);
                    } else if (which == 1) { // Delete
                        confirmDeleteLocation(location);
                    }
                });
        builder.show();
    }

    private void confirmDeleteLocation(Location location) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Location")
                .setMessage("Are you sure you want to delete " + location.getName() + "? This will also delete all paths associated with this location.")
                .setPositiveButton("Delete", (dialog, which) -> deleteLocation(location.getName()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteLocation(String locationName) {
        showLoading(true);
        locationService.deleteLocation(locationName, new LocationService.LocationCallback() {
            @Override
            public void onLocationSaved(Location location) {
                Toast.makeText(LocationManagementActivity.this, "Location deleted successfully", Toast.LENGTH_SHORT).show();
                loadLocations(); // Reload the locations
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(LocationManagementActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                showLoading(false);
            }
        });
    }

    private void showLoading(boolean isLoading) {
        runOnUiThread(() -> {
            if (progressLoading != null) {
                progressLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            }

            if (buttonAddLocation != null) {
                buttonAddLocation.setEnabled(!isLoading);
            }

            if (listViewLocations != null) {
                listViewLocations.setEnabled(!isLoading);
            }
        });
    }

    private void showEditLocationDialog(Location location) {
        // Create an EditText for the dialog
        final EditText editText = new EditText(this);
        editText.setText(location.getName());
        editText.setSelectAllOnFocus(true);

        // Set layout parameters
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        editText.setLayoutParams(layoutParams);

        // Add padding to the EditText (using a direct pixel value)
        int padding = 16 * (int) getResources().getDisplayMetrics().density; // 16dp converted to pixels

        // Create container to add padding
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(editText);
        container.setPadding(padding, padding, padding, padding);

        new AlertDialog.Builder(this)
                .setTitle("Edit Location Name")
                .setView(container)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = editText.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        updateLocationName(location, newName);
                    } else {
                        Toast.makeText(this, "Location name cannot be empty", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    private void updateLocationName(Location location, String newName) {
        // Don't update if the name is the same
        if (location.getName().equals(newName)) {
            return;
        }

        showLoading(true);
        locationService.updateLocationName(location.getName(), newName, currentUser, new LocationService.LocationCallback() {
            @Override
            public void onLocationSaved(Location updatedLocation) {
                Toast.makeText(LocationManagementActivity.this,
                        "Location updated successfully", Toast.LENGTH_SHORT).show();
                loadLocations(); // Reload the locations
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(LocationManagementActivity.this,
                        errorMessage, Toast.LENGTH_SHORT).show();
                showLoading(false);
            }
        });
    }
}