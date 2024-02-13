package com.example.echohub;

import static android.content.ContentValues.TAG;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private EditText editTextRegisterUsername, editTextRegisterPassword, editTextConfirmPassword, editTextRegisterEmail;
    private Button btnRegister;
    private ProgressBar progressBarRegister;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        editTextRegisterUsername = findViewById(R.id.editTextRegisterUsername);
        editTextRegisterEmail = findViewById(R.id.editTextRegisterEmail);
        editTextRegisterPassword = findViewById(R.id.editTextRegisterPassword);
        editTextConfirmPassword = findViewById(R.id.editTextConfirmPassword);
        btnRegister = findViewById(R.id.btnRegister);
        progressBarRegister = findViewById(R.id.progressBarRegister);

        btnRegister.setOnClickListener(view -> {
            // Show progress bar
            progressBarRegister.setVisibility(View.VISIBLE);

            // Handle registration logic
            String username = editTextRegisterUsername.getText().toString();
            String email = editTextRegisterEmail.getText().toString();
            String password = editTextRegisterPassword.getText().toString();
            String confirmPassword = editTextConfirmPassword.getText().toString();

            if(!password.equals(confirmPassword)) showToast("Confirm password not matched");
            else if (password.length()<8) showToast("Password should be least 8 characters long");
            else {
                mAuth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                            public void onComplete(@NonNull Task<AuthResult> task) {
                                // Hide progress bar when registration is complete

                                if (task.isSuccessful()) {
                                    // Sign in success, update UI with the signed-in user's information
                                    Log.d(TAG, "createUserWithEmail:success");
                                    FirebaseUser user = mAuth.getCurrentUser();

                                    // Create a map with file metadata
                                    // Modify the userData map to include new parameters
                                    Map<String, Object> userData = new HashMap<>();
                                    assert user != null;
                                    userData.put("userId", user.getUid());
                                    userData.put("userEmail", email);
                                    userData.put("userPassword", password);
                                    userData.put("userName", username);
                                    userData.put("profileUrl", "");
                                    userData.put("isEmailVerified", false);

                                    // Add the file metadata to Firestore
                                    db.collection("users")
                                            .add(userData)
                                            .addOnSuccessListener(documentReference -> {
                                                showToast("User Authenticated Successfully");
                                                updateUI(user);
                                            })
                                            .addOnFailureListener(e -> {
                                                showToast("User authentication failed " + e.getMessage());
                                                Log.e("Firestore", "Error adding user", e);
                                            });
                                } else {
                                    // If sign in fails, display a message to the user.
                                    Log.w(TAG, "createUserWithEmail:failure", task.getException());
                                    showToast("Authentication failed.");
                                    updateUI(null);
                                }
                                progressBarRegister.setVisibility(View.INVISIBLE);
                            }
                        });
            }
        });

        TextView textViewLogin = findViewById(R.id.textViewLogin);
        textViewLogin.setOnClickListener(view -> transferUser());
    }

    public void showToast(String s) {
        Toast.makeText(RegisterActivity.this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onStart() {
        super.onStart();
        // Check if user is signed in (non-null) and update UI accordingly.
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // The user is not signed in, handle accordingly
            // For example, you can redirect to the login activity
            updateUI(currentUser);
        }
    }

    private void transferUser() {
        startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
        finish();
    }

    private void updateUI(FirebaseUser user) {
        if (user != null) {
            // User is signed in, you can perform actions like navigating to the main activity
            Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
            startActivity(intent);
            finish(); // Optional: Close the LoginActivity to prevent going back
        } else {
            // User is signed out or login failed, handle accordingly
            Toast.makeText(RegisterActivity.this, "Please try again!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Any necessary cleanup or checks when the activity is stopped
        // Ensure that you are not signing out the user here
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Any necessary cleanup or checks when the activity is destroyed
        // Ensure that you are not signing out the user here
    }

}
