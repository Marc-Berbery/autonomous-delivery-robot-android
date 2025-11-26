package com.example.autonomous_delivery_robot.firebase;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.autonomous_delivery_robot.models.DeliveryLog;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class LogViewService {

    private static final String TAG = "LogViewService";
    private final DatabaseReference logsRef;

    public interface OrderIdsCallback {
        void onOrderIdsLoaded(List<String> orderIds);
        void onError(String errorMessage);
    }

    public interface LogsCallback {
        void onLogsLoaded(List<DeliveryLog> logs);
        void onError(String errorMessage);
    }

    public LogViewService(DatabaseReference logsRef) {
        this.logsRef = logsRef;
        Log.d(TAG, "LogViewService initialized with reference: " + logsRef.toString());
    }

    /**
     * Get all available order IDs from the logs database
     * @param callback Callback to receive order IDs
     */
    public void getOrderIds(OrderIdsCallback callback) {
        Log.d(TAG, "Getting all order IDs");

        logsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> orderIds = new ArrayList<>();

                for (DataSnapshot orderSnapshot : snapshot.getChildren()) {
                    String orderId = orderSnapshot.getKey();
                    if (orderId != null) {
                        orderIds.add(orderId);
                        Log.d(TAG, "Found order ID: " + orderId);
                    }
                }

                Log.d(TAG, "Found " + orderIds.size() + " order IDs");
                callback.onOrderIdsLoaded(orderIds);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error getting order IDs: " + error.getMessage(), error.toException());
                callback.onError(error.getMessage());
            }
        });
    }

    /**
     * Get all logs for a specific order
     * @param orderId The order ID to get logs for
     * @param callback Callback to receive logs
     */
    public void getLogsForOrder(String orderId, LogsCallback callback) {
        Log.d(TAG, "Getting logs for order: " + orderId);

        logsRef.child(orderId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<DeliveryLog> logs = new ArrayList<>();

                for (DataSnapshot logSnapshot : snapshot.getChildren()) {
                    try {
                        DeliveryLog log = logSnapshot.getValue(DeliveryLog.class);
                        if (log != null) {
                            logs.add(log);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing log: " + e.getMessage(), e);
                    }
                }

                Log.d(TAG, "Found " + logs.size() + " logs for order: " + orderId);
                callback.onLogsLoaded(logs);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error getting logs: " + error.getMessage(), error.toException());
                callback.onError(error.getMessage());
            }
        });
    }

    /**
     * Get the latest log entries, limited by count
     * @param limit Maximum number of logs to retrieve
     * @param callback Callback to receive logs
     */
    public void getLatestLogs(int limit, LogsCallback callback) {
        Log.d(TAG, "Getting latest " + limit + " logs across all orders");

        // First get all order IDs
        getOrderIds(new OrderIdsCallback() {
            @Override
            public void onOrderIdsLoaded(List<String> orderIds) {
                if (orderIds.isEmpty()) {
                    callback.onLogsLoaded(new ArrayList<>());
                    return;
                }

                // Get logs for each order
                List<DeliveryLog> allLogs = new ArrayList<>();
                final int[] ordersProcessed = {0};

                for (String orderId : orderIds) {
                    getLogsForOrder(orderId, new LogsCallback() {
                        @Override
                        public void onLogsLoaded(List<DeliveryLog> logs) {
                            allLogs.addAll(logs);
                            ordersProcessed[0]++;

                            // If all orders processed, sort and limit
                            if (ordersProcessed[0] == orderIds.size()) {
                                // Sort by timestamp desc
                                allLogs.sort((log1, log2) -> log2.getTimestamp().compareTo(log1.getTimestamp()));

                                // Limit results
                                List<DeliveryLog> limitedLogs = allLogs.size() > limit
                                        ? allLogs.subList(0, limit)
                                        : allLogs;

                                callback.onLogsLoaded(limitedLogs);
                            }
                        }

                        @Override
                        public void onError(String errorMessage) {
                            // Continue processing other orders
                            ordersProcessed[0]++;

                            if (ordersProcessed[0] == orderIds.size()) {
                                // Sort by timestamp desc
                                allLogs.sort((log1, log2) -> log2.getTimestamp().compareTo(log1.getTimestamp()));

                                // Limit results
                                List<DeliveryLog> limitedLogs = allLogs.size() > limit
                                        ? allLogs.subList(0, limit)
                                        : allLogs;

                                callback.onLogsLoaded(limitedLogs);
                            }
                        }
                    });
                }
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    /**
     * Search for logs matching a specific text
     * @param searchText Text to search for in log messages
     * @param callback Callback to receive matching logs
     */
    public void searchLogs(String searchText, LogsCallback callback) {
        Log.d(TAG, "Searching for logs containing: " + searchText);

        // Get all logs first, then filter
        getOrderIds(new OrderIdsCallback() {
            @Override
            public void onOrderIdsLoaded(List<String> orderIds) {
                if (orderIds.isEmpty()) {
                    callback.onLogsLoaded(new ArrayList<>());
                    return;
                }

                List<DeliveryLog> matchingLogs = new ArrayList<>();
                final int[] ordersProcessed = {0};

                for (String orderId : orderIds) {
                    getLogsForOrder(orderId, new LogsCallback() {
                        @Override
                        public void onLogsLoaded(List<DeliveryLog> logs) {
                            // Filter logs containing search text
                            for (DeliveryLog log : logs) {
                                if (log.getMessage() != null &&
                                        log.getMessage().toLowerCase().contains(searchText.toLowerCase())) {
                                    matchingLogs.add(log);
                                }
                            }

                            ordersProcessed[0]++;

                            if (ordersProcessed[0] == orderIds.size()) {
                                // Sort by timestamp desc
                                matchingLogs.sort((log1, log2) -> log2.getTimestamp().compareTo(log1.getTimestamp()));
                                callback.onLogsLoaded(matchingLogs);
                            }
                        }

                        @Override
                        public void onError(String errorMessage) {
                            // Continue processing other orders
                            ordersProcessed[0]++;

                            if (ordersProcessed[0] == orderIds.size()) {
                                // Sort by timestamp desc
                                matchingLogs.sort((log1, log2) -> log2.getTimestamp().compareTo(log1.getTimestamp()));
                                callback.onLogsLoaded(matchingLogs);
                            }
                        }
                    });
                }
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }
}