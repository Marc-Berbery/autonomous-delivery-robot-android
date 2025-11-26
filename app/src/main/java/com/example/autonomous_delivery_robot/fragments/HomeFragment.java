package com.example.autonomous_delivery_robot.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.autonomous_delivery_robot.R;
import com.example.autonomous_delivery_robot.activities.AdminActivity;
import com.example.autonomous_delivery_robot.activities.AuthActivity;
import com.example.autonomous_delivery_robot.activities.ControlActivity;
import com.example.autonomous_delivery_robot.activities.DeliveryActivity;
import com.example.autonomous_delivery_robot.activities.HistoryActivity;
import com.example.autonomous_delivery_robot.activities.LocationManagementActivity;
import com.example.autonomous_delivery_robot.activities.PathManagementActivity;
import com.example.autonomous_delivery_robot.activities.SensorActivity;
import com.example.autonomous_delivery_robot.firebase.UserService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private MaterialCardView btnControl, btnDelivery, btnLocations, btnPaths, btnSensors, btnHistory, btnAdminPanel;
    private MaterialButton btnLogout;

    private FirebaseAuth firebaseAuth;
    private UserService userService;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        firebaseAuth = FirebaseAuth.getInstance();
        userService = new UserService();

        // Initialize UI elements
        btnControl = view.findViewById(R.id.btnControl);
        btnDelivery = view.findViewById(R.id.btnDelivery);
        btnLocations = view.findViewById(R.id.btnLocations);
        btnPaths = view.findViewById(R.id.btnPaths);
        btnSensors = view.findViewById(R.id.btnSensors);
        btnHistory = view.findViewById(R.id.btnHistory);
        btnAdminPanel = view.findViewById(R.id.btnAdminPanel);
        btnLogout = view.findViewById(R.id.btnLogout);

        setupClickListeners();
        checkUserRoleAndConfigureUI();

        return view;
    }

    private void setupClickListeners() {
        btnControl.setOnClickListener(v -> startActivity(new Intent(getActivity(), ControlActivity.class)));
        btnDelivery.setOnClickListener(v -> startActivity(new Intent(getActivity(), DeliveryActivity.class)));
        btnLocations.setOnClickListener(v -> startActivity(new Intent(getActivity(), LocationManagementActivity.class)));
        btnPaths.setOnClickListener(v -> startActivity(new Intent(getActivity(), PathManagementActivity.class)));
        btnSensors.setOnClickListener(v -> startActivity(new Intent(getActivity(), SensorActivity.class)));
        btnHistory.setOnClickListener(v -> startActivity(new Intent(getActivity(), HistoryActivity.class)));
        btnAdminPanel.setOnClickListener(v -> startActivity(new Intent(getActivity(), AdminActivity.class)));

        btnLogout.setOnClickListener(v -> {
            Log.d(TAG, "Logging out user");
            firebaseAuth.signOut();
            startActivity(new Intent(getActivity(), AuthActivity.class));
            getActivity().finish();
        });
    }

    private void checkUserRoleAndConfigureUI() {
        btnAdminPanel.setVisibility(View.GONE);

        userService.getCurrentUserRole(new UserService.UserRoleCallback() {
            @Override
            public void onRoleReceived(String role) {
                Log.d(TAG, "User role received: " + role);
                if (UserService.ROLE_ADMIN.equals(role)) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            btnAdminPanel.setVisibility(View.VISIBLE);
                            Toast.makeText(getActivity(), "Admin access granted", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Error getting user role: " + errorMessage);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> Toast.makeText(getActivity(),
                            "Error retrieving user role: " + errorMessage,
                            Toast.LENGTH_SHORT).show());
                }
            }
        });
    }
}