package com.example.echohub;

import static java.lang.String.format;
import static java.lang.String.valueOf;

import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import java.io.IOException;
import java.util.Objects;

public class FileDetailsActivity extends AppCompatActivity {

    private FirebaseStorage storage;
    private FirebaseFirestore db;
    private int downloadsLeft = 0;
    private static final String TAG = "FileDetailsActivity";
    private long downloadIdFromYourApp;
    private static final String CHANNEL_ID = "file_download_channel";
    private boolean isUnlimited;
    private ProgressBar progressBar;

    // Broadcast receiver for download completion
    private final BroadcastReceiver onDownloadComplete = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Handle the completion of the download here
            long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);

            // Check if the completed download matches the one initiated in your app
            if (downloadId == downloadIdFromYourApp) {
                // Perform actions upon download completion
                showToast("Download complete!");
            }
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_details);

        storage = FirebaseStorage.getInstance();
        db = FirebaseFirestore.getInstance();

        // Register the broadcast receiver
        registerReceiver(onDownloadComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED);

        // Set up references to UI elements
        TextView textViewFileName = findViewById(R.id.textViewFileName);
        TextView textViewUploader = findViewById(R.id.textViewUploader);
        TextView textViewAnonymous = findViewById(R.id.textViewAnonymous);
        TextView textViewDownloadsLeft = findViewById(R.id.textViewDownloadsLeft);
        TextView textViewFileSize = findViewById(R.id.textViewFileSize);
        ImageView imageViewFileType = findViewById(R.id.imageViewFileType);
        Button btnDownload = findViewById(R.id.btnDownload);
        progressBar = findViewById(R.id.progressBar);

        // Set text colors based on the current theme
        int textColor = isDarkTheme() ? getResources().getColor(R.color.textColorDark) : getResources().getColor(R.color.textColorLight);
        textViewFileName.setTextColor(textColor);
        textViewUploader.setTextColor(textColor);
        textViewAnonymous.setTextColor(textColor);
        textViewDownloadsLeft.setTextColor(textColor);
        textViewFileSize.setTextColor(textColor);
        imageViewFileType.setColorFilter(textColor);

        // Get intent data
        Intent intent = getIntent();
        final String fileName = intent.getStringExtra("fileName");

        // Create a storage reference from our app
        StorageReference storageRef = storage.getReference();

        // Show the progress bar
        progressBar.setVisibility(View.VISIBLE);

        assert fileName != null;
        StorageReference fileRef = storageRef.child("uploads").child(fileName);
        fileRef.getMetadata().addOnSuccessListener(storageMetadata -> {
            downloadsLeft = Integer.parseInt(Objects.requireNonNull(storageMetadata.getCustomMetadata("downloadLimit")));
            isUnlimited = Boolean.parseBoolean(storageMetadata.getCustomMetadata("isUnlimited"));

            textViewFileName.setText(fileName);
            textViewDownloadsLeft.setText(format("Downloads Left: %s", isUnlimited ? "Unlimited" : downloadsLeft));
            textViewAnonymous.setText(format("Anonymous: %s", Boolean.parseBoolean(storageMetadata.getCustomMetadata("isAnonymousUpload")) ? "Yes" : "No"));
            String fileSize = format("%.2f", (double) storageMetadata.getSizeBytes()/(1024*1024));
            textViewFileSize.setText(format("File size: %s MB", fileSize));

            db.collection("users")
                    .whereEqualTo("userEmail", storageMetadata.getCustomMetadata("userEmail"))
                    .get()
                    .addOnCompleteListener(task -> {
                        // Hide the progress bar
                        progressBar.setVisibility(View.GONE);

                        if (task.isSuccessful()) {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                // Set the uploader information based on the retrieved user data
                                textViewUploader.setText(format("Uploaded by: %s", !Boolean.parseBoolean(storageMetadata.getCustomMetadata("isAnonymousUpload")) ? document.getString("userName") : "ANONYMOUS"));
                            }
                        } else {
                            Log.e(TAG, "Error getting user documents: ", task.getException());
                        }
                    });
        }).addOnFailureListener(exception -> Log.e(TAG, "Failed to fetch metadata "+exception.getMessage()));

        // Set up the image view based on the file type (you can customize this logic)
        setImageBasedOnFileType(imageViewFileType, fileName);

        // Set up click listener for the Download button
        btnDownload.setOnClickListener(view -> {
            if (downloadsLeft<0 && !isUnlimited) showToast("Download limit reached");
            else {
                if (!isUnlimited) downloadsLeft -= 1;
                StorageMetadata metadata = new StorageMetadata.Builder()
                        .setCustomMetadata("downloadLimit", valueOf(downloadsLeft))
                        .build();
                fileRef.updateMetadata(metadata)
                        .addOnSuccessListener(storageMetadata -> {
                            // Updated metadata is in storageMetadata
                            try {
                                downloadFile(fileRef, fileName);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .addOnFailureListener(exception -> {
                            // Uh-oh, an error occurred!
                            Log.e(TAG, "Error updating document", exception);
                        });
            }
        });
    }

    private boolean isDarkTheme() {
        int nightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister the broadcast receiver to avoid memory leaks
        unregisterReceiver(onDownloadComplete);
    }

    private void downloadFile(StorageReference fileRef, String fileName) throws IOException {
        fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
            // Local temp file has been created
            showToast("File download started");

            // Create request for android download manager
            DownloadManager.Request request = new DownloadManager.Request(uri);

            // Set title and description
            request.setTitle(fileName);
            request.setDescription("Downloading");

            // Set destination directory
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            // Enqueue download and save into referenceId
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);

            // Store the downloadId so that you can match it in the broadcast receiver
            downloadIdFromYourApp = manager.enqueue(request);

            // Show a notification
            showDownloadNotification(fileName);
        }).addOnFailureListener(exception -> {
            // Handle any errors
            showToast("Error while getting download URL");
        });
    }

    private void showDownloadNotification(String fileName) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Create a notification channel for Android Oreo and above
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "File Download", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("File Download Notifications");
        channel.enableLights(true);
        channel.setLightColor(Color.BLUE);
        notificationManager.createNotificationChannel(channel);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle("File Downloaded Successfully")
                .setContentText(fileName + " has been downloaded successfully")
                .setPriority(NotificationCompat.PRIORITY_LOW);

        // Show the notification
        notificationManager.notify((int) downloadIdFromYourApp, builder.build());
    }

    private void setImageBasedOnFileType(ImageView imageView, String fileName) {
        if (fileName.contains("jpg") || fileName.contains("jpeg") || fileName.contains("png"))
            imageView.setImageResource(R.drawable.ic_image_file);
        else if(fileName.contains("pdf") || fileName.contains("docx"))
            imageView.setImageResource(R.drawable.ic_doc_file);
        else
            imageView.setImageResource(R.drawable.ic_file);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
