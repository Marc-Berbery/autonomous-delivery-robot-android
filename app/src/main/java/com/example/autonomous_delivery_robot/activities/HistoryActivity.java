package com.example.autonomous_delivery_robot.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.autonomous_delivery_robot.R;
import com.example.autonomous_delivery_robot.adapters.OrderAdapter;
import com.example.autonomous_delivery_robot.models.Order;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends BaseActivity {

    private RecyclerView recyclerView;
    private OrderAdapter adapter;
    private List<Order> orderList;
    private DatabaseReference dbRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // Initialize UI elements
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        setupBottomNavigation();

        // Initialize order list
        orderList = new ArrayList<>();

        // Initialize adapter with context and order list
        adapter = new OrderAdapter(this, orderList);
        recyclerView.setAdapter(adapter);

        // Set up click listener for orders
        adapter.setOnOrderClickListener((order, position) -> {
            Toast.makeText(HistoryActivity.this,
                    "Order selected: " + order.getItem() + " (" + order.getStatus() + ")",
                    Toast.LENGTH_SHORT).show();

            // You can add more functionality here, like opening an order details activity
            // Intent intent = new Intent(HistoryActivity.this, OrderDetailsActivity.class);
            // intent.putExtra("FROM_LOCATION", order.fromLocation);
            // intent.putExtra("TO_LOCATION", order.toLocation);
            // intent.putExtra("ITEM", order.item);
            // startActivity(intent);
        });

        // Initialize Firebase database reference
        dbRef = FirebaseDatabase.getInstance("https://autonomous-delivery-robo-53d40-default-rtdb.europe-west1.firebasedatabase.app/").getReference("orders");

        // Fetch data from Firebase
        loadOrdersFromFirebase();
    }
    @Override
    protected int getBottomNavSelectedItemId() {
        return R.id.nav_locations;
    }
    private void loadOrdersFromFirebase() {
        dbRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                orderList.clear();

                for (DataSnapshot orderSnapshot : snapshot.getChildren()) {
                    Order order = orderSnapshot.getValue(Order.class);

                    if (order != null) {
                        orderList.add(order);
                    }
                }

                if (orderList.isEmpty()) {
                    // Show empty state or message
                    Toast.makeText(HistoryActivity.this, "No delivery orders found", Toast.LENGTH_SHORT).show();
                }

                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle database error
                Toast.makeText(HistoryActivity.this,
                        "Error loading orders: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    // You might want to add additional methods for filtering orders
    // For example:
    private void filterOrdersByStatus(String status) {
        dbRef.orderByChild("status").equalTo(status).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                orderList.clear();

                for (DataSnapshot orderSnapshot : snapshot.getChildren()) {
                    Order order = orderSnapshot.getValue(Order.class);
                    if (order != null) {
                        orderList.add(order);
                    }
                }

                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(HistoryActivity.this,
                        "Error filtering orders: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }


}