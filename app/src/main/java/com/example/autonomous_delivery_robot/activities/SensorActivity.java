package com.example.autonomous_delivery_robot.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.autonomous_delivery_robot.R;
import com.example.autonomous_delivery_robot.mqtt.MqttHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.eclipse.paho.client.mqttv3.*;

public class SensorActivity extends BaseActivity {

    private static final String SENSOR_TOPIC = "/khanfar/sensors/ultrasonic";

    private MqttHelper mqttHelper;
    private TextView txtSensor1, txtSensor2, txtSensor3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor);

        txtSensor1 = findViewById(R.id.txtSensor1);
        txtSensor2 = findViewById(R.id.txtSensor2);
        txtSensor3 = findViewById(R.id.txtSensor3);
        setupBottomNavigation();

        mqttHelper = new MqttHelper("tcp://broker.hivemq.com:1883", "client_sensor");

        mqttHelper.connect(new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                Toast.makeText(SensorActivity.this, "MQTT Connected", Toast.LENGTH_SHORT).show();
                subscribeToSensors();
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                Toast.makeText(SensorActivity.this, "MQTT Connection Failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void subscribeToSensors() {
        try {
            mqttHelper.subscribe(SENSOR_TOPIC, (topic, message) -> {
                final String payload = new String(message.getPayload());

                runOnUiThread(() -> {
                    if (payload.startsWith("sensor1:")) {
                        txtSensor1.setText("Sensor 1: " + payload.split(":")[1].trim());
                    } else if (payload.startsWith("sensor2:")) {
                        txtSensor2.setText("Sensor 2: " + payload.split(":")[1].trim());
                    } else if (payload.startsWith("sensor3:")) {
                        txtSensor3.setText("Sensor 3: " + payload.split(":")[1].trim());
                    }
                });
            });
        } catch (MqttException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to subscribe to sensors", Toast.LENGTH_SHORT).show();
        }
    }


}
