package com.example.autonomous_delivery_robot.activities;

import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.*;
import com.example.autonomous_delivery_robot.R;
import com.example.autonomous_delivery_robot.firebase.LocationService;
import com.example.autonomous_delivery_robot.firebase.PathService;
import com.example.autonomous_delivery_robot.mqtt.MqttHelper;
import com.example.autonomous_delivery_robot.models.Location;
import com.example.autonomous_delivery_robot.models.Order;
import com.example.autonomous_delivery_robot.models.Path;
import com.example.autonomous_delivery_robot.models.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import org.eclipse.paho.client.mqttv3.*;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// Add ZXing imports
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import android.content.Intent;
import com.example.autonomous_delivery_robot.firebase.LogService;


public class DeliveryActivity extends BaseActivity {

    private static final String TAG = "DeliveryActivity";
    private Spinner spinnerLocation, spinnerPath;
    private EditText inputItem;
    private TextView txtObstacle;
    private Button btnStart, btnToggleCamera;
    private ProgressBar progressLoading;
    private WebView cameraView;

    // OTP related UI elements
    private LinearLayout layoutOtp;
    private EditText[] otpDigits = new EditText[5];
    private Button btnPasteOtp, btnVerifyOtp;
    private TextView txtOtpStatus;
    private String receivedOtp = null;

    private MqttHelper mqttHelper;
    private static final String SENSOR_TOPIC = "/khanfar/sensors/ultrasonic";
    private static final String STREAM_URL = "http://192.168.18.88:8000/";
    private static final String CONTROL_TOPIC = "/khanfar/control";
    private static final String DELIVERY_TOPIC = "/khanfar/delivery";
    private static final String STATUS_TOPIC = "/khanfar/status";
    private static final String OTP_TOPIC = "/khanfar/otp";

    private boolean obstacleDetected = false;
    private boolean isCameraVisible = false;
    private boolean isDeliveryInProgress = false;
    private boolean isOtpVerified = false;
    private String currentOrderId = null; // Store the current order ID
    private String currentDeliveryItem = ""; // Store the current delivery item

    // SharedPreferences for persistence
    private SharedPreferences sharedPrefs;
    private static final String PREFS_NAME = "DeliveryPrefs";
    private static final String KEY_IS_DELIVERY_IN_PROGRESS = "isDeliveryInProgress";
    private static final String KEY_IS_OTP_VERIFIED = "isOtpVerified";
    private static final String KEY_CURRENT_ORDER_ID = "currentOrderId";
    private static final String KEY_RECEIVED_OTP = "receivedOtp";
    private static final String KEY_DELIVERY_ITEM = "deliveryItem";
    private static final String KEY_SELECTED_LOCATION = "selectedLocation";
    private static final String KEY_SELECTED_PATH = "selectedPath";

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference dbRef;
    private LocationService locationService;
    private PathService pathService;
    private LogService logService;

