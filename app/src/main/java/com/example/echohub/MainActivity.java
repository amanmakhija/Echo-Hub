package com.example.echohub;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigationView);
        bottomNavigationView.setOnNavigationItemSelectedListener(navItemSelectedListener);

        // Set the initial fragment
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, new HomeFragment())
                .commit();
    }

    private final BottomNavigationView.OnNavigationItemSelectedListener navItemSelectedListener =
            item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.navigation_home) {
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragmentContainer, new HomeFragment())
                            .commit();
                    return true;
                } else if (itemId == R.id.navigation_profile) {
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragmentContainer, new MyAccountFragment())
                            .commit();
                    return true;
                } else if (itemId == R.id.navigation_my_uploads) {
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragmentContainer, new MyUploadsFragment())
                            .commit();
                    return true;
                }
                return false;
            };
}
