package com.example.autonomous_delivery_robot.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.autonomous_delivery_robot.R;
import com.example.autonomous_delivery_robot.adapters.OrderAdapter;
import com.example.autonomous_delivery_robot.models.Order;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class HistoryFragment extends Fragment {

    private RecyclerView recyclerView;
    private OrderAdapter adapter;
    private List<Order> orderList;
    private DatabaseReference dbRef;
    private ProgressBar progressBar;
    private TextView emptyStateText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);

        // Initialize UI elements
        recyclerView = view.findViewById(R.id.recyclerView);
        progressBar = view.findViewById(R.id.progressBar);
        emptyStateText = view.findViewById(R.id.emptyStateText);

        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        // Initialize order list
        orderList = new ArrayList<>();

        // Initialize adapter with context and order list
        adapter = new OrderAdapter(getActivity(), orderList);
        recyclerView.setAdapter(adapter);

        // Set up click listener for orders
        adapter.setOnOrderClickListener((order, position) -> {
            Toast.makeText(getActivity(),
                    "Order selected: " + order.getItem() + " (" + order.getStatus() + ")",
                    Toast.LENGTH_SHORT).show();

            // You can add more functionality here, like opening an order details dialog
        });

        // Initialize Firebase database reference
        dbRef = FirebaseDatabase.getInstance("https://autonomous-delivery-robo-53d40-default-rtdb.europe-west1.firebasedatabase.app/").getReference("orders");

        // Fetch data from Firebase
        loadOrdersFromFirebase();

        return view;
    }

    private void loadOrdersFromFirebase() {
        showLoading(true);

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

                // Update UI based on data
                if (orderList.isEmpty()) {
                    showEmptyState(true);
                } else {
                    showEmptyState(false);
                }

                adapter.notifyDataSetChanged();
                showLoading(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle database error
                Toast.makeText(getActivity(),
                        "Error loading orders: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
                showLoading(false);
                showEmptyState(true);
                emptyStateText.setText("Error loading orders");
            }
        });
    }

    private void showLoading(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void showEmptyState(boolean show) {
        if (emptyStateText != null && recyclerView != null) {
            emptyStateText.setVisibility(show ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    // You might want to add additional methods for filtering orders
    // For example:
    private void filterOrdersByStatus(String status) {
        showLoading(true);

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

                // Update UI based on data
                if (orderList.isEmpty()) {
                    showEmptyState(true);
                    emptyStateText.setText("No orders with status: " + status);
                } else {
                    showEmptyState(false);
                }

                adapter.notifyDataSetChanged();
                showLoading(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getActivity(),
                        "Error filtering orders: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
                showLoading(false);
            }
        });
    }
}