    // Data
    private List<Location> locations = new ArrayList<>();
    private List<Path> paths = new ArrayList<>();
    private Location selectedLocation;
    private Path selectedPath;
    private User currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delivery);

        // Initialize SharedPreferences
        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        FirebaseDatabase database = FirebaseDatabase.getInstance("https://autonomous-delivery-robo-53d40-default-rtdb.europe-west1.firebasedatabase.app/");
        dbRef = database.getReference();

        // Initialize services
        locationService = new LocationService();
        pathService = new PathService();
        logService = new LogService();

        // Initialize LogService with the broker URL, but not the mqttHelper instance
        logService.initialize("tcp://broker.hivemq.com:1883");

        // UI elements
        spinnerLocation = findViewById(R.id.spinnerFromLocation);
        spinnerPath = findViewById(R.id.spinnerDestination);
        inputItem = findViewById(R.id.inputItem);
        txtObstacle = findViewById(R.id.txtObstacle);
        btnStart = findViewById(R.id.btnStartDelivery);
        btnToggleCamera = findViewById(R.id.btnToggleCamera);
        cameraView = findViewById(R.id.cameraView);
        progressLoading = findViewById(R.id.progressLoading);

        // Initialize OTP UI elements
        layoutOtp = findViewById(R.id.layoutOtp);
        otpDigits[0] = findViewById(R.id.otpDigit1);
        otpDigits[1] = findViewById(R.id.otpDigit2);
        otpDigits[2] = findViewById(R.id.otpDigit3);
        otpDigits[3] = findViewById(R.id.otpDigit4);
        otpDigits[4] = findViewById(R.id.otpDigit5);
        btnPasteOtp = findViewById(R.id.btnPasteOtp);
        btnVerifyOtp = findViewById(R.id.btnVerifyOtp);
        txtOtpStatus = findViewById(R.id.txtOtpStatus);

        // Setup bottom navigation
        setupBottomNavigation();

        // Set up OTP input fields
        setupOtpInputFields();

        // Check if user is logged in
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Get current user
        getCurrentUser(firebaseUser.getEmail());

        setupCameraView();  // Configure WebView, but don't load yet
        setupSpinnerListeners();

        mqttHelper = new MqttHelper("tcp://broker.hivemq.com:1883", "delivery_client");
        mqttHelper.connect(new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                subscribeToSensorData();
                subscribeToOtpData();
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                runOnUiThread(() ->
                        Toast.makeText(DeliveryActivity.this, "MQTT connection failed", Toast.LENGTH_SHORT).show()
                );
            }
        });

        btnStart.setOnClickListener(v -> {
            if (isDeliveryInProgress) {
                if (isOtpVerified) {
                    // If OTP is verified, scan QR code
                    scanQRCode();
                } else {
                    // This should not happen normally, but just in case
                    Toast.makeText(this, "Please verify OTP first", Toast.LENGTH_SHORT).show();
                }
            } else {
                // Start a new delivery
                startDelivery();
            }
        });

        btnPasteOtp.setOnClickListener(v -> pasteOtpFromClipboard());
        btnVerifyOtp.setOnClickListener(v -> verifyOtp());

        btnToggleCamera.setOnClickListener(v -> toggleCamera());
    }

    @Override
    protected int getBottomNavSelectedItemId() {
        return R.id.nav_delivery;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Load saved state if we're coming back to the activity
        loadState();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Save state when leaving the activity
        saveState();
    }

    private void saveState() {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putBoolean(KEY_IS_DELIVERY_IN_PROGRESS, isDeliveryInProgress);
        editor.putBoolean(KEY_IS_OTP_VERIFIED, isOtpVerified);
        editor.putString(KEY_CURRENT_ORDER_ID, currentOrderId != null ? currentOrderId : "");
        editor.putString(KEY_RECEIVED_OTP, receivedOtp != null ? receivedOtp : "");
        editor.putString(KEY_DELIVERY_ITEM, currentDeliveryItem);

        // Save selected location and path names if they exist
        if (selectedLocation != null) {
            editor.putString(KEY_SELECTED_LOCATION, selectedLocation.getName());
        }

        if (selectedPath != null) {
            editor.putString(KEY_SELECTED_PATH, selectedPath.getPathName());
        }

        editor.apply();
    }

    private void loadState() {
        // Only restore state if we're not already in a delivery
        if (!isDeliveryInProgress) {
            isDeliveryInProgress = sharedPrefs.getBoolean(KEY_IS_DELIVERY_IN_PROGRESS, false);
            isOtpVerified = sharedPrefs.getBoolean(KEY_IS_OTP_VERIFIED, false);

            String savedOrderId = sharedPrefs.getString(KEY_CURRENT_ORDER_ID, "");
            currentOrderId = savedOrderId.isEmpty() ? null : savedOrderId;

            String savedOtp = sharedPrefs.getString(KEY_RECEIVED_OTP, "");
            receivedOtp = savedOtp.isEmpty() ? null : savedOtp;

            currentDeliveryItem = sharedPrefs.getString(KEY_DELIVERY_ITEM, "");

            // If we have an ongoing delivery, restore UI state
            if (isDeliveryInProgress) {
                // Check if order is still active in Firebase
                verifyOrderStatus();
            }
        }
    }

    private void verifyOrderStatus() {
        // Only proceed if we have a current order ID
        if (currentOrderId == null || currentOrderId.isEmpty()) {
            resetDeliveryState();
            return;
        }

        // Show loading while we verify
        showLoading(true);

        // Query Firebase for this order
        dbRef.child("orders").child(currentOrderId).get()
                .addOnSuccessListener(dataSnapshot -> {
                    if (!dataSnapshot.exists()) {
                        // Order doesn't exist anymore
                        Log.d(TAG, "Order not found, resetting state");
                        resetDeliveryState();
                        showLoading(false);
                        return;
                    }

                    Order order = dataSnapshot.getValue(Order.class);
                    if (order == null) {
                        resetDeliveryState();
                        showLoading(false);
                        return;
                    }

                    // Check order status
                    if ("In Progress".equals(order.getStatus())) {
                        Log.d(TAG, "Found active order: " + currentOrderId);

                        // Restore UI to appropriate state
                        setupUIForActiveDelivery(order);

                        // If we were recording logs, restart logging
                        if (logService != null && !logService.isRecording()) {
                            logService.startRecording(currentOrderId);
                        }

                        // Resubscribe to robot status
                        subscribeToRobotStatus();
                    } else {
                        // Order exists but is not in progress (completed or cancelled)
                        Log.d(TAG, "Order exists but is not in progress: " + order.getStatus());
                        resetDeliveryState();
                    }
                    showLoading(false);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking order status: " + e.getMessage());
                    resetDeliveryState();
                    showLoading(false);
                });
    }

    private void setupUIForActiveDelivery(Order order) {
        // Disable input fields
        disableInputFields(true);

        // Store reference to the current order's data
        if (order.getLocation() != null) {
            selectedLocation = order.getLocation();
        }

        if (order.getPath() != null) {
            selectedPath = order.getPath();
        }

        currentDeliveryItem = order.getItem();

        // Update UI based on OTP verification state
        if (isOtpVerified) {
            // OTP is verified, show Scan QR button
            btnStart.setText("Scan QR Code");
            btnStart.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_light));
            btnStart.setVisibility(View.VISIBLE);
            layoutOtp.setVisibility(View.GONE);
        } else {
            // OTP not verified, show OTP input
            btnStart.setVisibility(View.GONE);
            layoutOtp.setVisibility(View.VISIBLE);
            txtOtpStatus.setVisibility(View.GONE);
        }

        // Set input fields with the current order's data
        inputItem.setText(currentDeliveryItem);

        // Find and select the correct location and path in spinners
        if (selectedLocation != null) {
            for (int i = 0; i < locations.size(); i++) {
                if (locations.get(i).getName().equals(selectedLocation.getName())) {
                    spinnerLocation.setSelection(i);
                    break;
                }
            }
        }

        if (selectedPath != null) {
            for (int i = 0; i < paths.size(); i++) {
                if (paths.get(i).getPathName().equals(selectedPath.getPathName())) {
                    spinnerPath.setSelection(i);
                    break;
                }
            }
        }
    }

    private void setupOtpInputFields() {
        // Set up automatic focus changes for OTP fields
        for (int i = 0; i < otpDigits.length; i++) {
            final int currentIndex = i;
            otpDigits[i].addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    // Not needed
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    // Not needed
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (s.length() == 1 && currentIndex < otpDigits.length - 1) {
                        // Move focus to next digit
                        otpDigits[currentIndex + 1].requestFocus();
                    }
                }
            });
        }
    }

    private void subscribeToOtpData() {
        try {
            mqttHelper.subscribe(OTP_TOPIC, (topic, message) -> {
                String payload = new String(message.getPayload());
                Log.d(TAG, "OTP received: " + payload);

                // Store the received OTP
                receivedOtp = payload.trim();

                // Log this event
                if (logService != null && logService.isRecording()) {
                    logService.recordCustomLog("OTP received from robot");
                }
            });
        } catch (MqttException e) {
            Log.e(TAG, "Error subscribing to OTP topic", e);
        }
    }

    private void pasteOtpFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard.hasPrimaryClip()) {
            String pasteData = clipboard.getPrimaryClip().getItemAt(0).getText().toString();

            // Check if the pasted text is a 5-digit number
            if (pasteData.length() == 5 && pasteData.matches("\\d+")) {
                // Fill the OTP fields
                for (int i = 0; i < 5; i++) {
                    otpDigits[i].setText(String.valueOf(pasteData.charAt(i)));
                }

                // Move focus to the last digit
                otpDigits[4].requestFocus();
            } else {
                Toast.makeText(this, "Clipboard does not contain a valid 5-digit OTP", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
        }
    }

    private String getEnteredOtp() {
        StringBuilder otp = new StringBuilder();
        for (EditText digit : otpDigits) {
            otp.append(digit.getText().toString());
        }
        return otp.toString();
    }

    private boolean isOtpComplete() {
        for (EditText digit : otpDigits) {
            if (digit.getText().toString().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void verifyOtp() {
        // First check if OTP is complete
        if (!isOtpComplete()) {
            txtOtpStatus.setText("Please enter all digits");
            txtOtpStatus.setVisibility(View.VISIBLE);
            return;
        }

        // Check if we have received an OTP from MQTT
        if (receivedOtp == null) {
            txtOtpStatus.setText("No OTP received yet, please try again later");
            txtOtpStatus.setVisibility(View.VISIBLE);
            return;
        }

        // Compare entered OTP with received OTP
        String enteredOtp = getEnteredOtp();
        if (enteredOtp.equals(receivedOtp)) {
            // OTP verified successfully
            txtOtpStatus.setText("OTP verified successfully");
            txtOtpStatus.setTextColor(getResources().getColor(android.R.color.holo_green_light));
            txtOtpStatus.setVisibility(View.VISIBLE);

            // Log the successful verification
            if (logService != null && logService.isRecording()) {
                logService.recordCustomLog("OTP verified by user");
            }

            // Hide OTP UI
            layoutOtp.setVisibility(View.GONE);

            // Mark OTP as verified
            isOtpVerified = true;

            // Change button to "Scan QR Code"
            btnStart.setText("Scan QR Code");
            btnStart.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_light));
            btnStart.setVisibility(View.VISIBLE);

            Toast.makeText(this, "OTP verified! Ready to scan QR code when delivery arrives", Toast.LENGTH_LONG).show();
        } else {
            // Wrong OTP
            txtOtpStatus.setText("Incorrect OTP, please try again");
            txtOtpStatus.setTextColor(getResources().getColor(android.R.color.holo_red_light));
            txtOtpStatus.setVisibility(View.VISIBLE);

            // Clear OTP fields for retry
            for (EditText digit : otpDigits) {
                digit.setText("");
            }

            // Set focus to first digit
            otpDigits[0].requestFocus();
        }
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
                                loadUserLocations();
                            } else {
                                Toast.makeText(DeliveryActivity.this, "User data is invalid", Toast.LENGTH_SHORT).show();
                                showLoading(false);
                            }
                        } else {
                            Toast.makeText(DeliveryActivity.this, "User not found", Toast.LENGTH_SHORT).show();
                            showLoading(false);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error getting user: ", e);
                        Toast.makeText(DeliveryActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        showLoading(false);
                    });
        } else {
            Toast.makeText(DeliveryActivity.this, "Not logged in", Toast.LENGTH_SHORT).show();
            showLoading(false);
            finish();
        }
    }

    private void loadUserLocations() {
        locationService.getUserLocations(currentUser.getEmail(), new LocationService.LocationsCallback() {
            @Override
            public void onLocationsLoaded(List<Location> userLocations) {
                locations = userLocations;
                if (locations.isEmpty()) {
                    Toast.makeText(DeliveryActivity.this, "No locations found. Please add a location first.", Toast.LENGTH_LONG).show();
                }
                updateLocationSpinner();

                // Now that locations are loaded, check for active deliveries
                verifyOrderStatus();

                showLoading(false);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error loading locations: " + error);
                Toast.makeText(DeliveryActivity.this, "Error loading locations: " + error, Toast.LENGTH_SHORT).show();
                showLoading(false);
            }
        });
    }

    private void loadPathsForLocation(Location location) {
        showLoading(true);
        if (location == null) {
            paths.clear();
            updatePathSpinner();
            showLoading(false);
            return;
        }

        pathService.getPathsForLocation(location.getName(), new PathService.PathsCallback() {
            @Override
            public void onPathsLoaded(List<Path> locationPaths) {
                paths = locationPaths;
                if (paths.isEmpty()) {
                    Toast.makeText(DeliveryActivity.this, "No paths found for this location", Toast.LENGTH_SHORT).show();
                }
                updatePathSpinner();
                showLoading(false);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error loading paths: " + error);
                Toast.makeText(DeliveryActivity.this, "Error loading paths: " + error, Toast.LENGTH_SHORT).show();
                showLoading(false);
            }
        });
    }

    private void setupSpinnerListeners() {
        spinnerLocation.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < locations.size()) {
                    selectedLocation = locations.get(position);
                    loadPathsForLocation(selectedLocation);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedLocation = null;
                paths.clear();
                updatePathSpinner();
            }
        });

        spinnerPath.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < paths.size()) {
                    selectedPath = paths.get(position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedPath = null;
            }
        });
    }

    private void updateLocationSpinner() {
        List<String> locationNames = new ArrayList<>();
        for (Location location : locations) {
            locationNames.add(location.getName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, R.layout.spinner_item_white, locationNames);
        adapter.setDropDownViewResource(R.layout.spinner_item_white);
        spinnerLocation.setAdapter(adapter);

        // Select first item if available
        if (!locations.isEmpty()) {
            spinnerLocation.setSelection(0);
            selectedLocation = locations.get(0);
            loadPathsForLocation(selectedLocation);
        } else {
            selectedLocation = null;
            paths.clear();
            updatePathSpinner();
        }
    }

    private void updatePathSpinner() {
        List<String> pathNames = new ArrayList<>();
        for (Path path : paths) {
            pathNames.add(path.getPathName());
        }

        if (pathNames.isEmpty()) {
            pathNames.add("No paths available");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, R.layout.spinner_item_white, pathNames);
        adapter.setDropDownViewResource(R.layout.spinner_item_white);
        spinnerPath.setAdapter(adapter);

        // Select first item if available
        if (!paths.isEmpty()) {
            spinnerPath.setSelection(0);
            selectedPath = paths.get(0);
        } else {
            selectedPath = null;
        }
    }

    private void setupCameraView() {
        WebSettings settings = cameraView.getSettings();
        cameraView.setWebViewClient(new WebViewClient());
        settings.setJavaScriptEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        cameraView.setHorizontalScrollBarEnabled(false);
        cameraView.setVerticalScrollBarEnabled(false);
        cameraView.setVisibility(View.GONE); // hide by default
    }

    private void subscribeToSensorData() {
        try {
            mqttHelper.subscribe(SENSOR_TOPIC, (topic, message) -> {
                String payload = new String(message.getPayload());
                runOnUiThread(() -> {
                    if (payload.contains("sensor") && payload.contains("cm")) {
                        txtObstacle.setText("Obstacle: " + payload);
                        txtObstacle.setTextColor(getColor(android.R.color.holo_red_light));
                        obstacleDetected = true;
                        showManualControlDialog();
                        sendStopSignal();
                    } else {
                        txtObstacle.setText("No obstacles detected");
                        txtObstacle.setTextColor(getColor(android.R.color.holo_green_light));
                        obstacleDetected = false;
                    }
                });
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void showManualControlDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_manual_control, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        Button btnManualControl = dialogView.findViewById(R.id.btnManualControl);
        btnManualControl.setOnClickListener(v -> {
            // Stop logging when user takes manual control
            if (logService != null && logService.isRecording()) {
                logService.stopRecording();
            }
            dialog.dismiss();
            Intent intent = new Intent(this, ControlActivity.class); // Or your manual control screen
            startActivity(intent);
        });

        dialog.show();
    }


    private void sendStopSignal() {
        try {
            mqttHelper.publish(CONTROL_TOPIC, "stop");
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void startDelivery() {
        if (selectedLocation == null) {
            Toast.makeText(this, "Please select a location", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedPath == null || paths.isEmpty()) {
            Toast.makeText(this, "Please select a valid path", Toast.LENGTH_SHORT).show();
            return;
        }

        String item = inputItem.getText().toString().trim();

        if (item.isEmpty()) {
            Toast.makeText(this, "Please enter the item to deliver", Toast.LENGTH_SHORT).show();
            return;
        }

        if (obstacleDetected) {
            Toast.makeText(this, "Cannot start: Obstacle detected!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save the delivery item
        currentDeliveryItem = item;

        // Create a new order and store its ID
        currentOrderId = saveOrderToFirebase(selectedLocation, selectedPath, item);

        // Start logging for this order
        logService.startRecording(currentOrderId);

        // Get the instruction set from the selected path
        String instructions = selectedPath.getInstructionSet();

        // Check if instruction set is available
        if (instructions == null || instructions.isEmpty()) {
            Toast.makeText(this, "Error: Path has no instruction set", Toast.LENGTH_SHORT).show();
            return;
        }

        // Append the current user email to the instructions
        String instructionsWithEmail = currentUser.getEmail() + ";" + instructions;

        // Publish instruction set to MQTT delivery topic
        try {
            mqttHelper.publish(DELIVERY_TOPIC, instructionsWithEmail);
            Log.d(TAG, "Published instruction set with email to " + DELIVERY_TOPIC + ": " + instructionsWithEmail);

            // Also send the traditional command for backward compatibility
            String command = "deliver:" + selectedLocation.getName() + ":" + selectedPath.getPathName() + ":" + item;
            mqttHelper.publish(CONTROL_TOPIC, command);

            // Subscribe to robot status updates
            subscribeToRobotStatus();

            Toast.makeText(this, "Delivery started!", Toast.LENGTH_SHORT).show();

            // Hide the start button and show OTP input
            btnStart.setVisibility(View.GONE);
            layoutOtp.setVisibility(View.VISIBLE);

            // Set the delivery in progress flag
            isDeliveryInProgress = true;
            isOtpVerified = false;

            // Disable input fields during delivery
            disableInputFields(true);

        } catch (MqttException e) {
            Log.e(TAG, "Failed to send delivery command", e);
            Toast.makeText(this, "Failed to send delivery command: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Disable input fields during delivery
    private void disableInputFields(boolean disable) {
        spinnerLocation.setEnabled(!disable);
        spinnerPath.setEnabled(!disable);
        inputItem.setEnabled(!disable);
    }

    // Modified to return the order ID
    private String saveOrderToFirebase(Location location, Path path, String item) {
        // Get current date and time for startDate
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String currentDateTime = sdf.format(new Date());

        // Create a new order with the updated structure
        Order newOrder = new Order(
                location,              // location
                path,                  // path
                currentUser,           // user
                "In Progress",         // status
                item,                  // item
                currentDateTime,       // startDate
                ""                     // endDate (empty as delivery is not completed yet)
        );

        // Log Firebase operation
        Log.d(TAG, "Attempting to save order to Firebase");

        // Generate a unique ID in Firebase
        String orderId = dbRef.child("orders").push().getKey();

        // Save the order to Firebase
        if (orderId != null) {
            dbRef.child("orders").child(orderId).setValue(newOrder)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(DeliveryActivity.this, "Order created successfully!", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Order successfully saved with ID: " + orderId);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(DeliveryActivity.this, "Failed to create order: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Error saving order: " + e.getMessage(), e);
                    });
        }

        return orderId;
    }

    // New method to initiate QR code scanning
    private void scanQRCode() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.setPrompt("Scan delivery confirmation QR code");
        integrator.setCameraId(0);  // Use default camera
        integrator.setBeepEnabled(true);
        integrator.setBarcodeImageEnabled(true);
        integrator.setCaptureActivity(CaptureActivityPortrait.class);  // Use our portrait activity
        integrator.initiateScan();
    }

    // Handle QR code scanning result
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if(result != null) {
            if(result.getContents() == null) {
                Toast.makeText(this, "Scan cancelled", Toast.LENGTH_LONG).show();
            } else {
                String qrContent = result.getContents();
                processQRCode(qrContent);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    // Process the QR code content
    private void processQRCode(String qrContent) {
        if (qrContent.equalsIgnoreCase("delivered")) {

            // Add QR code scan log
            if (logService != null && logService.isRecording()) {
                logService.recordCustomLog("QR code scanned: " + qrContent);
            }

            // Update order status in Firebase
            if (currentOrderId != null) {
                updateOrderStatus(currentOrderId);
            } else {
                Toast.makeText(this, "Error: No active order found", Toast.LENGTH_SHORT).show();
            }

            // Publish "delivered" message to MQTT
            try {
                mqttHelper.publish(DELIVERY_TOPIC, "delivered");
                Log.d(TAG, "Published 'delivered' to " + DELIVERY_TOPIC);

                // Subscribe to status topic to get notification when robot returns
                subscribeToRobotStatus();

            } catch (MqttException e) {
                Log.e(TAG, "Failed to send delivered message", e);
            }

            // Change button back to Start Delivery but locked
            btnStart.setText("Start Delivery");
            btnStart.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));

            // Button is still in delivery state but visually shows Start Delivery
            isDeliveryInProgress = true;

            // Show toast message
            Toast.makeText(this, "Delivery completed! Waiting for robot to return.", Toast.LENGTH_LONG).show();

            // When button is clicked while waiting, show message
            btnStart.setOnClickListener(v -> {
                Toast.makeText(this, "Waiting for the robot to get back to the basestation", Toast.LENGTH_SHORT).show();
            });
        } else {
            // Not the expected QR code
            Toast.makeText(this, "Invalid QR code: " + qrContent, Toast.LENGTH_LONG).show();
        }
    }

    private void subscribeToRobotStatus() {
        try {
            mqttHelper.subscribe(STATUS_TOPIC, (topic, message) -> {
                String payload = new String(message.getPayload());
                Log.d(TAG, "Received robot status: " + payload);

                if (payload.equalsIgnoreCase("arrived")) {
                    runOnUiThread(() -> {
                        Toast.makeText(DeliveryActivity.this, "Robot has returned to basestation!", Toast.LENGTH_SHORT).show();
                        // Stop logging before resetting state
                        if (logService != null && logService.isRecording()) {
                            logService.stopRecording();
                        }
                        resetDeliveryState();
                    });
                }
            });
            Log.d(TAG, "Subscribed to robot status topic: " + STATUS_TOPIC);
        } catch (MqttException e) {
            Log.e(TAG, "Error subscribing to robot status", e);
        }
    }

    // Reset the delivery state
    private void resetDeliveryState() {
        isDeliveryInProgress = false;
        isOtpVerified = false;
        currentOrderId = null;
        receivedOtp = null;
        currentDeliveryItem = "";

        // Clear OTP fields
        for (EditText digit : otpDigits) {
            digit.setText("");
        }

        // Hide OTP UI and status
        layoutOtp.setVisibility(View.GONE);
        txtOtpStatus.setVisibility(View.GONE);

        // Reset Start button
        btnStart.setText("Start Delivery");
        btnStart.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_dark));
        btnStart.setVisibility(View.VISIBLE);
        btnStart.setOnClickListener(v -> {
            if (isDeliveryInProgress) {
                scanQRCode();
            } else {
                startDelivery();
            }
        });

        // Re-enable input fields
        disableInputFields(false);

        // Clear SharedPreferences state
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.remove(KEY_IS_DELIVERY_IN_PROGRESS);
        editor.remove(KEY_IS_OTP_VERIFIED);
        editor.remove(KEY_CURRENT_ORDER_ID);
        editor.remove(KEY_RECEIVED_OTP);
        editor.remove(KEY_DELIVERY_ITEM);
        editor.remove(KEY_SELECTED_LOCATION);
        editor.remove(KEY_SELECTED_PATH);
        editor.apply();
    }

    // Update order status and end date in Firebase
    private void updateOrderStatus(String orderId) {
        // Get current date and time for endDate
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String currentDateTime = sdf.format(new Date());

        // Update order status and end date
        DatabaseReference orderRef = dbRef.child("orders").child(orderId);
        orderRef.child("status").setValue("Delivered");
        orderRef.child("endDate").setValue(currentDateTime)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Order status updated successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating order status: " + e.getMessage(), e);
                    Toast.makeText(DeliveryActivity.this, "Error updating order: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void toggleCamera() {
        if (isCameraVisible) {
            cameraView.setVisibility(View.GONE);
            btnToggleCamera.setText("Show Live Feed");
            isCameraVisible = false;
        } else {
            cameraView.setVisibility(View.VISIBLE);
            btnToggleCamera.setText("Hide Live Feed");
            isCameraVisible = true;

            // Load the stream once
            if (cameraView.getUrl() == null || cameraView.getUrl().isEmpty()) {
                cameraView.loadUrl(STREAM_URL);
            }
        }
    }

    private void showLoading(boolean isLoading) {
        runOnUiThread(() -> {
            if (progressLoading != null) {
                progressLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            }

            if (spinnerLocation != null) {
                spinnerLocation.setEnabled(!isLoading);
            }

            if (spinnerPath != null) {
                spinnerPath.setEnabled(!isLoading);
            }

            if (btnStart != null) {
                btnStart.setEnabled(!isLoading);
            }
        });
    }

    @Override
    protected void onDestroy() {
        // Cleanup LogService
        if (logService != null) {
            logService.cleanup();
        }
        super.onDestroy();
        // Disconnect MQTT client to prevent memory leaks
        if (mqttHelper != null) {
            try {
                mqttHelper.disconnect();
            } catch (MqttException e) {
                Log.e(TAG, "Error disconnecting MQTT", e);
                // Don't throw RuntimeException as it will crash the app
            }
        }
    }
}