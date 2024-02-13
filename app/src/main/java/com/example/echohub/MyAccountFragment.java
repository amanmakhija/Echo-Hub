package com.example.echohub;

import static android.content.ContentValues.TAG;
import static java.lang.String.format;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Objects;

public class MyAccountFragment extends Fragment {

    private TextView textViewUsername, textViewUserEmail, textViewIsEmailVerified;
    private ImageView verifiedEmail, editUsername, editEmail;
    private TextView verifyEmail;
    private FirebaseAuth mAuth;
    private FirebaseUser user;
    private FirebaseFirestore db;
    private ProgressBar progressBar;
    private DocumentReference docRef;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_my_account, container, false);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        user = mAuth.getCurrentUser();

        textViewUsername = view.findViewById(R.id.textViewUsername);
        textViewUserEmail = view.findViewById(R.id.textViewUserEmail);
        textViewIsEmailVerified = view.findViewById(R.id.textViewIsEmailVerified);
        Button btnLogout = view.findViewById(R.id.btnLogout);
        Button btnDeleteAccount = view.findViewById(R.id.btnDeleteAccount);
        Button btnResetPassword = view.findViewById(R.id.btnResetPassword);
        progressBar = view.findViewById(R.id.progressBar);
        verifiedEmail = view.findViewById(R.id.verifiedEmail);
        verifyEmail = view.findViewById(R.id.verifyEmail);
        editUsername = view.findViewById(R.id.edit_username);
        editEmail = view.findViewById(R.id.edit_email);

        updateUserInfo();

        btnLogout.setOnClickListener(v -> logoutUser());
        btnDeleteAccount.setOnClickListener(v -> deleteAccount());
        btnResetPassword.setOnClickListener(v -> resetPassword());
        verifyEmail.setOnClickListener(v -> verifyEmail());

        return view;
    }

    private void updateUserInfo() {
        progressBar.setVisibility(View.VISIBLE);
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            String email = user.getEmail();
            boolean isEmailVerified = user.isEmailVerified();

            db.collection("users")
                    .whereEqualTo("userEmail", email)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                // Set the uploader information based on the retrieved user data
                                textViewUsername.setText(format("Username: %s", document.getString("userName")));
                                docRef = document.getReference();

                                // Hide the progress bar
                                progressBar.setVisibility(View.GONE);
                            }

                        } else {
                            Log.e(TAG, "Error getting user documents: ", task.getException());
                        }
                    });

            textViewUserEmail.setText(format("Email: %s" , email));
            textViewIsEmailVerified.setText(format("Email Verified: %s" , isEmailVerified?"Verified":"Not Verified"));

            // Set text colors based on the current theme
            int textColor = isDarkTheme() ? getResources().getColor(R.color.textColorDark) : getResources().getColor(R.color.textColorLight);
            textViewUsername.setTextColor(textColor);
            textViewUserEmail.setTextColor(textColor);
            textViewIsEmailVerified.setTextColor(textColor);
            editEmail.setColorFilter(textColor);
            editUsername.setColorFilter(textColor);

            if (isEmailVerified) {
                verifiedEmail.setVisibility(View.VISIBLE);
                verifyEmail.setVisibility(View.GONE);
            }
            else {
                verifyEmail.setVisibility(View.VISIBLE);
                verifiedEmail.setVisibility(View.GONE);
            }
        }
    }

    private boolean isDarkTheme() {
        int nightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private void verifyEmail() {
        if (user!=null) {
            user.sendEmailVerification().addOnCompleteListener(task -> {
                if (task.isSuccessful()) showToast("Verification email sent");
                else {
                    showToast("Error while sending email. Please try again");
                    Log.e("Verify Email", String.valueOf(task.getException()));
                }

            });
        }
    }

    private void logoutUser() {
        mAuth.signOut();
        // Navigate to the login page or perform any other required action
        Intent i = new Intent(getContext(), LoginActivity.class);
        startActivity(i);
        requireActivity().finish();
    }

    private void deleteAccount() {
        if (user != null) {
            user.delete()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // Account deleted successfully
                            docRef.delete();
                            Intent i = new Intent(getContext(), LoginActivity.class);
                            startActivity(i);
                            requireActivity().finish();
                        } else {
                            // Handle errors
                            showToast("Failed to delete account: " + task.getException());
                        }
                    });
        }
    }

    private void resetPassword() {

        if (user != null) {
            mAuth.sendPasswordResetEmail(Objects.requireNonNull(user.getEmail()))
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // Reset password email sent successfully
                            showToast("Password reset email sent");
                        } else {
                            // Handle errors
                            showToast("Failed to send password reset email: " + task.getException());
                        }
                    });
        }
    }

    private void showToast(String s) {
        Toast.makeText(requireContext(), s, Toast.LENGTH_SHORT).show();
    }
}
