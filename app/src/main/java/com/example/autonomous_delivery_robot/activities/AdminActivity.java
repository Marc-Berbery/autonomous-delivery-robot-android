package com.example.autonomous_delivery_robot.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.autonomous_delivery_robot.R;
import com.example.autonomous_delivery_robot.firebase.UserService;
import com.example.autonomous_delivery_robot.models.User;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class AdminActivity extends BaseActivity {

    private static final String TAG = "AdminActivity";
    private RecyclerView recyclerViewUsers;
    private EditText editTextUserId;
    private Button btnSetAdmin, btnSetUser, btnViewLogs;
    private TextView textViewNoUsers;

    private UserService userService;
    private DatabaseReference usersRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        // Initialize Firebase
        userService = new UserService();
        usersRef = FirebaseDatabase.getInstance("https://autonomous-delivery-robo-53d40-default-rtdb.europe-west1.firebasedatabase.app/")
                .getReference("users");

        Log.d(TAG, "Database reference initialized: " + usersRef.toString());

        // Initialize UI components
        recyclerViewUsers = findViewById(R.id.recyclerViewUsers);
        editTextUserId = findViewById(R.id.editTextUserId);
        btnSetAdmin = findViewById(R.id.btnSetAdmin);
        btnSetUser = findViewById(R.id.btnSetUser);
        btnViewLogs = findViewById(R.id.btnViewLogs);
        textViewNoUsers = findViewById(R.id.textViewNoUsers);

        // Set up RecyclerView
        recyclerViewUsers.setLayoutManager(new LinearLayoutManager(this));
        setupBottomNavigation();


        // Load user list
        loadUsers();

        // Set up button click listeners
        btnSetAdmin.setOnClickListener(v -> {
            String userId = editTextUserId.getText().toString().trim();
            if (!TextUtils.isEmpty(userId)) {
                Log.d(TAG, "Setting user as admin: " + userId);
                userService.setUserAsAdmin(userId);
                Toast.makeText(this, "User set as admin", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Please enter a user ID", Toast.LENGTH_SHORT).show();
            }
        });

        btnSetUser.setOnClickListener(v -> {
            String userId = editTextUserId.getText().toString().trim();
            if (!TextUtils.isEmpty(userId)) {
                Log.d(TAG, "Setting user as regular user: " + userId);
                userService.setUserAsRegular(userId);
                Toast.makeText(this, "User set as regular user", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Please enter a user ID", Toast.LENGTH_SHORT).show();
            }
        });

        // Navigate to LogViewActivity
        btnViewLogs.setOnClickListener(v -> {
            Log.d(TAG, "Navigating to LogViewActivity");
            Intent intent = new Intent(AdminActivity.this, LogViewActivity.class);
            startActivity(intent);
        });
    }

    private void loadUsers() {
        Log.d(TAG, "Loading users from database");
        textViewNoUsers.setVisibility(View.GONE);

        usersRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<UserWithId> users = new ArrayList<>();

                Log.d(TAG, "Data snapshot received, has children: " + snapshot.hasChildren());

                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    Log.d(TAG, "Processing user snapshot: " + userSnapshot.getKey());

                    try {
                        User user = userSnapshot.getValue(User.class);
                        String userId = userSnapshot.getKey();

                        if (user != null) {
                            Log.d(TAG, "User loaded: " + userId + ", Email: " + user.email + ", Role: " + user.role);
                            users.add(new UserWithId(userId, user));
                        } else {
                            Log.e(TAG, "Failed to parse user data for: " + userId);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing user data: " + e.getMessage(), e);
                    }
                }

                if (users.isEmpty()) {
                    Log.d(TAG, "No users found in database");
                    textViewNoUsers.setVisibility(View.VISIBLE);
                } else {
                    Log.d(TAG, "Loaded " + users.size() + " users");
                    textViewNoUsers.setVisibility(View.GONE);
                }

                // Set adapter
                UserAdapter adapter = new UserAdapter(users);
                recyclerViewUsers.setAdapter(adapter);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Database error when loading users: " + error.getMessage(), error.toException());
                Toast.makeText(AdminActivity.this,
                        "Error loading users: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
                textViewNoUsers.setVisibility(View.VISIBLE);
                textViewNoUsers.setText("Error loading users: " + error.getMessage());
            }
        });
    }

    // Class to hold User with ID
    private static class UserWithId {
        String id;
        User user;

        UserWithId(String id, User user) {
            this.id = id;
            this.user = user;
        }
    }

    // Adapter for the RecyclerView
    private class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

        private List<UserWithId> userList;

        UserAdapter(List<UserWithId> userList) {
            this.userList = userList;
            Log.d(TAG, "UserAdapter created with " + userList.size() + " items");
        }

        @NonNull
        @Override
        public UserViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            android.view.View view = getLayoutInflater().inflate(R.layout.item_user, parent, false);
            return new UserViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
            UserWithId userWithId = userList.get(position);
            holder.bind(userWithId);
        }

        @Override
        public int getItemCount() {
            return userList.size();
        }

        class UserViewHolder extends RecyclerView.ViewHolder {
            private android.widget.TextView textViewEmail;
            private android.widget.TextView textViewRole;
            private android.widget.TextView textViewUserId;

            UserViewHolder(@NonNull android.view.View itemView) {
                super(itemView);
                textViewEmail = itemView.findViewById(R.id.textViewEmail);
                textViewRole = itemView.findViewById(R.id.textViewRole);
                textViewUserId = itemView.findViewById(R.id.textViewUserId);

                // Set click listener to copy user ID to EditText
                itemView.setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        UserWithId user = userList.get(position);
                        Log.d(TAG, "User selected: " + user.id);
                        editTextUserId.setText(user.id);
                    }
                });
            }

            void bind(UserWithId userWithId) {
                textViewEmail.setText(userWithId.user.email);
                textViewRole.setText(userWithId.user.role);
                textViewUserId.setText(userWithId.id);
            }
        }
    }


// Call setupBottomNavigation() in onCreate after initializing views
}