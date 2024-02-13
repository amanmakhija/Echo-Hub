package com.example.echohub;

import static android.content.ContentValues.TAG;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {
    private EditText editTextUsername, editTextPassword;
    private FirebaseAuth mAuth;
    private ProgressBar progressBarRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        progressBarRegister = findViewById(R.id.progressBarLogin);

        editTextUsername = findViewById(R.id.editTextEmail);
        editTextPassword = findViewById(R.id.editTextPassword);
        Button btnLogin = findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(view -> {
            // Show progress bar
            progressBarRegister.setVisibility(View.VISIBLE);

            // Handle login logic
            String email = editTextUsername.getText().toString();
            String password = editTextPassword.getText().toString();
            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(LoginActivity.this, task -> {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d(TAG, "signInWithEmail:success");
                            FirebaseUser user = mAuth.getCurrentUser();
                            updateUI(user);
                        } else {
                            // If sign in fails, display a message to the user.
                            Log.w(TAG, "signInWithEmail:failure", task.getException());
                            Toast.makeText(LoginActivity.this, "Authentication failed.",
                                    Toast.LENGTH_SHORT).show();
                            updateUI(null);
                        }
                        progressBarRegister.setVisibility(View.INVISIBLE);
                    });
        });

        TextView textViewRegister = findViewById(R.id.textViewRegister);
        textViewRegister.setOnClickListener(view -> transferUser());
    }

    private void transferUser() {
        startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        finish();
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

    private void updateUI(FirebaseUser user) {
        if (user != null) {
            // User is signed in, you can perform actions like navigating to the main activity
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            startActivity(intent);
            finish(); // Optional: Close the LoginActivity to prevent going back
        } else {
            // User is signed out or login failed, handle accordingly
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
