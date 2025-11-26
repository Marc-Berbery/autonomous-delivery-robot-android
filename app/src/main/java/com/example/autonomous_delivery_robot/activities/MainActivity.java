package com.example.autonomous_delivery_robot.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.autonomous_delivery_robot.R;
import com.example.autonomous_delivery_robot.fragments.DeliveryFragment;
import com.example.autonomous_delivery_robot.fragments.HistoryFragment;
import com.example.autonomous_delivery_robot.fragments.HomeFragment;
import com.example.autonomous_delivery_robot.fragments.LocationManagementFragment;
import com.example.autonomous_delivery_robot.fragments.PathManagementFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class MainActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "MainActivity";
    private FirebaseAuth firebaseAuth;
    private BottomNavigationView bottomNavigationView;

    // Track current fragment to avoid reloading the same fragment
    private Fragment activeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        firebaseAuth = FirebaseAuth.getInstance();

        // Check if user is logged in
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "No user is signed in, navigating to AuthActivity");
            startActivity(new Intent(this, AuthActivity.class));
            finish();
            return;
        }

        Log.d(TAG, "User signed in: " + currentUser.getUid() + ", Email: " + currentUser.getEmail());

        // Set up the bottom navigation
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnNavigationItemSelectedListener(this);

        // Initial fragment (Home)
        if (savedInstanceState == null) {
            activeFragment = new HomeFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, activeFragment)
                    .commit();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Fragment selectedFragment = null;

        int itemId = item.getItemId();
        if (itemId == R.id.nav_home) {
            selectedFragment = new HomeFragment();
        } else if (itemId == R.id.nav_delivery) {
            selectedFragment = new DeliveryFragment();
        } else if (itemId == R.id.nav_locations) {
            selectedFragment = new LocationManagementFragment();
        } else if (itemId == R.id.nav_more) {
            // Show more options menu
            MoreOptionsBottomSheet bottomSheet = new MoreOptionsBottomSheet();
            bottomSheet.show(getSupportFragmentManager(), "MoreOptionsBottomSheet");
            return true;
        }

        if (selectedFragment != null) {
            // Only replace if it's a different fragment
            if (!(activeFragment != null && activeFragment.getClass().equals(selectedFragment.getClass()))) {
                activeFragment = selectedFragment;
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
            }
            return true;
        }

        return false;
    }

    // Method to load a specific fragment (can be called from MoreOptionsBottomSheet)
    public void loadFragment(Fragment fragment) {
        if (fragment != null) {
            activeFragment = fragment;
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();

            // Update selected menu item
            if (fragment instanceof HomeFragment) {
                bottomNavigationView.setSelectedItemId(R.id.nav_home);
            } else if (fragment instanceof DeliveryFragment) {
                bottomNavigationView.setSelectedItemId(R.id.nav_delivery);
            } else if (fragment instanceof LocationManagementFragment) {
                bottomNavigationView.setSelectedItemId(R.id.nav_locations);
            }
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        // First pass to parent
        super.onActivityResult(requestCode, resultCode, data);

        // Check if the result is for QR code scanning
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            // Find the DeliveryFragment if it's active and pass the result
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (currentFragment instanceof DeliveryFragment) {
                if (result.getContents() == null) {
                    Toast.makeText(this, "Scan cancelled", Toast.LENGTH_LONG).show();
                } else {
                    // Pass the QR content to the fragment properly
                    try {
                        // Try to use reflection as a fallback (avoid if possible)
                        java.lang.reflect.Method method = currentFragment.getClass()
                                .getMethod("handleQRResult", String.class);
                        method.invoke(currentFragment, result.getContents());
                    } catch (Exception e) {
                        Log.e(TAG, "Error passing QR result to fragment: " + e.getMessage());
                        // Fallback - create a new instance with bundle
                        DeliveryFragment newFragment = new DeliveryFragment();
                        Bundle args = new Bundle();
                        args.putString("qr_result", result.getContents());
                        newFragment.setArguments(args);

                        // Replace the current fragment
                        getSupportFragmentManager().beginTransaction()
                                .replace(R.id.fragment_container, newFragment)
                                .commit();
                    }
                }
            }
        }
    }
}