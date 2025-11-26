package com.example.autonomous_delivery_robot.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.autonomous_delivery_robot.R;
import com.example.autonomous_delivery_robot.firebase.UserService;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.SignInMethodQueryResult;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.List;

public class AuthActivity extends AppCompatActivity {

    private static final String TAG = "AuthActivity";
    private TextInputLayout textInputEmail;
    private TextInputLayout textInputPassword;
    private MaterialButton buttonLogin;
    private MaterialButton buttonSignup;
    private MaterialButton buttonGoogleSignIn;
    private ImageView imageLogo;

    private FirebaseAuth firebaseAuth;
    private GoogleSignInClient googleSignInClient;
    private UserService userService;
    private DatabaseReference usersRef;

    private static final int RC_SIGN_IN = 9001;

    // Store the last attempted email/password for potential account linking
    private String lastAttemptedEmail = "";
    private String lastAttemptedPassword = "";
    // Store Google credential for potential linking
    private AuthCredential pendingGoogleCredential = null;

    // For development, set this to true to make your first sign-up an admin
    private static final boolean MAKE_FIRST_SIGNUP_ADMIN = true;

    // Flag to track if we've already checked for existing admins
    private boolean adminsChecked = false;
    private boolean adminExists = false;

    // Animation
    private Animation fadeIn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        // Initialize animations
        fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
        fadeIn.setDuration(1000);

        textInputEmail = findViewById(R.id.textInputEmail);
        textInputPassword = findViewById(R.id.textInputPassword);
        buttonLogin = findViewById(R.id.buttonLogin);
        buttonSignup = findViewById(R.id.buttonSignup);
        buttonGoogleSignIn = findViewById(R.id.buttonGoogleSignIn);
        imageLogo = findViewById(R.id.imageLogo);

        firebaseAuth = FirebaseAuth.getInstance();
        userService = new UserService();
        usersRef = FirebaseDatabase.getInstance("https://autonomous-delivery-robo-53d40-default-rtdb.europe-west1.firebasedatabase.app/")
                .getReference("users");

        // Animate logo
        imageLogo.startAnimation(fadeIn);

        // Check if admin already exists
        checkForExistingAdmins();

