package com.example.autonomous_delivery_robot.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.autonomous_delivery_robot.R;
import com.example.autonomous_delivery_robot.firebase.UserService;
import com.example.autonomous_delivery_robot.fragments.HistoryFragment;
import com.example.autonomous_delivery_robot.fragments.PathManagementFragment;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;

public class MoreOptionsBottomSheet extends BottomSheetDialogFragment {

    private LinearLayout optionPaths, optionHistory, optionSensors, optionAdmin, optionLogout;
    private UserService userService;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_more_options, container, false);

        userService = new UserService();

        // Initialize UI elements
        optionPaths = view.findViewById(R.id.optionPaths);
        optionHistory = view.findViewById(R.id.optionHistory);
        optionSensors = view.findViewById(R.id.optionSensors);
        optionAdmin = view.findViewById(R.id.optionAdmin);
        optionLogout = view.findViewById(R.id.optionLogout);

        setupClickListeners();
        checkUserRoleAndConfigureUI();

        return view;
    }

    private void setupClickListeners() {
        optionPaths.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).loadFragment(new PathManagementFragment());
                dismiss();
            }
        });

        optionHistory.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).loadFragment(new HistoryFragment());
                dismiss();
            }
        });

        optionSensors.setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), SensorActivity.class));
            dismiss();
        });

        optionAdmin.setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), AdminActivity.class));
            dismiss();
        });

        optionLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(getActivity(), AuthActivity.class));
            if (getActivity() != null) {
                getActivity().finish();
            }
            dismiss();
        });
    }

    private void checkUserRoleAndConfigureUI() {
        optionAdmin.setVisibility(View.GONE);

        userService.getCurrentUserRole(new UserService.UserRoleCallback() {
            @Override
            public void onRoleReceived(String role) {
                if (UserService.ROLE_ADMIN.equals(role) && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        optionAdmin.setVisibility(View.VISIBLE);
                    });
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> Toast.makeText(getActivity(),
                            "Error retrieving user role: " + errorMessage,
                            Toast.LENGTH_SHORT).show());
                }
            }
        });
    }
}