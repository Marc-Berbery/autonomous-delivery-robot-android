package com.example.autonomous_delivery_robot.activities;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.autonomous_delivery_robot.R;
import com.example.autonomous_delivery_robot.firebase.LocationService;
import com.example.autonomous_delivery_robot.firebase.PathService;
import com.example.autonomous_delivery_robot.mqtt.MqttHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.ArrayList;
import java.util.Locale;

public class ControlActivity extends BaseActivity {

    private static final String TAG = "ControlActivity";
    private static final String TOPIC_CONTROL = "/khanfar/control";
    private static final String TOPIC_JSON = "/khanfar/json";
    private static final String TOPIC_TITLE = "/khanfar/title";
    private static final String TOPIC_STATUS = "/khanfar/status";
    private static final String STREAM_URL = "http://192.168.18.88:8000/";
    private static final int PERMISSION_REQUEST_RECORD_AUDIO = 1001;

    private MqttHelper mqttHelper;

    private TextView txtLastCommand;
    private MaterialButton btnForward, btnBack, btnLeft, btnRight, btnTurbo;
    private MaterialButton btnStartRecording, btnEndRecording, btnVoiceControl, btnTiltControl;
    private MaterialCardView cameraCardView;
    private WebView cameraView;

    private boolean isTurboOn = false;
    private boolean isRecordingMode = false;
    private String locationName = "";
    private String recordedPathJson = "";
    private boolean isRecording = false;
    private boolean commandSentDuringRecording = false;
    private boolean isCalibrating = false;
    private MaterialAlertDialogBuilder calibrationDialogBuilder;
    private androidx.appcompat.app.AlertDialog calibrationDialog;
    private androidx.appcompat.app.AlertDialog voiceDialog;

    private SpeechRecognizer speechRecognizer;
    private Animation micAnimation;
    private boolean isListening = false;
    private Vibrator vibrator;

