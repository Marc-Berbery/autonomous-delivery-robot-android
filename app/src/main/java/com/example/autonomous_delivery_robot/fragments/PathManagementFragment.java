package com.example.autonomous_delivery_robot.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.autonomous_delivery_robot.R;
import com.example.autonomous_delivery_robot.activities.ControlActivity;
import com.example.autonomous_delivery_robot.activities.PathManagementActivity;
import com.example.autonomous_delivery_robot.adapters.PathAdapter;
import com.example.autonomous_delivery_robot.firebase.LocationService;
import com.example.autonomous_delivery_robot.firebase.PathService;
import com.example.autonomous_delivery_robot.models.Location;
import com.example.autonomous_delivery_robot.models.Path;
import com.example.autonomous_delivery_robot.models.User;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.List;

public class PathManagementFragment extends Fragment {

    private static final String TAG = "PathManagement";
    private Spinner spinnerLocations;
    private Button buttonAddPath;
    private ListView listViewPaths;
    private ProgressBar progressLoading;
    private View emptyStateView;

    private LocationService locationService;
    private PathService pathService;
    private PathAdapter pathAdapter;
    private List<Location> locations = new ArrayList<>();
    private List<Path> paths = new ArrayList<>();
    private User currentUser;
    private Location selectedLocation;

