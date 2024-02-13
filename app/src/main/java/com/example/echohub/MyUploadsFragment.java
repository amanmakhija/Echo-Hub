package com.example.echohub;

import static android.content.ContentValues.TAG;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.Objects;

public class MyUploadsFragment extends Fragment {

    private LinearLayout linearFiles;
    private SwipeRefreshLayout swipeRefreshLayout;
    private FloatingActionButton fabUpload;
    private Uri selectedFileUri;
    private FirebaseAuth mAuth;
    private FirebaseUser user;
    private FirebaseStorage storage;
    private Button btnChooseFile;
    private ProgressBar progressBar;

    public MyUploadsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_my_uploads, container, false);

        mAuth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();
        user = mAuth.getCurrentUser();

        linearFiles = view.findViewById(R.id.linearFiles);
        fabUpload = view.findViewById(R.id.fabUpload);
        progressBar = view.findViewById(R.id.progressBar);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);

        fabUpload.setOnClickListener(v -> showUploadFileDialog());

        if (swipeRefreshLayout != null) {
            // Trigger the refresh
            swipeRefreshLayout.setOnRefreshListener(this::refreshFiles);
            refreshFiles();
        }

        return view;
    }

    private boolean isDarkTheme() {
        int nightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    // Method to add a file to the LinearLayout
    private void addFile(String fileName) {
        // Check if the fragment is attached to a context
        if (getContext() == null) {
            // Fragment is not attached, handle accordingly (e.g., log or return)
            return;
        }

        // Inflate the custom layout file
        LinearLayout layout = (LinearLayout) LayoutInflater.from(requireContext()).inflate(R.layout.file_listings, null);

        // Create an ImageView for the file icon
        ImageView imageView = layout.findViewById(R.id.imageViewFileType);
        setImageBasedOnFileType(imageView, fileName);

        // Create a TextView to display the file name
        TextView textView = layout.findViewById(R.id.textViewFileName);
        textView.setText(fileName);

        // Set text colors based on the current theme
        int textColor = isDarkTheme() ? getResources().getColor(R.color.textColorDark) : getResources().getColor(R.color.textColorLight);
        textView.setTextColor(textColor);
        imageView.setColorFilter(textColor);

        // Add the custom layout to the linear layout
        linearFiles.addView(layout);

        // Add some spacing between files (adjust as needed)
        linearFiles.addView(new Space(requireContext()), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 16));

        // Set up click listener for the file name
        layout.setOnClickListener(v -> openFileDetails(fileName));
    }

    private void setImageBasedOnFileType(ImageView imageView, String fileName) {
        if (fileName.contains("jpg") || fileName.contains("jpeg") || fileName.contains("png"))
            imageView.setImageResource(R.drawable.ic_image_file);
        else if(fileName.contains("pdf") || fileName.contains("docx"))
            imageView.setImageResource(R.drawable.ic_doc_file);
        else if (fileName.contains("zip") || fileName.contains("rar") || fileName.contains("tar"))
            imageView.setImageResource(R.drawable.ic_zip_file);
        else if (fileName.contains("exe"))
            imageView.setImageResource(R.drawable.ic_exe_file);
        else if (fileName.contains("apk"))
            imageView.setImageResource(R.drawable.ic_apk_file);
        else
            imageView.setImageResource(R.drawable.ic_file);
    }

    private void openFileDetails(String fileName) {
        // Create intent to open FileDetailsActivity
        Intent intent = new Intent(requireContext(), NewFileDetailsActivity.class);
        intent.putExtra("fileName", fileName);
        startActivity(intent);
    }

    private void uploadFileToStorage(Uri fileUri, String fileName) {
        if (!user.isEmailVerified()) showToast("Please verify your email first");
        else {
            StorageReference storageReference = storage.getReference().child(user.getUid());
            storageReference.listAll()
                    .addOnSuccessListener(listResult -> {
                        for (StorageReference item : listResult.getItems()) {
                            if (item.getName().equals(fileName)) {
                                showToast("File name should be unique");
                                return;
                            }
                        }
                    })
                    .addOnFailureListener(e -> Log.e(TAG, Objects.requireNonNull(e.getMessage())));

            if (fileUri != null) {
                // Show the progress bar
                progressBar.setVisibility(View.VISIBLE);

                String userId = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();

                // Create a reference to '[uid]/[file_name]'
                StorageReference storageRef = storage.getReference().child(userId).child(fileName);

                StorageMetadata metadata = new StorageMetadata.Builder()
                        .setCustomMetadata("userEmail", mAuth.getCurrentUser().getEmail())
                        .build();

                // Upload file to Firebase Storage
                UploadTask uploadTask = storageRef.putFile(fileUri, metadata);

                uploadTask.continueWithTask(task -> {
                    if (!task.isSuccessful()) throw Objects.requireNonNull(task.getException());

                    // Continue with the task to get the download URL
                    return storageRef.getDownloadUrl();
                }).addOnCompleteListener(task -> {
                    // Hide the progress bar
                    progressBar.setVisibility(View.GONE);

                    if (task.isSuccessful()) {
                        showToast("File uploaded successfully");
                        refreshFiles();
                    } else {
                        // Handle failures
                        showToast("File upload failed: " + Objects.requireNonNull(task.getException()).getMessage());
                    }
                });
            }
        }
    }

    private void showToast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    // Method to refresh the list of files
    private void refreshFiles() {
        if (linearFiles != null) {
            progressBar.setVisibility(View.VISIBLE);
            linearFiles.removeAllViews();

            // Get a reference to the root of your storage bucket
            StorageReference storageRef = storage.getReference();

            StorageReference uploadsRef = storageRef.child(user.getUid());

            // List all items (files) in the [uid] folder
            uploadsRef.listAll()
                    .addOnSuccessListener(listResult -> {
                        for (StorageReference item : listResult.getItems()) {
                            String fileName = item.getName();
                            addFile(fileName);
                        }
                        swipeRefreshLayout.setRefreshing(false);
                        progressBar.setVisibility(View.GONE);
                    })
                    .addOnFailureListener(e -> {
                        showToast("Error fetching files: " + e.getMessage());
                        swipeRefreshLayout.setRefreshing(false);
                        progressBar.setVisibility(View.GONE);
                    });
        }
    }

    private void showUploadFileDialog() {
        // Inflate the dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_upload_file_new, null);

        // Create the AlertDialog
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setView(dialogView);

        // Set up references to UI elements in the dialog
        EditText editTextFileName = dialogView.findViewById(R.id.editTextFileName);
        Button btnUpload = dialogView.findViewById(R.id.btnUpload);
        btnChooseFile = dialogView.findViewById(R.id.btnChooseFile);

        // Create and show the dialog
        AlertDialog dialog = builder.create();
        dialog.show();

        // Set up click listener for the Choose File button
        btnChooseFile.setOnClickListener(v -> openFileChooser());

        // Set up click listener for the Upload button
        btnUpload.setOnClickListener(v -> {
            // Get values from the dialog
            String fileName = editTextFileName.getText().toString();

            // Dismiss the dialog
            dialog.dismiss();

            // Call your upload method with the additional parameters
            if (selectedFileUri != null) {
                uploadFileToStorage(selectedFileUri, fileName);
            } else {
                showToast("Please choose a file first");
            }
        });
    }

    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");  // All file types
        startActivityForResult(intent, 1);
    }

    // Override onActivityResult to get the selected file URI
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1 && resultCode == Activity.RESULT_OK && data != null) {
            // Get the selected file URI
            selectedFileUri = data.getData();
        }
    }
}