package com.example.autonomous_delivery_robot.activities;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.autonomous_delivery_robot.R;
import com.example.autonomous_delivery_robot.mqtt.MqttHelper;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttException;

public class TiltControlActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "TiltControlActivity";
    private static final String TOPIC_CONTROL = "/khanfar/control";
    private static final String STREAM_URL = "http://192.168.18.88:8000/";

    // UI Components
    private TextView txtTiltValues;
    private TextView txtLastCommandTilt;
    private TextView txtCalibrationCountdown;
    private Button btnCalibrateTilt;
    private Button btnToggleTiltControl;
    private Button btnExitTiltControl;
    private SeekBar sensitivitySeekBar;
    private WebView cameraViewTilt;

    // Sensor components
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private float[] calibrationValues = new float[3];
    private boolean tiltControlEnabled = false;
    private boolean inNeutralPosition = true;
    private String lastTiltCommand = null;

    // Calibration countdown
    private boolean isCalibrating = false;
    private CountDownTimer calibrationTimer;

    // Throttling and sensitivity
    private long lastUiUpdateTime = 0;
    private static final long UI_UPDATE_INTERVAL = 100; // Update UI max 10 times per second
    private long lastCommandTime = 0;
    private long commandCooldown = 300; // Minimum time between commands
    private float tiltThreshold = 3.0f; // Default sensitivity

    // MQTT
    private MqttHelper mqttHelper;

    // Vibration feedback
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tilt_control);

        // Initialize UI components
        txtTiltValues = findViewById(R.id.txtTiltValues);
        txtLastCommandTilt = findViewById(R.id.txtLastCommandTilt);
        txtCalibrationCountdown = findViewById(R.id.txtCalibrationCountdown);
        btnCalibrateTilt = findViewById(R.id.btnCalibrateTilt);
        btnToggleTiltControl = findViewById(R.id.btnToggleTiltControl);
        btnExitTiltControl = findViewById(R.id.btnExitTiltControl);
        sensitivitySeekBar = findViewById(R.id.sensitivitySeekBar);
        cameraViewTilt = findViewById(R.id.cameraViewTilt);

        // Initialize sensor
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accelerometer == null) {
                // Device doesn't have an accelerometer
                btnToggleTiltControl.setEnabled(false);
                btnCalibrateTilt.setEnabled(false);
                Toast.makeText(this, "Tilt control not available on this device", Toast.LENGTH_SHORT).show();
            }
        }

        // Initialize MQTT
        mqttHelper = new MqttHelper("tcp://broker.hivemq.com", "client_tilt_" + System.currentTimeMillis());

        // Set up camera stream
        setupCameraStream();

        // Connect to MQTT broker
        connectMqtt();

        // Initialize vibrator for haptic feedback
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // Set up button click listeners
        setupButtonListeners();

        // Set up sensitivity slider
        setupSensitivitySlider();

        // Create calibration timer
        createCalibrationTimer();

        // Set initial values and calibration
        calibrationValues[0] = 0;
        calibrationValues[1] = 0;
        calibrationValues[2] = 9.8f; // Default gravity value
    }

    private void setupCameraStream() {
        // Set up WebView for camera stream
        WebSettings settings = cameraViewTilt.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        cameraViewTilt.setHorizontalScrollBarEnabled(false);
        cameraViewTilt.setVerticalScrollBarEnabled(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // Set WebViewClient to handle loading
        cameraViewTilt.setWebViewClient(new WebViewClient() {
            private boolean pageLoadError = false;

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!pageLoadError) {
                    Log.d(TAG, "Camera stream loaded successfully");
                    runOnUiThread(() -> {
                        Toast.makeText(TiltControlActivity.this, "Camera connected", Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                pageLoadError = true;
                Log.e(TAG, "Error loading camera stream: " + description);
                runOnUiThread(() -> {
                    Toast.makeText(TiltControlActivity.this, "Camera connection failed", Toast.LENGTH_SHORT).show();
                });
            }
        });

        // Load the camera stream URL
        cameraViewTilt.loadUrl(STREAM_URL);
    }

    private void connectMqtt() {
        mqttHelper.connect(new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                runOnUiThread(() -> {
                    Toast.makeText(TiltControlActivity.this, "MQTT Connected", Toast.LENGTH_SHORT).show();
                    btnToggleTiltControl.setEnabled(true);
                });
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                runOnUiThread(() -> {
                    Toast.makeText(TiltControlActivity.this, "MQTT Connection Failed", Toast.LENGTH_SHORT).show();
                    btnToggleTiltControl.setEnabled(false);
                });
            }
        });
    }

    private void createCalibrationTimer() {
        calibrationTimer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsRemaining = (int) (millisUntilFinished / 1000);
                runOnUiThread(() -> {
                    txtCalibrationCountdown.setText("Hold position: " + secondsRemaining);
                    // Visual pulse feedback to show timer is active
                    if (vibrator != null && vibrator.hasVibrator()) {
                        vibrator.vibrate(50);
                    }
                });
            }

            @Override
            public void onFinish() {
                runOnUiThread(() -> {
                    txtCalibrationCountdown.setText("Calibration complete!");

                    // Capture current sensor values as calibration
                    if (sensorManager != null) {
                        // Register a one-shot sensor reading for calibration
                        sensorManager.registerListener(new SensorEventListener() {
                            @Override
                            public void onSensorChanged(SensorEvent event) {
                                if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                                    // Store current values as calibration reference
                                    calibrationValues[0] = event.values[0];
                                    calibrationValues[1] = event.values[1];
                                    calibrationValues[2] = event.values[2];

                                    // Update UI and finish calibration
                                    runOnUiThread(() -> {
                                        txtTiltValues.setText(String.format("X: 0.0, Y: 0.0"));
                                        txtCalibrationCountdown.setVisibility(View.GONE);
                                        btnCalibrateTilt.setEnabled(true);

                                        // Vibrate to indicate calibration complete
                                        if (vibrator != null && vibrator.hasVibrator()) {
                                            vibrator.vibrate(300);
                                        }
                                    });

                                    // Unregister this one-shot listener
                                    sensorManager.unregisterListener(this);

                                    // Reset calibration flag
                                    isCalibrating = false;
                                }
                            }

                            @Override
                            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                                // Not needed
                            }
                        }, accelerometer, SensorManager.SENSOR_DELAY_UI);
                    }

                    // After calibration, restore the tilt control state
                    if (tiltControlEnabled) {
                        // Re-register the sensor listener if tilt control was on
                        if (sensorManager != null) {
                            sensorManager.registerListener(TiltControlActivity.this,
                                    accelerometer, SensorManager.SENSOR_DELAY_UI);
                        }
                    }
                });
            }
        };
    }

    private void setupButtonListeners() {
        // Calibrate button
        btnCalibrateTilt.setOnClickListener(v -> calibrateTiltSensor());

        // Toggle tilt control button
        btnToggleTiltControl.setOnClickListener(v -> toggleTiltControl());

        // Exit button
        btnExitTiltControl.setOnClickListener(v -> {
            // Make sure to stop tilt control before exiting
            if (tiltControlEnabled) {
                toggleTiltControl();
            }
            finish(); // Close the activity
        });
    }

    private void setupSensitivitySlider() {
        // Set initial progress
        sensitivitySeekBar.setProgress(50); // Default middle sensitivity

        sensitivitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Update sensitivity based on slider position
                // Make threshold between 1.5 (very sensitive) and 6.0 (less sensitive)
                tiltThreshold = 6.0f - ((progress / 100f) * 4.5f);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Not needed
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Toast.makeText(TiltControlActivity.this,
                        "Sensitivity set to " + (seekBar.getProgress() > 50 ? "high" : "low"),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void toggleTiltControl() {
        tiltControlEnabled = !tiltControlEnabled;

        if (tiltControlEnabled) {
            // Start tilt control
            btnToggleTiltControl.setText("Stop Tilt Control");
            btnToggleTiltControl.setBackgroundTintList(getResources().getColorStateList(android.R.color.holo_red_light));

            // Make sure we have calibration values
            if (calibrationValues[0] == 0 && calibrationValues[1] == 0) {
                // No calibration yet, let's do it now
                calibrateTiltSensor();
            }

            // Start listening to sensor if not calibrating
            if (!isCalibrating && sensorManager != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
            }

            Toast.makeText(this, "Tilt control activated", Toast.LENGTH_SHORT).show();

            // Vibrate to indicate activation
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(200);
            }
        } else {
            // Stop tilt control
            btnToggleTiltControl.setText("Start Tilt Control");
            btnToggleTiltControl.setBackgroundTintList(getResources().getColorStateList(android.R.color.holo_green_light));

            // Stop listening to sensor if not calibrating
            if (!isCalibrating && sensorManager != null) {
                sensorManager.unregisterListener(this);
            }

            // Send stop command
            sendCommand("stop");

            // Reset state
            inNeutralPosition = true;
            lastTiltCommand = null;

            Toast.makeText(this, "Tilt control deactivated", Toast.LENGTH_SHORT).show();
        }
    }

    private void calibrateTiltSensor() {

        // Send stop command first
        sendCommand("stop");
        // Disable calibration button to prevent multiple calibrations
        btnCalibrateTilt.setEnabled(false);

        // Set the calibration flag
        isCalibrating = true;



        // Unregister sensor temporarily during calibration to prevent commands
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }

        // Show countdown timer
        txtCalibrationCountdown.setVisibility(View.VISIBLE);
        txtCalibrationCountdown.setText("Preparing calibration...");

        // Vibrate to indicate calibration start
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(200);
        }

        Toast.makeText(this, "Hold phone in neutral position for 5 seconds", Toast.LENGTH_SHORT).show();

        // Give a brief pause before starting countdown
        new Handler().postDelayed(() -> {
            // Start the calibration timer
            calibrationTimer.start();
        }, 500);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER || !tiltControlEnabled || isCalibrating) {
            return;
        }

        // Get current accelerometer values
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        // Calculate tilt relative to calibration
        float tiltX = x - calibrationValues[0]; // Left/Right tilt
        float tiltY = y - calibrationValues[1]; // Forward/Back tilt

        // Update UI - throttle updates
        long currentTime = System.currentTimeMillis();
        boolean shouldUpdateUi = (currentTime - lastUiUpdateTime) > UI_UPDATE_INTERVAL;

        if (shouldUpdateUi) {
            lastUiUpdateTime = currentTime;
            runOnUiThread(() -> {
                txtTiltValues.setText(String.format("X: %.1f, Y: %.1f", tiltX, tiltY));
            });
        }

        // Check if we're in a neutral position (no significant tilt)
        boolean isNeutralNow = Math.abs(tiltX) < tiltThreshold/2 && Math.abs(tiltY) < tiltThreshold/2;

        // If we've returned to neutral, reset our state to allow new commands
        if (isNeutralNow) {
            if (!inNeutralPosition) {
                // We just returned to neutral, send stop command
                sendCommand("stop");
                inNeutralPosition = true;
                lastTiltCommand = null;
            }
            return;
        }

        // If we're already in a tilted position, don't send any more commands
        // until we return to neutral first
        if (!inNeutralPosition) {
            return;
        }

        // Determine command based on tilt
        String command = null;

        if (Math.abs(tiltX) > Math.abs(tiltY)) {
            // Left/Right tilt is stronger
            if (tiltX > tiltThreshold) {
                command = "left";  // Tilting phone to the left
            } else if (tiltX < -tiltThreshold) {
                command = "right"; // Tilting phone to the right
            }
        } else {
            // Forward/Back tilt is stronger
            if (tiltY > tiltThreshold) {
                command = "backward"; // Tilting phone forward
            } else if (tiltY < -tiltThreshold) {
                command = "forward";  // Tilting phone backward
            }
        }

        // Check command cooldown
        boolean canSendCommand = (currentTime - lastCommandTime) > commandCooldown;

        // Send command if we have one and we're transitioning from neutral and cooldown has elapsed
        if (command != null && canSendCommand) {
            sendCommand(command);
            inNeutralPosition = false;
            lastTiltCommand = command;
            lastCommandTime = currentTime;

            // Vibrate to indicate command sent
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(50);
            }
        }
    }

    private void sendCommand(String cmd) {
        // Only send commands if not calibrating
        if (isCalibrating) {
            Log.d(TAG, "Command not sent (calibrating): " + cmd);
            return;
        }

        try {
            mqttHelper.publish(TOPIC_CONTROL, cmd);

            // Update UI
            runOnUiThread(() -> {
                txtLastCommandTilt.setText(cmd);
            });

            Log.d(TAG, "Command sent: " + cmd);
        } catch (MqttException e) {
            Log.e(TAG, "Error sending command", e);
            runOnUiThread(() -> {
                Toast.makeText(this, "Failed to send: " + cmd, Toast.LENGTH_SHORT).show();
            });
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed for this implementation
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop sensor when activity is paused
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }

        // Send stop command when leaving
        if (tiltControlEnabled) {
            sendCommand("stop");
        }

        // Cancel any active calibration
        if (isCalibrating) {
            calibrationTimer.cancel();
            isCalibrating = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Resume sensor if tilt control was enabled and not calibrating
        if (tiltControlEnabled && !isCalibrating && sensorManager != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up MQTT
        if (mqttHelper != null) {
            try {
                mqttHelper.disconnect();
            } catch (MqttException e) {
                Log.e(TAG, "Error disconnecting MQTT", e);
            }
        }

        // Unregister sensors
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }

        // Cancel any timers
        if (calibrationTimer != null) {
            calibrationTimer.cancel();
        }
    }
}