    private FirebaseAuth mAuth;
    private DatabaseReference dbRef;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_path_management, container, false);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        FirebaseDatabase database = FirebaseDatabase.getInstance("https://autonomous-delivery-robo-53d40-default-rtdb.europe-west1.firebasedatabase.app/");
        dbRef = database.getReference();

        // Initialize services
        locationService = new LocationService();
        pathService = new PathService();

        // UI elements
        spinnerLocations = view.findViewById(R.id.spinnerLocations);
        buttonAddPath = view.findViewById(R.id.buttonAddPath);
        listViewPaths = view.findViewById(R.id.listViewPaths);
        progressLoading = view.findViewById(R.id.progressLoading);
        emptyStateView = view.findViewById(R.id.emptyStateContainer);

        // Set up path adapter
        pathAdapter = new PathAdapter(getActivity(), paths);
        listViewPaths.setAdapter(pathAdapter);

        // Check if user is logged in
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null) {
            Toast.makeText(getActivity(), "Please log in first", Toast.LENGTH_SHORT).show();
            return view;
        }

        // Get current user
        getCurrentUser(firebaseUser.getEmail());

        // Location spinner listener
        spinnerLocations.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < locations.size()) {
                    selectedLocation = locations.get(position);
                    loadPaths(selectedLocation.getName());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedLocation = null;
                paths.clear();
                pathAdapter.notifyDataSetChanged();

                // Show empty state
                updateEmptyState();
            }
        });

        // Add path button click - now redirects to ControlActivity
        buttonAddPath.setOnClickListener(v -> startPathRecording());

        // Set up item click listener for edit/delete options
        listViewPaths.setOnItemClickListener((parent, v, position, id) -> {
            Path path = paths.get(position);
            showPathOptionsDialog(path);
        });

        return view;
    }

    // New method to start path recording in the ControlActivity
    private void startPathRecording() {
        if (selectedLocation == null) {
            Toast.makeText(getActivity(), "Please select a location first", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(getActivity(), ControlActivity.class);
        intent.putExtra(PathManagementActivity.EXTRA_LOCATION_NAME, selectedLocation.getName());
        intent.putExtra("recording_mode", true); // Flag for control activity to start in recording mode
        startActivity(intent);
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
                                Toast.makeText(getActivity(), "User data is invalid", Toast.LENGTH_SHORT).show();
                                showLoading(false);
                            }
                        } else {
                            Toast.makeText(getActivity(), "User not found", Toast.LENGTH_SHORT).show();
                            showLoading(false);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error getting user: ", e);
                        Toast.makeText(getActivity(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        showLoading(false);
                    });
        } else {
            Toast.makeText(getActivity(), "Not logged in", Toast.LENGTH_SHORT).show();
            showLoading(false);
        }
    }

    private void loadLocations() {
        locationService.getUserLocations(currentUser.getEmail(), new LocationService.LocationsCallback() {
            @Override
            public void onLocationsLoaded(List<Location> userLocations) {
                locations.clear();
                locations.addAll(userLocations);

                if (locations.isEmpty()) {
                    Toast.makeText(getActivity(), "Please add locations first", Toast.LENGTH_LONG).show();
                    return;
                }

                updateLocationSpinner();
                showLoading(false);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error loading locations: " + error);
                Toast.makeText(getActivity(), "Error loading locations: " + error, Toast.LENGTH_SHORT).show();
                showLoading(false);
            }
        });
    }

    private void updateLocationSpinner() {
        List<String> locationNames = new ArrayList<>();
        for (Location location : locations) {
            locationNames.add(location.getName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                getActivity(), R.layout.spinner_item_white, locationNames);
        adapter.setDropDownViewResource(R.layout.spinner_item_white);
        spinnerLocations.setAdapter(adapter);

        // Select first item if available
        if (!locations.isEmpty()) {
            spinnerLocations.setSelection(0);
            selectedLocation = locations.get(0);
            loadPaths(selectedLocation.getName());
        }
    }

    private void loadPaths(String locationName) {
        showLoading(true);
        pathService.getPathsForLocation(locationName, new PathService.PathsCallback() {
            @Override
            public void onPathsLoaded(List<Path> locationPaths) {
                paths.clear();
                paths.addAll(locationPaths);
                pathAdapter.notifyDataSetChanged();

                // Update empty state visibility
                updateEmptyState();

                showLoading(false);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error loading paths: " + error);
                Toast.makeText(getActivity(), "Error loading paths: " + error, Toast.LENGTH_SHORT).show();
                showLoading(false);
            }
        });
    }

    private void updateEmptyState() {
        if (paths.isEmpty()) {
            emptyStateView.setVisibility(View.VISIBLE);
            listViewPaths.setVisibility(View.GONE);
        } else {
            emptyStateView.setVisibility(View.GONE);
            listViewPaths.setVisibility(View.VISIBLE);
        }
    }

    private void showPathOptionsDialog(Path path) {
        String[] options = {"Delete Path"};

        new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(path.getPathName())
                .setItems(options, (dialog, which) -> {
                    // Handle option selection
                    if (which == 0) { // Delete
                        confirmDeletePath(path);
                    }
                })
                .show();
    }

    private void confirmDeletePath(Path path) {
        new MaterialAlertDialogBuilder(requireActivity())
                .setTitle("Delete Path")
                .setMessage("Are you sure you want to delete " + path.getPathName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> deletePath(path.getPathName()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deletePath(String pathName) {
        showLoading(true);
        pathService.deletePath(pathName, new PathService.PathCallback() {
            @Override
            public void onPathSaved(Path path) {
                Toast.makeText(getActivity(), "Path deleted successfully", Toast.LENGTH_SHORT).show();
                if (selectedLocation != null) {
                    loadPaths(selectedLocation.getName()); // Reload the paths
                }
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(getActivity(), errorMessage, Toast.LENGTH_SHORT).show();
                showLoading(false);
            }
        });
    }

    private void showLoading(boolean isLoading) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (progressLoading != null) {
                    progressLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
                }

                if (buttonAddPath != null) {
                    buttonAddPath.setEnabled(!isLoading);
                }

                if (spinnerLocations != null) {
                    spinnerLocations.setEnabled(!isLoading);
                }

                if (listViewPaths != null) {
                    listViewPaths.setEnabled(!isLoading);
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh paths when returning to this fragment (e.g., after recording a new path)
        if (selectedLocation != null) {
            loadPaths(selectedLocation.getName());
        }
    }
}