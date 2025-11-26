package com.example.autonomous_delivery_robot.firebase;

import android.util.Log;

import com.example.autonomous_delivery_robot.models.DeliveryLog;
import com.example.autonomous_delivery_robot.mqtt.MqttHelper;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class LogService {
    private static final String TAG = "LogService";
    private boolean isRecording = false;
    private String currentOrderId = null;
    private DatabaseReference logsRef;
    private MqttHelper mqttHelper;
    private boolean listenersInitialized = false;

    // MQTT topics to subscribe to
    private static final String[] TOPICS = {
            "/khanfar/sensors/ultrasonic",
            "/khanfar/control",
            "/khanfar/delivery",
            "/khanfar/status",
            "/khanfar/IR"
    };

    // Messages to filter out (skip)
    private static final Set<String> FILTERED_IR_MESSAGES = new HashSet<>(
            Arrays.asList("package_present", "no_package")
    );

    // Store listeners to avoid recreating them
    private Map<String, IMqttMessageListener> topicListeners = new HashMap<>();

    public LogService() {
        FirebaseDatabase database = FirebaseDatabase.getInstance("https://autonomous-delivery-robo-53d40-default-rtdb.europe-west1.firebasedatabase.app/");
        logsRef = database.getReference("logs");
    }

    public void initialize(String brokerUrl) {
        // Create a separate MqttHelper instance specifically for logging
        this.mqttHelper = new MqttHelper(brokerUrl, "logging_client_" + System.currentTimeMillis());

        // Connect the logging MQTT client
        mqttHelper.connect(new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                Log.d(TAG, "LogService MQTT client connected successfully");
                if (!listenersInitialized) {
                    setupMqttListeners();
                    listenersInitialized = true;
                }
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                Log.e(TAG, "LogService MQTT connection failed", exception);
            }
        });
    }

    private void setupMqttListeners() {
        for (String topic : TOPICS) {
            IMqttMessageListener listener = (t, message) -> {
                if (isRecording) {
                    String payload = new String(message.getPayload());

                    // Skip filtered IR messages
                    if (t.equals("/khanfar/IR") && FILTERED_IR_MESSAGES.contains(payload)) {
                        return;
                    }

                    // Skip manual control commands when not in delivery mode
                    if (t.equals("/khanfar/control")) {
                        // Skip basic control commands (forward, backward, left, right, etc.)
                        if (payload.equalsIgnoreCase("forward") ||
                                payload.equalsIgnoreCase("backward") ||
                                payload.equalsIgnoreCase("left") ||
                                payload.equalsIgnoreCase("right") ||
                                payload.equalsIgnoreCase("stop") ||
                                payload.startsWith("turbo")) {
                            return;
                        }
                    }

                    recordLog(t, payload);
                }
            };

            topicListeners.put(topic, listener);

            try {
                mqttHelper.subscribe(topic, listener);
                Log.d(TAG, "Subscribed to topic for logging: " + topic);
            } catch (MqttException e) {
                Log.e(TAG, "Failed to subscribe to topic: " + topic, e);
            }
        }
    }

    public void startRecording(String orderId) {
        if (isRecording) {
            Log.w(TAG, "Already recording logs for order: " + currentOrderId);
            return;
        }

        if (mqttHelper == null) {
            Log.e(TAG, "MqttHelper not initialized. Call initialize() first");
            return;
        }

        currentOrderId = orderId;
        isRecording = true;

        // Record start log
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String timestamp = sdf.format(new Date());

        DeliveryLog startLog = new DeliveryLog(
                currentOrderId,
                timestamp,
                "logging",
                "Log recording started"
        );

        logsRef.child(currentOrderId).push().setValue(startLog);
        Log.d(TAG, "Started recording logs for order: " + currentOrderId);
    }

    public void stopRecording() {
        if (!isRecording) {
            Log.w(TAG, "Not currently recording logs");
            return;
        }

        // Record stop log
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String timestamp = sdf.format(new Date());

        DeliveryLog endLog = new DeliveryLog(
                currentOrderId,
                timestamp,
                "logging",
                "Log recording stopped"
        );

        logsRef.child(currentOrderId).push().setValue(endLog)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Stopped recording logs for order: " + currentOrderId);
                    isRecording = false;
                    currentOrderId = null;
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save end log", e);
                });
    }

    public void recordLog(String topic, String message) {
        if (!isRecording || currentOrderId == null) {
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String timestamp = sdf.format(new Date());

        DeliveryLog log = new DeliveryLog(
                currentOrderId,
                timestamp,
                topic,
                message
        );

        logsRef.child(currentOrderId).push().setValue(log)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save log: " + message, e);
                });
    }

    // Method to publish messages from the LogService MQTT client
    public void publishMessage(String topic, String message) {
        if (mqttHelper != null) {
            try {
                mqttHelper.publish(topic, message);
            } catch (MqttException e) {
                Log.e(TAG, "Failed to publish message: " + message, e);
            }
        }
    }

    // New method to log custom events
    public void recordCustomLog(String message) {
        if (!isRecording || currentOrderId == null) {
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String timestamp = sdf.format(new Date());

        DeliveryLog log = new DeliveryLog(
                currentOrderId,
                timestamp,
                "app_event",  // Custom topic for app-generated events
                message
        );

        logsRef.child(currentOrderId).push().setValue(log)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save custom log: " + message, e);
                });
    }

    public boolean isRecording() {
        return isRecording;
    }

    public void cleanup() {
        if (isRecording) {
            stopRecording();
        }

        // Explicitly disconnect the MQTT client
        if (mqttHelper != null) {
            try {
                mqttHelper.disconnect();
            } catch (MqttException e) {
                Log.e(TAG, "Error disconnecting MQTT in LogService", e);
            }
        }
    }
}