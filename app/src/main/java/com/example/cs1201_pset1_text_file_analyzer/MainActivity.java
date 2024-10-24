package com.example.cs1201_pset1_text_file_analyzer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MainActivity extends AppCompatActivity {
    Button uploadButton;                // Upload button
    ProgressBar progressBar;            // Progress bar
    TextView progressTextIndicator;     // Progress bar text indicator

    ActivityResultLauncher<Intent> fileSelected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Bind variables to components on screen
        uploadButton = findViewById(R.id.uploadButton);
        progressBar = findViewById(R.id.progressBar);
        progressTextIndicator = findViewById(R.id.progressTextIndicator);

        // Hides progress bar and text indicator (show when file is selected)
        prepareScreen();

        fileSelected = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Uri uri = result.getData().getData();
                        String content = null;
                        try {
                            content = new String(Files.readAllBytes(Paths.get(uri.getPath())));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        System.out.println(content);
                    }
                });

        // Perform file selection when clicking on upload button
        uploadButton.setOnClickListener(view -> {
            Intent chooseFile = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            chooseFile.setType("*/*");
            chooseFile.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/plain", "application/pdf"});
            chooseFile.addCategory(Intent.CATEGORY_OPENABLE);
            chooseFile = Intent.createChooser(chooseFile, "Choose a file");
            fileSelected.launch(chooseFile);
        });
    }

    void prepareScreen() {
        progressBar.setProgress(0);
        progressBar.setVisibility(ProgressBar.INVISIBLE);
        progressTextIndicator.setVisibility(TextView.INVISIBLE);
    }
}