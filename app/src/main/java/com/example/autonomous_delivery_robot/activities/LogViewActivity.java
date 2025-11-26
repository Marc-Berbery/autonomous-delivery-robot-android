package com.example.autonomous_delivery_robot.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.autonomous_delivery_robot.R;
import com.example.autonomous_delivery_robot.firebase.LogViewService;
import com.example.autonomous_delivery_robot.models.DeliveryLog;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LogViewActivity extends AppCompatActivity {

    private static final String TAG = "LogViewActivity";

    // UI components
    private Spinner spinnerOrderIds;
    private Button btnRefreshOrders;
    private EditText editTextFilterLogs;
    private Spinner spinnerTopicFilter;
    private RecyclerView recyclerViewLogs;
    private TextView textViewNoLogs;
    private ProgressBar progressBar;

    // Data
    private List<String> orderIds = new ArrayList<>();
    private List<DeliveryLog> allLogs = new ArrayList<>();
    private List<DeliveryLog> filteredLogs = new ArrayList<>();
    private Set<String> topicsList = new HashSet<>();

    // Firebase
    private DatabaseReference logsRef;
    private LogViewService logViewService;

    // Adapters
    private ArrayAdapter<String> orderAdapter;
    private ArrayAdapter<String> topicAdapter;
    private LogAdapter logAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_view);

        // Initialize Firebase
        FirebaseDatabase database = FirebaseDatabase.getInstance("https://autonomous-delivery-robo-53d40-default-rtdb.europe-west1.firebasedatabase.app/");
        logsRef = database.getReference("logs");
        logViewService = new LogViewService(logsRef);

        // Initialize UI components
        spinnerOrderIds = findViewById(R.id.spinnerOrderIds);
        btnRefreshOrders = findViewById(R.id.btnRefreshOrders);
        editTextFilterLogs = findViewById(R.id.editTextFilterLogs);
        spinnerTopicFilter = findViewById(R.id.spinnerTopicFilter);
        recyclerViewLogs = findViewById(R.id.recyclerViewLogs);
        textViewNoLogs = findViewById(R.id.textViewNoLogs);
        progressBar = findViewById(R.id.progressBar);

        // Setup RecyclerView
        recyclerViewLogs.setLayoutManager(new LinearLayoutManager(this));
        logAdapter = new LogAdapter(filteredLogs);
        recyclerViewLogs.setAdapter(logAdapter);

        // Setup Spinners
        orderAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, orderIds);
        orderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerOrderIds.setAdapter(orderAdapter);

        // Add "All Topics" option as the first item
        topicsList.add("All Topics");
        topicAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>(topicsList));
        topicAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTopicFilter.setAdapter(topicAdapter);

        // Load initial data
        loadOrderIds();

        // Setup event listeners
        setupEventListeners();
    }

    private void setupEventListeners() {
        // Refresh button
        btnRefreshOrders.setOnClickListener(v -> loadOrderIds());

        // Order selection
        spinnerOrderIds.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedOrderId = orderIds.get(position);
                loadLogsForOrder(selectedOrderId);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        // Topic filter
        spinnerTopicFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyFilters();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        // Text filter
        editTextFilterLogs.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Not used
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Not used
            }
        });
    }

    private void loadOrderIds() {
        showProgress(true);
        logViewService.getOrderIds(new LogViewService.OrderIdsCallback() {
            @Override
            public void onOrderIdsLoaded(List<String> ids) {
                orderIds.clear();
                orderIds.addAll(ids);
                orderAdapter.notifyDataSetChanged();

                showProgress(false);

                if (!orderIds.isEmpty()) {
                    spinnerOrderIds.setSelection(0);
                    loadLogsForOrder(orderIds.get(0));
                } else {
                    textViewNoLogs.setText("No orders found");
                    textViewNoLogs.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String errorMessage) {
                showProgress(false);
                Toast.makeText(LogViewActivity.this,
                        "Error loading orders: " + errorMessage,
                        Toast.LENGTH_SHORT).show();
                textViewNoLogs.setText("Error loading orders: " + errorMessage);
                textViewNoLogs.setVisibility(View.VISIBLE);
            }
        });
    }

    private void loadLogsForOrder(String orderId) {
        showProgress(true);
        textViewNoLogs.setVisibility(View.GONE);

        logViewService.getLogsForOrder(orderId, new LogViewService.LogsCallback() {
            @Override
            public void onLogsLoaded(List<DeliveryLog> logs) {
                allLogs.clear();
                allLogs.addAll(logs);

                // Sort logs by timestamp (newest first)
                Collections.sort(allLogs, new Comparator<DeliveryLog>() {
                    @Override
                    public int compare(DeliveryLog log1, DeliveryLog log2) {
                        return log2.getTimestamp().compareTo(log1.getTimestamp());
                    }
                });

                // Extract unique topics for filtering
                updateTopicsList();

                // Apply current filters
                applyFilters();

                showProgress(false);

                if (allLogs.isEmpty()) {
                    textViewNoLogs.setText("No logs found for this order");
                    textViewNoLogs.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String errorMessage) {
                showProgress(false);
                Toast.makeText(LogViewActivity.this,
                        "Error loading logs: " + errorMessage,
                        Toast.LENGTH_SHORT).show();
                textViewNoLogs.setText("Error loading logs: " + errorMessage);
                textViewNoLogs.setVisibility(View.VISIBLE);
            }
        });
    }

    private void updateTopicsList() {
        // Clear previous topics but keep "All Topics"
        topicsList.clear();
        topicsList.add("All Topics");

        // Extract unique topics
        for (DeliveryLog log : allLogs) {
            if (log.getTopic() != null && !log.getTopic().isEmpty()) {
                topicsList.add(log.getTopic());
            }
        }

        // Update adapter
        topicAdapter.clear();
        topicAdapter.addAll(new ArrayList<>(topicsList));
        topicAdapter.notifyDataSetChanged();
    }

    private void applyFilters() {
        String selectedTopic = spinnerTopicFilter.getSelectedItem().toString();
        String textFilter = editTextFilterLogs.getText().toString().toLowerCase().trim();

        filteredLogs.clear();

        for (DeliveryLog log : allLogs) {
            // Check topic filter
            boolean passesTopicFilter = selectedTopic.equals("All Topics") ||
                    (log.getTopic() != null && log.getTopic().equals(selectedTopic));

            // Check text filter
            boolean passesTextFilter = textFilter.isEmpty() ||
                    (log.getMessage() != null && log.getMessage().toLowerCase().contains(textFilter));

            if (passesTopicFilter && passesTextFilter) {
                filteredLogs.add(log);
            }
        }

        logAdapter.notifyDataSetChanged();

        if (filteredLogs.isEmpty() && !allLogs.isEmpty()) {
            textViewNoLogs.setText("No logs match the current filters");
            textViewNoLogs.setVisibility(View.VISIBLE);
        } else {
            textViewNoLogs.setVisibility(View.GONE);
        }
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // RecyclerView Adapter for displaying logs
    private class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {

        private List<DeliveryLog> logList;

        LogAdapter(List<DeliveryLog> logList) {
            this.logList = logList;
        }

        @NonNull
        @Override
        public LogViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            android.view.View view = getLayoutInflater().inflate(R.layout.item_log, parent, false);
            return new LogViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
            DeliveryLog log = logList.get(position);
            holder.bind(log);
        }

        @Override
        public int getItemCount() {
            return logList.size();
        }

        class LogViewHolder extends RecyclerView.ViewHolder {
            private TextView textViewTimestamp;
            private TextView textViewTopic;
            private TextView textViewMessage;

            LogViewHolder(@NonNull android.view.View itemView) {
                super(itemView);
                textViewTimestamp = itemView.findViewById(R.id.textViewTimestamp);
                textViewTopic = itemView.findViewById(R.id.textViewTopic);
                textViewMessage = itemView.findViewById(R.id.textViewMessage);
            }

            void bind(DeliveryLog log) {
                textViewTimestamp.setText(log.getTimestamp());
                textViewTopic.setText(log.getTopic());
                textViewMessage.setText(log.getMessage());

                // Color-code different topics
                if (log.getTopic() != null) {
                    switch (log.getTopic()) {
                        case "/khanfar/sensors/ultrasonic":
                            textViewTopic.setTextColor(getResources().getColor(android.R.color.holo_blue_light));
                            break;
                        case "/khanfar/control":
                            textViewTopic.setTextColor(getResources().getColor(android.R.color.holo_orange_light));
                            break;
                        case "/khanfar/delivery":
                            textViewTopic.setTextColor(getResources().getColor(android.R.color.holo_green_light));
                            break;
                        case "/khanfar/status":
                            textViewTopic.setTextColor(getResources().getColor(android.R.color.holo_purple));
                            break;
                        case "/khanfar/IR":
                            textViewTopic.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                            break;
                        case "app_event":
                            textViewTopic.setTextColor(getResources().getColor(android.R.color.white));
                            break;
                        case "logging":
                            textViewTopic.setTextColor(getResources().getColor(android.R.color.darker_gray));
                            break;
                        default:
                            textViewTopic.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                            break;
                    }
                }
            }
        }
    }


}