package com.example.autonomous_delivery_robot.mqtt;


import android.util.Log;
import org.eclipse.paho.client.mqttv3.*;

public class MqttHelper {

    private static final String TAG = "MqttHelper";
    private MqttClient client;

    public MqttHelper(String serverUri, String clientId) {
        try {
            client = new MqttClient(serverUri, clientId, null);
        } catch (MqttException e) {
            Log.e(TAG, "Error creating client", e);
        }
    }

    public void connect(IMqttActionListener callback) {
        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            client.connect(options);
            callback.onSuccess(null);
        } catch (MqttException e) {
            callback.onFailure(null, e);
        }
    }

    public void subscribe(String topic, IMqttMessageListener listener) throws MqttException {
        client.subscribe(topic, 1, listener);
    }

    public void publish(String topic, String msg) throws MqttException {
        MqttMessage message = new MqttMessage(msg.getBytes());
        message.setQos(1);
        client.publish(topic, message);
    }

    public void disconnect() throws MqttException {
        if (client != null && client.isConnected()) {
            client.disconnect();
            Log.d(TAG, "MQTT client disconnected");
        }
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }
}