        // Configure Google Sign In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken("272622697078-bm5khf1pcr44ci4eo26ridunb6v5r742.apps.googleusercontent.com")
                .requestEmail()
                .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);

        // Email/Password Login
        buttonLogin.setOnClickListener(v -> {
            // Button animation
            animateButtonClick(buttonLogin);

            String email = getEmailInput();
            String password = getPasswordInput();

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                showInputErrors(email, password);
                return;
            }

            // Store for potential account linking
            lastAttemptedEmail = email;
            lastAttemptedPassword = password;

            Log.d(TAG, "Attempting to sign in with email: " + email);
            firebaseAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete(@NonNull Task<AuthResult> task) {
                            if (task.isSuccessful()) {
                                FirebaseUser user = firebaseAuth.getCurrentUser();
                                Log.d(TAG, "signInWithEmail:success, user: " + (user != null ? user.getUid() : "null"));

                                // Check if we have a pending Google credential to link
                                if (pendingGoogleCredential != null && user != null) {
                                    linkWithGoogle(user, pendingGoogleCredential);
                                } else {
                                    navigateToMainActivity();
                                }
                            } else {
                                Log.w(TAG, "signInWithEmail:failure", task.getException());
                                showErrorToast("Authentication failed: " +
                                        (task.getException() != null ? task.getException().getMessage() : "Unknown error"));
                            }
                        }
                    });
        });

        // Email/Password Signup
        buttonSignup.setOnClickListener(v -> {
            // Button animation
            animateButtonClick(buttonSignup);

            String email = getEmailInput();
            String password = getPasswordInput();

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                showInputErrors(email, password);
                return;
            }

            // Store for potential account linking
            lastAttemptedEmail = email;
            lastAttemptedPassword = password;

            Log.d(TAG, "Attempting to create account with email: " + email);
            firebaseAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete(@NonNull Task<AuthResult> task) {
                            if (task.isSuccessful()) {
                                FirebaseUser user = firebaseAuth.getCurrentUser();
                                Log.d(TAG, "createUserWithEmail:success, user: " + (user != null ? user.getUid() : "null"));
                                Toast.makeText(AuthActivity.this, "Signup Successful", Toast.LENGTH_SHORT).show();

                                if (user != null) {
                                    // Decide the role based on whether an admin already exists
                                    String role;

                                    // If we haven't checked for admins yet or the check is still in progress, use the default logic
                                    if (!adminsChecked) {
                                        role = MAKE_FIRST_SIGNUP_ADMIN ? UserService.ROLE_ADMIN : UserService.ROLE_USER;
                                        Log.d(TAG, "Admin check not complete, using default logic. Role: " + role);
                                    } else {
                                        // If we have checked and no admin exists, make this user admin (if MAKE_FIRST_SIGNUP_ADMIN is true)
                                        if (!adminExists && MAKE_FIRST_SIGNUP_ADMIN) {
                                            role = UserService.ROLE_ADMIN;
                                            Log.d(TAG, "No admin exists, setting this user as admin");
                                        } else {
                                            role = UserService.ROLE_USER;
                                            Log.d(TAG, "Admin already exists or first signup admin disabled, setting as regular user");
                                        }
                                    }

                                    Log.d(TAG, "Setting user role to: " + role);
                                    userService.saveUser(user.getEmail(), role);

                                    // Check if we have a pending Google credential to link
                                    if (pendingGoogleCredential != null) {
                                        linkWithGoogle(user, pendingGoogleCredential);
                                    } else {
                                        navigateToMainActivity();
                                    }
                                }
                            } else {
                                Log.w(TAG, "createUserWithEmail:failure", task.getException());
                                showErrorToast("Authentication failed: " +
                                        (task.getException() != null ? task.getException().getMessage() : "Unknown error"));
                            }
                        }
                    });
        });

        // Google Sign-In
        buttonGoogleSignIn.setOnClickListener(v -> {
            // Button animation
            animateButtonClick(buttonGoogleSignIn);

            Log.d(TAG, "Starting Google sign-in intent");
            Intent signInIntent = googleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        });
    }

    private void animateButtonClick(MaterialButton button) {
        button.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction(() -> {
                    button.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start();
                })
                .start();
    }

    private String getEmailInput() {
        return textInputEmail.getEditText() != null ?
                textInputEmail.getEditText().getText().toString().trim() : "";
    }

    private String getPasswordInput() {
        return textInputPassword.getEditText() != null ?
                textInputPassword.getEditText().getText().toString().trim() : "";
    }

    private void showInputErrors(String email, String password) {
        if (TextUtils.isEmpty(email)) {
            textInputEmail.setError("Email is required");
        } else {
            textInputEmail.setError(null);
        }

        if (TextUtils.isEmpty(password)) {
            textInputPassword.setError("Password is required");
        } else {
            textInputPassword.setError(null);
        }

        Toast.makeText(AuthActivity.this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
    }

    private void showErrorToast(String message) {
        Toast.makeText(AuthActivity.this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onStart() {
        super.onStart();
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            navigateToMainActivity();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                String email = account.getEmail();

                // Pre-fill the email field
                if (textInputEmail.getEditText() != null) {
                    textInputEmail.getEditText().setText(email);
                }

                // Check if this email already exists in Firebase
                checkExistingAccountAndSignIn(account);
            } catch (ApiException e) {
                showErrorToast("Google sign in failed: " + e.getStatusCode());
            }
        }
    }

    private void checkExistingAccountAndSignIn(GoogleSignInAccount account) {
        // Create the Google credential
        AuthCredential googleCredential = GoogleAuthProvider.getCredential(account.getIdToken(), null);

        // Store this credential for potential linking
        pendingGoogleCredential = googleCredential;

        // First, check if the email exists in Firebase
        firebaseAuth.fetchSignInMethodsForEmail(account.getEmail())
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        SignInMethodQueryResult result = task.getResult();

                        if (result != null && result.getSignInMethods() != null && !result.getSignInMethods().isEmpty()) {
                            // Email exists in Firebase
                            boolean hasEmailPassword = result.getSignInMethods().contains(EmailAuthProvider.EMAIL_PASSWORD_SIGN_IN_METHOD);
                            boolean hasGoogle = result.getSignInMethods().contains(GoogleAuthProvider.GOOGLE_SIGN_IN_METHOD);

                            if (hasEmailPassword && hasGoogle) {
                                // User already has both methods linked
                                // Just sign in with Google
                                firebaseAuthWithGoogle(googleCredential);
                            } else if (hasEmailPassword) {
                                // The email has email/password authentication but not Google
                                // Ask user if they want to link accounts
                                showSignInMethodChoiceDialog(account.getEmail(), googleCredential);
                            } else {
                                // Email exists but doesn't have email/password authentication
                                // Proceed with Google sign-in
                                firebaseAuthWithGoogle(googleCredential);
                            }
                        } else {
                            // Email doesn't exist in Firebase
                            // Ask if they want to create a new account with both methods
                            showNewAccountOptionsDialog(account.getEmail(), googleCredential);
                        }
                    } else {
                        // Error checking for email, proceed with Google sign-in
                        firebaseAuthWithGoogle(googleCredential);
                    }
                });
    }

    private void showNewAccountOptionsDialog(String email, AuthCredential googleCredential) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Create New Account")
                .setMessage("Would you like to create a password for this account so you can sign in with either Google or email/password?")
                .setPositiveButton("Yes, set password", (dialog, which) -> {
                    // Focus on password field since email is already filled
                    if (textInputPassword.getEditText() != null) {
                        textInputPassword.getEditText().requestFocus();
                    }
                    Toast.makeText(AuthActivity.this,
                            "Please enter a password and click Sign Up to complete account creation",
                            Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("No, use Google only", (dialog, which) -> {
                    // Proceed with Google sign-in only
                    firebaseAuthWithGoogle(googleCredential);
                })
                .setCancelable(false)
                .show();
    }

    private void showSignInMethodChoiceDialog(String email, AuthCredential googleCredential) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Account Exists")
                .setMessage("An account with this email already exists. Would you like to sign in with your password and link it with Google?")
                .setPositiveButton("Sign in with password", (dialog, which) -> {
                    // Focus on password field since email is already filled
                    if (textInputPassword.getEditText() != null) {
                        textInputPassword.getEditText().requestFocus();
                    }
                    Toast.makeText(AuthActivity.this, "Please enter your password to sign in and link with Google",
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Use Google only", (dialog, which) -> {
                    // Try Google sign-in which will likely fail due to collision
                    // This will trigger our collision handler
                    firebaseAuthWithGoogle(googleCredential);
                })
                .setCancelable(false)
                .show();
    }

    private void firebaseAuthWithGoogle(AuthCredential googleCredential) {
        Log.d(TAG, "Attempting to sign in with Google credential");
        firebaseAuth.signInWithCredential(googleCredential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        Log.d(TAG, "signInWithCredential:success, user: " + (user != null ? user.getUid() : "null"));

                        if (user != null) {
                            // Check if this is a new user
                            boolean isNewUser = task.getResult().getAdditionalUserInfo().isNewUser();
                            Log.d(TAG, "Is new user: " + isNewUser);

                            if (isNewUser) {
                                // Decide the role based on whether an admin already exists
                                String role;

                                // If we haven't checked for admins yet or the check is still in progress, use the default logic
                                if (!adminsChecked) {
                                    role = MAKE_FIRST_SIGNUP_ADMIN ? UserService.ROLE_ADMIN : UserService.ROLE_USER;
                                    Log.d(TAG, "Admin check not complete, using default logic. Role: " + role);
                                } else {
                                    // If we have checked and no admin exists, make this user admin (if MAKE_FIRST_SIGNUP_ADMIN is true)
                                    if (!adminExists && MAKE_FIRST_SIGNUP_ADMIN) {
                                        role = UserService.ROLE_ADMIN;
                                        Log.d(TAG, "No admin exists, setting this user as admin");
                                    } else {
                                        role = UserService.ROLE_USER;
                                        Log.d(TAG, "Admin already exists or first signup admin disabled, setting as regular user");
                                    }
                                }

                                Log.d(TAG, "Setting new Google user role to: " + role);
                                userService.saveUser(user.getEmail(), role);
                            }

                            // Check if user has both sign-in methods
                            checkUserProviders(user, googleCredential);
                        } else {
                            navigateToMainActivity();
                        }
                    } else {
                        Log.w(TAG, "signInWithCredential:failure", task.getException());
                        // Check if the error is due to account collision
                        if (task.getException() instanceof FirebaseAuthUserCollisionException) {
                            handleAccountCollision();
                        } else {
                            showErrorToast("Authentication failed: " +
                                    (task.getException() != null ? task.getException().getMessage() : "Unknown error"));
                        }
                    }
                });
    }

    private void checkUserProviders(FirebaseUser user, AuthCredential googleCredential) {
        // Get provider data for this user
        List<? extends UserInfo> providerData = user.getProviderData();
        boolean hasPassword = false;
        boolean hasGoogle = false;

        // Check what providers the user has
        for (UserInfo profile : providerData) {
            String providerId = profile.getProviderId();
            if (EmailAuthProvider.PROVIDER_ID.equals(providerId)) {
                hasPassword = true;
            } else if (GoogleAuthProvider.PROVIDER_ID.equals(providerId)) {
                hasGoogle = true;
            }
        }

        // If user has Google but no password and they entered a password, offer to link
        if (hasGoogle && !hasPassword && textInputPassword.getEditText() != null
                && !TextUtils.isEmpty(textInputPassword.getEditText().getText())) {
            String password = textInputPassword.getEditText().getText().toString().trim();

            if (!TextUtils.isEmpty(password)) {
                // Create email/password credential
                AuthCredential emailPasswordCredential =
                        EmailAuthProvider.getCredential(user.getEmail(), password);

                // Link the credentials
                linkWithEmailPassword(user, emailPasswordCredential);
            } else {
                navigateToMainActivity();
            }
        } else {
            navigateToMainActivity();
        }
    }

    private void handleAccountCollision() {
        // Show a dialog explaining the situation to the user
        new MaterialAlertDialogBuilder(this)
                .setTitle("Account Already Exists")
                .setMessage("An account with this email already exists. You need to sign in with your password first to link it with Google.")
                .setPositiveButton("OK", (dialog, which) -> {
                    // Focus on password field
                    if (textInputPassword.getEditText() != null) {
                        textInputPassword.getEditText().requestFocus();
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void linkWithGoogle(FirebaseUser user, AuthCredential googleCredential) {
        user.linkWithCredential(googleCredential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(AuthActivity.this,
                                "Google account linked successfully! You can now sign in with either method.",
                                Toast.LENGTH_LONG).show();
                        navigateToMainActivity();
                    } else {
                        Toast.makeText(AuthActivity.this,
                                "Failed to link Google account: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                        navigateToMainActivity(); // Still navigate as user is signed in
                    }
                });
    }

    private void linkWithEmailPassword(FirebaseUser user, AuthCredential emailPasswordCredential) {
        user.linkWithCredential(emailPasswordCredential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(AuthActivity.this,
                                "Email/password auth linked successfully! You can now sign in with either method.",
                                Toast.LENGTH_LONG).show();
                        navigateToMainActivity();
                    } else {
                        Toast.makeText(AuthActivity.this,
                                "Failed to link email/password: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                        navigateToMainActivity(); // Still navigate as user is signed in
                    }
                });
    }

    private void navigateToMainActivity() {
        Log.d(TAG, "Navigating to MainActivity");
        Intent intent = new Intent(AuthActivity.this, MainActivity.class);
        startActivity(intent);
        // Add animation
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        finish();
    }

    /**
     * Check if any admin users already exist in the database
     */
    private void checkForExistingAdmins() {
        Log.d(TAG, "Checking for existing admin users");

        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean foundAdmin = false;

                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    try {
                        if (userSnapshot.child("role").getValue(String.class) != null &&
                                UserService.ROLE_ADMIN.equals(userSnapshot.child("role").getValue(String.class))) {
                            foundAdmin = true;
                            Log.d(TAG, "Found existing admin user: " + userSnapshot.getKey());
                            break;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error checking admin role: " + e.getMessage(), e);
                    }
                }

                adminExists = foundAdmin;
                adminsChecked = true;

                Log.d(TAG, "Admin check complete. Admin exists: " + adminExists);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Admin check failed: " + error.getMessage(), error.toException());
                // If there's an error, we'll assume no admin exists to be safe
                adminExists = false;
                adminsChecked = true;
            }
        });
    }

}