    // For permission request
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    showVoiceControlDialog();
                } else {
                    Toast.makeText(this, "Microphone permission is required for voice control",
                            Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);

        isRecordingMode = getIntent().getBooleanExtra("recording_mode", false);
        if (isRecordingMode) {
            locationName = getIntent().getStringExtra(PathManagementActivity.EXTRA_LOCATION_NAME);
            if (TextUtils.isEmpty(locationName)) {
                Toast.makeText(this, "Location name is required", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }

        initializeViews();
        initializeSpeechRecognizer();
        loadMicAnimation();
        setupBasicUIState();

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        new Handler(Looper.getMainLooper()).postDelayed(this::setupCameraStream, 200);
        new Handler(Looper.getMainLooper()).postDelayed(this::initMQTT, 400);

        // Initialize calibration dialog builder
        createCalibrationDialogBuilder();
    }

    @Override
    protected int getBottomNavSelectedItemId() {
        return R.id.nav_delivery;
    }

    private void initializeViews() {
        txtLastCommand = findViewById(R.id.txtLastCommand);
        btnForward = findViewById(R.id.btnForward);
        btnBack = findViewById(R.id.btnBack);
        btnLeft = findViewById(R.id.btnLeft);
        btnRight = findViewById(R.id.btnRight);
        btnTurbo = findViewById(R.id.btnTurbo);
        btnStartRecording = findViewById(R.id.btnStartRecording);
        btnEndRecording = findViewById(R.id.btnEndRecording);
        btnVoiceControl = findViewById(R.id.btnVoiceControl);
        btnTiltControl = findViewById(R.id.btnTiltControl);
        cameraCardView = findViewById(R.id.cameraCardView);
        cameraView = findViewById(R.id.cameraView);

        setControlsEnabled(false);

        btnVoiceControl.setOnClickListener(v -> {
            animateButtonPress(btnVoiceControl);
            if (checkAudioPermission()) {
                showVoiceControlDialog();
            } else {
                requestAudioPermission();
            }
        });

        btnTiltControl.setOnClickListener(v -> {
            animateButtonPress(btnTiltControl);
            startActivity(new Intent(ControlActivity.this, TiltControlActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        btnTurbo.setOnClickListener(v -> {
            animateButtonPress(btnTurbo);
            toggleTurbo();
        });

        btnStartRecording.setOnClickListener(v -> {
            animateButtonPress(btnStartRecording);
            startRecording();
        });

        btnEndRecording.setOnClickListener(v -> {
            animateButtonPress(btnEndRecording);
            endRecording();
        });
    }

    private void loadMicAnimation() {
        micAnimation = AnimationUtils.loadAnimation(this, R.anim.mic_pulse);
    }

    private void setupBasicUIState() {
        if (isRecordingMode) {
            btnStartRecording.setVisibility(View.VISIBLE);
            btnStartRecording.setEnabled(false);
            btnEndRecording.setVisibility(View.GONE);
            btnEndRecording.setEnabled(false);
            btnTurbo.setVisibility(View.GONE);
        } else {
            btnStartRecording.setVisibility(View.GONE);
            btnEndRecording.setVisibility(View.GONE);
        }
    }

    private void initMQTT() {
        mqttHelper = new MqttHelper("tcp://broker.hivemq.com:1883", "client_control_" + System.currentTimeMillis());

        mqttHelper.connect(new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                runOnUiThread(() -> {
                    Toast.makeText(ControlActivity.this, "MQTT Connected", Toast.LENGTH_SHORT).show();
                    setControlsEnabled(true);
                    setupButtonListeners();

                    if (isRecordingMode) {
                        subscribeToJsonTopic();
                        subscribeToStatusTopic();
                        toggleTurboOn();
                    }
                });
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                runOnUiThread(() -> {
                    Toast.makeText(ControlActivity.this, "MQTT Connection Failed", Toast.LENGTH_SHORT).show();
                    setControlsEnabled(false);
                });
            }
        });
    }

    private void initializeSpeechRecognizer() {
        // Check if speech recognition is available
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition not available on this device", Toast.LENGTH_SHORT).show();
            btnVoiceControl.setEnabled(false);
            return;
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                isListening = true;
                updateVoiceDialogUI("Listening...", "");
            }

            @Override
            public void onBeginningOfSpeech() {
                updateVoiceDialogUI("Listening...", "");
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                // Update microphone visualization if needed
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
                // Not needed for this implementation
            }

            @Override
            public void onEndOfSpeech() {
                isListening = false;
                updateVoiceDialogUI("Processing...", "");
            }

            @Override
            public void onError(int error) {
                isListening = false;
                String errorMessage;
                switch (error) {
                    case SpeechRecognizer.ERROR_AUDIO:
                        errorMessage = "Audio recording error";
                        break;
                    case SpeechRecognizer.ERROR_NETWORK:
                    case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                        errorMessage = "Network error";
                        break;
                    case SpeechRecognizer.ERROR_NO_MATCH:
                        errorMessage = "No speech detected";
                        break;
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        errorMessage = "Speech timeout";
                        break;
                    default:
                        errorMessage = "Unknown error";
                }

                updateVoiceDialogUI("Error: " + errorMessage, "");

                // Restart listening after a brief delay
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (voiceDialog != null && voiceDialog.isShowing()) {
                        startVoiceRecognition();
                    }
                }, 1500);
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String text = matches.get(0).toLowerCase();
                    Log.d(TAG, "Voice recognized: " + text);
                    processVoiceCommand(text);

                    // Restart listening after processing
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (voiceDialog != null && voiceDialog.isShowing()) {
                            startVoiceRecognition();
                        }
                    }, 1500);
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String text = matches.get(0).toLowerCase();
                    updateVoiceDialogUI("Listening...", text);
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
                // Not needed for this implementation
            }
        });
    }

    private void showVoiceControlDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_voice_recording, null);
        builder.setView(dialogView);
        builder.setCancelable(false);

        // Get dialog views
        ImageView microphoneIcon = dialogView.findViewById(R.id.microphoneIcon);
        TextView txtListeningStatus = dialogView.findViewById(R.id.txtListeningStatus);
        TextView txtRecognizedText = dialogView.findViewById(R.id.txtRecognizedText);
        MaterialButton btnCancelVoice = dialogView.findViewById(R.id.btnCancelVoice);

        // Set up cancel button
        btnCancelVoice.setOnClickListener(v -> {
            animateButtonPress(btnCancelVoice);
            if (speechRecognizer != null) {
                speechRecognizer.cancel();
            }
            voiceDialog.dismiss();
        });

        // Create and show dialog
        voiceDialog = builder.create();
        voiceDialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_rounded_card);

        // Start microphone animation
        microphoneIcon.startAnimation(micAnimation);

        voiceDialog.show();

        // Start voice recognition
        startVoiceRecognition();
    }

    private void updateVoiceDialogUI(String status, String recognizedText) {
        if (voiceDialog != null && voiceDialog.isShowing()) {
            TextView txtListeningStatus = voiceDialog.findViewById(R.id.txtListeningStatus);
            TextView txtRecognizedText = voiceDialog.findViewById(R.id.txtRecognizedText);
            ImageView microphoneIcon = voiceDialog.findViewById(R.id.microphoneIcon);

            if (txtListeningStatus != null) {
                txtListeningStatus.setText(status);
            }

            if (txtRecognizedText != null) {
                txtRecognizedText.setText(recognizedText);
            }

            // Control animation based on listening state
            if (microphoneIcon != null) {
                if (isListening) {
                    microphoneIcon.startAnimation(micAnimation);
                } else {
                    microphoneIcon.clearAnimation();
                }
            }
        }
    }

    private void startVoiceRecognition() {
        if (speechRecognizer != null) {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

            try {
                speechRecognizer.startListening(intent);
            } catch (Exception e) {
                Log.e(TAG, "Error starting speech recognition", e);
                Toast.makeText(this, "Error starting speech recognition", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void processVoiceCommand(String text) {
        String commandToSend = null;

        // Check for valid commands in the text
        if (text.contains("forward")) {
            commandToSend = "forward";
            updateVoiceDialogUI("Command recognized!", "\"Forward\" - Moving forward");
        } else if (text.contains("backward") || text.contains("back")) {
            commandToSend = "backward";
            updateVoiceDialogUI("Command recognized!", "\"Backward\" - Moving backward");
        } else if (text.contains("left")) {
            commandToSend = "left";
            updateVoiceDialogUI("Command recognized!", "\"Left\" - Turning left");
        } else if (text.contains("right")) {
            commandToSend = "right";
            updateVoiceDialogUI("Command recognized!", "\"Right\" - Turning right");
        } else if (text.contains("stop")) {
            commandToSend = "stop";
            updateVoiceDialogUI("Command recognized!", "\"Stop\" - Stopping movement");
        } else {
            updateVoiceDialogUI("Command not recognized", "Try saying: forward, backward, left, right, or stop");
            return;
        }

        // Provide haptic feedback
        provideHapticFeedback();

        // Send the command
        if (commandToSend != null) {
            sendCommand(commandToSend);

            // If it's not already a stop command, schedule a stop command after specified time
            if (!commandToSend.equals("stop")) {
                if (commandToSend.equals("forward") || commandToSend.equals("backward")) {
                    // Schedule a stop command after 4 seconds
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        sendCommand("stop");
                        updateVoiceDialogUI("Auto-stopped", "Movement stopped after 4 seconds");
                    }, 4000);
                } else {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        sendCommand("stop");
                        updateVoiceDialogUI("Auto-stopped", "Movement stopped after 0.8 seconds");
                    }, 800);
                }
            }
        }
    }

    private void provideHapticFeedback() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                // Deprecated in API 26
                vibrator.vibrate(50);
            }
        }
    }

    private boolean checkAudioPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAudioPermission() {
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
    }

    private void subscribeToJsonTopic() {
        try {
            mqttHelper.subscribe(TOPIC_JSON, new IMqttMessageListener() {
                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String payload = new String(message.getPayload());
                    Log.d(TAG, "JSON Message received: " + payload);
                    recordedPathJson = payload;
                }
            });
        } catch (MqttException e) {
            Log.e(TAG, "Error subscribing to JSON topic", e);
            Toast.makeText(ControlActivity.this, "Failed to subscribe to JSON topic", Toast.LENGTH_SHORT).show();
        }
    }

    private void subscribeToStatusTopic() {
        try {
            mqttHelper.subscribe(TOPIC_STATUS, new IMqttMessageListener() {
                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String payload = new String(message.getPayload());
                    Log.d(TAG, "Status Message received: " + payload);

                    // Handle different status messages
                    if (payload.equals("calibration_started")) {
                        runOnUiThread(() -> {
                            isCalibrating = true;
                            showCalibrationDialog();
                            disableControlButtons();
                        });
                    } else if (payload.equals("calibration_complete")) {
                        runOnUiThread(() -> {
                            isCalibrating = false;
                            dismissCalibrationDialog();
                            enableControlButtons();
                            Toast.makeText(ControlActivity.this,
                                    "Calibration complete, you can now control the robot",
                                    Toast.LENGTH_SHORT).show();
                        });
                    } else if (payload.equals("command_ignored_calibrating")) {
                        runOnUiThread(() -> {
                            Toast.makeText(ControlActivity.this,
                                    "Command ignored: Calibration in progress",
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            });
        } catch (MqttException e) {
            Log.e(TAG, "Error subscribing to status topic", e);
        }
    }

    private void startRecording() {
        // Send start command
        try {
            mqttHelper.publish(TOPIC_CONTROL, "start");
            txtLastCommand.setText("Starting calibration and recording...");

            // Show initial UI feedback before receiving status message
            Toast.makeText(this, "Starting gyroscope calibration...", Toast.LENGTH_LONG).show();
            disableControlButtons();

            // Reset the command tracking flag
            commandSentDuringRecording = false;
            isRecording = true;

            // Switch buttons
            btnStartRecording.setVisibility(View.GONE);
            btnEndRecording.setVisibility(View.VISIBLE);
            btnEndRecording.setEnabled(false); // Initially disabled until a command is sent

        } catch (MqttException e) {
            Log.e(TAG, "Error sending start command", e);
            Toast.makeText(this, "Failed to start recording", Toast.LENGTH_SHORT).show();
        }
    }

    private void endRecording() {
        // Check if any commands were sent during recording
        if (!commandSentDuringRecording) {
            Toast.makeText(this, "Cannot end recording: No movement commands recorded", Toast.LENGTH_LONG).show();
            return;
        }

        // Send end command
        try {
            mqttHelper.publish(TOPIC_CONTROL, "end");
            txtLastCommand.setText("Recording ended");
            isRecording = false;

            // Show dialog to enter path name
            showPathNameDialog();

        } catch (MqttException e) {
            Log.e(TAG, "Error sending end command", e);
            Toast.makeText(this, "Failed to end recording", Toast.LENGTH_SHORT).show();
        }
    }

    private void showPathNameDialog() {
        // Create dialog view
        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.dialog_path_name, null);
        com.google.android.material.textfield.TextInputLayout textInputLayout =
                view.findViewById(R.id.textInputPathName);

        // Create and show dialog
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle("Save Path")
                .setView(view)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", (dialog, which) -> {
                    dialog.dismiss();
                    finish(); // Return to the previous activity
                })
                .setCancelable(false);

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();

        // Override the positive button to prevent dismissal if validation fails
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String pathName = textInputLayout.getEditText().getText().toString().trim();

            if (TextUtils.isEmpty(pathName)) {
                textInputLayout.setError("Path name is required");
                return;
            }

            if (TextUtils.isEmpty(recordedPathJson)) {
                Toast.makeText(ControlActivity.this, "No path data received", Toast.LENGTH_SHORT).show();
                return;
            }

            savePath(pathName);
            dialog.dismiss();
        });
    }

    // Modified savePath method to remove the title publication to MQTT
    private void savePath(String pathName) {
        // Verify we have valid JSON data
        if (TextUtils.isEmpty(recordedPathJson)) {
            Toast.makeText(ControlActivity.this, "No path data received", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate JSON format
        try {
            // Try to parse JSON to verify it's valid
            new org.json.JSONObject(recordedPathJson);
        } catch (org.json.JSONException e) {
            Log.e(TAG, "Invalid JSON data: " + e.getMessage());
            Toast.makeText(ControlActivity.this, "Path data is not in valid JSON format", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get location service to find the location by name
        LocationService locationService = new LocationService();
        locationService.getLocationByName(locationName, new LocationService.LocationCallback() {
            @Override
            public void onLocationSaved(com.example.autonomous_delivery_robot.models.Location location) {
                if (location == null) {
                    Toast.makeText(ControlActivity.this, "Location not found", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Now save the path with the location
                PathService pathService = new PathService();
                pathService.savePath(pathName, recordedPathJson, locationName, new PathService.PathCallback() {
                    @Override
                    public void onPathSaved(com.example.autonomous_delivery_robot.models.Path path) {
                        Toast.makeText(ControlActivity.this, "Path saved successfully!", Toast.LENGTH_SHORT).show();
                        finish(); // Return to the previous activity
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Toast.makeText(ControlActivity.this,
                                "Failed to save path: " + errorMessage,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(ControlActivity.this,
                        "Error finding location: " + errorMessage,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupCameraStream() {
        // Set a special WebViewClient to detect loading success or failure
        cameraView.setWebViewClient(new WebViewClient() {
            private boolean pageLoadError = false;

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!pageLoadError) {
                    Log.d(TAG, "Camera stream loaded successfully");
                    runOnUiThread(() -> {
                        // Enable recording button only if camera stream loaded successfully
                        if (isRecordingMode) {
                            btnStartRecording.setEnabled(true);
                            Toast.makeText(ControlActivity.this, "Camera connected, recording ready", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                pageLoadError = true;
                Log.e(TAG, "Error loading camera stream: " + description);
                runOnUiThread(() -> {
                    // Disable recording button if camera stream failed to load
                    if (isRecordingMode) {
                        btnStartRecording.setEnabled(false);
                        Toast.makeText(ControlActivity.this,
                                "Cannot start recording: Camera not connected",
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        });

        WebSettings settings = cameraView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        cameraView.setHorizontalScrollBarEnabled(false);
        cameraView.setVerticalScrollBarEnabled(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // Initially disable the start recording button until camera loads
        if (isRecordingMode) {
            btnStartRecording.setEnabled(false);
        }

        cameraView.loadUrl(STREAM_URL);
    }

    private void setupButtonListeners() {
        btnForward.setOnTouchListener(createCalibrationAwareControlTouchListener("forward", btnForward));
        btnBack.setOnTouchListener(createCalibrationAwareControlTouchListener("backward", btnBack));
        btnLeft.setOnTouchListener(createCalibrationAwareControlTouchListener("left", btnLeft));
        btnRight.setOnTouchListener(createCalibrationAwareControlTouchListener("right", btnRight));
    }

    private View.OnTouchListener createCalibrationAwareControlTouchListener(String direction, MaterialButton button) {
        return (v, event) -> {
            // If calibrating, ignore touch events
            if (isCalibrating) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    Toast.makeText(ControlActivity.this,
                            "Please wait for calibration to complete",
                            Toast.LENGTH_SHORT).show();
                }
                return true;
            }

            // Normal behavior when not calibrating
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    animateButtonPress(button);
                    sendCommand(direction);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    animateButtonRelease(button);
                    sendCommand("stop");
                    return true;
            }
            return false;
        };
    }

    private void animateButtonPress(MaterialButton button) {
        // Add scale down animation
        button.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .start();

        // Provide haptic feedback
        provideHapticFeedback();
    }

    private void animateButtonRelease(MaterialButton button) {
        // Add scale up animation
        button.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(100)
                .start();
    }

    private void createCalibrationDialogBuilder() {
        calibrationDialogBuilder = new MaterialAlertDialogBuilder(this)
                .setTitle("Calibrating Gyroscope")
                .setMessage("Please wait while the robot's gyroscope is being calibrated...")
                .setCancelable(false);
    }

    private void showCalibrationDialog() {
        calibrationDialog = calibrationDialogBuilder.show();
    }

    private void disableControlButtons() {
        btnForward.setEnabled(false);
        btnBack.setEnabled(false);
        btnLeft.setEnabled(false);
        btnRight.setEnabled(false);

        // Visually indicate disabled state
        btnForward.setAlpha(0.5f);
        btnBack.setAlpha(0.5f);
        btnLeft.setAlpha(0.5f);
        btnRight.setAlpha(0.5f);
    }

    private void enableControlButtons() {
        btnForward.setEnabled(true);
        btnBack.setEnabled(true);
        btnLeft.setEnabled(true);
        btnRight.setEnabled(true);

        // Restore visual appearance
        btnForward.setAlpha(1.0f);
        btnBack.setAlpha(1.0f);
        btnLeft.setAlpha(1.0f);
        btnRight.setAlpha(1.0f);
    }

    private void dismissCalibrationDialog() {
        if (calibrationDialog != null && calibrationDialog.isShowing()) {
            calibrationDialog.dismiss();
        }
    }

    private void toggleTurbo() {
        isTurboOn = !isTurboOn;
        String command = isTurboOn ? "turbo on" : "turbo off";
        btnTurbo.setText(isTurboOn ? "Turbo On" : "Turbo Off");

        // Change button background tint based on turbo state
        int colorRes = isTurboOn ? R.color.secondary : R.color.secondary_variant;
        btnTurbo.setBackgroundTintList(ContextCompat.getColorStateList(this, colorRes));

        try {
            mqttHelper.publish(TOPIC_CONTROL, command);
            txtLastCommand.setText("Last command: " + command);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to send: " + command, Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleTurboOn() {
        isTurboOn = true;
        try {
            mqttHelper.publish(TOPIC_CONTROL, "turbo on");
            txtLastCommand.setText("Last command: Turbo on (automatic)");
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to enable turbo mode", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendCommand(String cmd) {
        try {
            mqttHelper.publish(TOPIC_CONTROL, cmd);
            txtLastCommand.setText("Last command: " + cmd);

            // If we're in recording mode and actively recording, track that a command was sent
            if (isRecordingMode && isRecording) {
                commandSentDuringRecording = true;
                // Enable the end button once a command has been sent
                btnEndRecording.setEnabled(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to send: " + cmd, Toast.LENGTH_SHORT).show();
        }
    }

    private void setControlsEnabled(boolean enabled) {
        btnForward.setEnabled(enabled);
        btnBack.setEnabled(enabled);
        btnLeft.setEnabled(enabled);
        btnRight.setEnabled(enabled);
        btnTurbo.setEnabled(enabled);
        btnVoiceControl.setEnabled(enabled);
        btnTiltControl.setEnabled(enabled);

        if (isRecordingMode) {
            btnStartRecording.setEnabled(enabled);
            btnEndRecording.setEnabled(enabled);
        }
    }

    @Override
    protected void onDestroy() {
        // If turbo is on, turn it off before disconnecting
        if (isTurboOn && mqttHelper != null) {
            try {
                // Send turbo off command
                mqttHelper.publish(TOPIC_CONTROL, "turbo off");
                Log.d(TAG, "Sent 'turbo off' command on activity destroy");
            } catch (Exception e) {
                Log.e(TAG, "Error sending turbo off command on destroy", e);
            }
        }

        // Existing cleanup code
        if (mqttHelper != null) {
            try {
                mqttHelper.disconnect();
            } catch (MqttException e) {
                Log.e(TAG, "Error disconnecting MQTT", e);
            }
        }

        // Clean up speech recognizer
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }

        super.onDestroy();
    }

    // Add animation for activity transitions
    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

// Call setupBottomNavigation() in onCreate after initializing views
}