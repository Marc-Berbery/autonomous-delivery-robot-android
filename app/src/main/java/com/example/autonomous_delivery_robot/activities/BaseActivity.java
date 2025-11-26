package com.example.autonomous_delivery_robot.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.autonomous_delivery_robot.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    protected void setupBottomNavigation() {
        // Find the bottom navigation view if it exists
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        if (bottomNavigationView == null) return;

        // Set the appropriate item as selected based on the current activity
        int selectedItemId = getBottomNavSelectedItemId();
        if (selectedItemId != 0) {
            bottomNavigationView.setSelectedItemId(selectedItemId);
        }

        // Set up navigation listener
        bottomNavigationView.setOnNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();

            // Don't navigate if we're already on the selected page
            if (itemId == getBottomNavSelectedItemId()) {
                return true;
            }

            // Navigate to the selected activity
            if (itemId == R.id.nav_home) {
                navigateToActivity(MainActivity.class);
                return true;
            } else if (itemId == R.id.nav_delivery) {
                navigateToActivity(DeliveryActivity.class);
                return true;
            } else if (itemId == R.id.nav_locations) {
                navigateToActivity(LocationManagementActivity.class);
                return true;
            } else if (itemId == R.id.nav_more) {
                showMoreOptions();
                return true;
            }
            return false;
        });
    }

    // Method to be overridden by specific activities to return their corresponding nav item
    protected int getBottomNavSelectedItemId() {
        return 0; // Default return value, should be overridden
    }

    protected void navigateToActivity(Class<?> activityClass) {
        Intent intent = new Intent(this, activityClass);
        startActivity(intent);
        finish();
    }

    protected void showMoreOptions() {
        BottomSheetDialogFragment bottomSheet = new MoreOptionsBottomSheet();
        bottomSheet.show(getSupportFragmentManager(), "MoreOptionsBottomSheet");
    }
}