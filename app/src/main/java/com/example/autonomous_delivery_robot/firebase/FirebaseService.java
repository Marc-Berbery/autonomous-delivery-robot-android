package com.example.autonomous_delivery_robot.firebase;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.example.autonomous_delivery_robot.models.Order;

public class FirebaseService {

    private final DatabaseReference ref;

    public FirebaseService() {
        // Use the European database URL - replace with your actual Firebase database URL
        FirebaseDatabase database = FirebaseDatabase.getInstance("https://autonomous-delivery-robo-53d40-default-rtdb.europe-west1.firebasedatabase.app/");
        ref = database.getReference("deliveries");
    }

    public void logOrder(Order order) {
        ref.push().setValue(order);
    }
}
