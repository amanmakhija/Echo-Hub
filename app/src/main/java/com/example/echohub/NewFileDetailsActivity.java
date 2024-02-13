package com.example.echohub;

import android.Manifest;

import static java.lang.String.format;

import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.IOException;

public class NewFileDetailsActivity extends AppCompatActivity {

    private FirebaseStorage storage;
    private FirebaseAuth mAuth;
    private FirebaseUser user;
    private static final String TAG = "FileDetailsActivity";
    private long downloadIdFromYourApp;
    private static final String CHANNEL_ID = "file_download_channel";
    private ProgressBar progressBar;
    private StorageReference fileRef;
    private String fileName;
    private static final int WRITE_EXTERNAL_STORAGE_REQUEST_CODE = 123;

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
        setContentView(R.layout.activity_new_file_details);

        storage = FirebaseStorage.getInstance();
        mAuth = FirebaseAuth.getInstance();
        user = mAuth.getCurrentUser();

        // Register the broadcast receiver
        registerReceiver(onDownloadComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED);

        // Set up references to UI elements
        TextView textViewFileName = findViewById(R.id.textViewFileName);
        TextView textViewFileSize = findViewById(R.id.textViewFileSize);
        ImageView imageViewFileType = findViewById(R.id.imageViewFileType);
        Button btnDownload = findViewById(R.id.btnDownload);
        progressBar = findViewById(R.id.progressBar);

        // Set text colors based on the current theme
        int textColor = isDarkTheme() ? getResources().getColor(R.color.textColorDark) : getResources().getColor(R.color.textColorLight);
        textViewFileName.setTextColor(textColor);
        textViewFileSize.setTextColor(textColor);
        imageViewFileType.setColorFilter(textColor);

        // Get intent data
        Intent intent = getIntent();
        fileName = intent.getStringExtra("fileName");

        // Create a storage reference from our app
        StorageReference storageRef = storage.getReference();

        // Show the progress bar
        progressBar.setVisibility(View.VISIBLE);

        assert fileName != null && user != null;
        fileRef = storageRef.child(user.getUid()).child(fileName);
        fileRef.getMetadata().addOnSuccessListener(storageMetadata -> {

            textViewFileName.setText(fileName);
            String fileSize = format("%.2f", (double) storageMetadata.getSizeBytes()/(1024*1024));
            textViewFileSize.setText(format("File size: %s MB", fileSize));

            // Hide the progress bar
            progressBar.setVisibility(View.GONE);
        }).addOnFailureListener(exception -> {
            Log.e(TAG, "Failed to fetch metadata "+exception.getMessage());
            progressBar.setVisibility(View.GONE);
        });

        // Set up the image view based on the file type (you can customize this logic)
        setImageBasedOnFileType(imageViewFileType, fileName);

        // Set up click listener for the Download button
        btnDownload.setOnClickListener(view -> {
            // Check if the app has write external storage permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                // Permission is not granted, request it
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        WRITE_EXTERNAL_STORAGE_REQUEST_CODE); // Use your defined request code
            } else {
                // Permission is granted, proceed with the download
                try {
                    downloadFile(fileRef, fileName);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    // Handle the result of the permission request
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == WRITE_EXTERNAL_STORAGE_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed with the download
                try {
                    downloadFile(fileRef, fileName);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                // Permission denied, show a message or take appropriate action
                showToast("Write external storage permission denied");
            }
        }
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
