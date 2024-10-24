package com.example.cs1201_pset1_text_file_analyzer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
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

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class MainActivity extends AppCompatActivity {
    Button uploadButton;                // Upload button
    ProgressBar progressBar;            // Progress bar

    ActivityResultLauncher<Intent> fileSelected;
    String filename, contents;

    @SuppressLint("Range")
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

        // Hides progress bar and text indicator (show when file is selected)
        prepareScreen();

        // file selected listener
        // https://developer.android.com/training/data-storage/shared/documents-files
        fileSelected = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        // https://stackoverflow.com/questions/29763405/android-get-text-from-pdf
                        Uri uri = result.getData().getData();
                        String content = null;
                        try {
                            // https://developer.android.com/training/secure-file-sharing/retrieve-info#java
                            if (getContentResolver().getType(uri).contains("pdf")) { // PDF
                                PdfReader reader = new PdfReader(getContentResolver().openInputStream(uri));
                                int n = reader.getNumberOfPages();
                                for (int i = 0; i < n; i++) {
                                    content += PdfTextExtractor.getTextFromPage(reader, i + 1).trim() + "\n";
                                }
                                reader.close();
                            } else { // plain-text file
                                content = new String(getContentResolver().openInputStream(uri).readAllBytes());
                            }

                            // https://developer.android.com/training/secure-file-sharing/retrieve-info
                            Cursor data = getContentResolver().query(uri, null, null, null, null);
                            data.moveToFirst();
                            filename = data.getString(data.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        contents = content;
                        countUnplugged();
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
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(ProgressBar.INVISIBLE);
    }

    void showLoading() {
        progressBar.setVisibility(ProgressBar.VISIBLE);
    }

    public void countUnplugged() {
        showLoading();

        // Adapted from CountingPlugged source code
        // Read text file and replace all new lines, punctuation, and repeated spaces with a single space.
        String[] file = contents
                .replaceAll("[\r\n”“?!,.\"]", " ")
                .replaceAll(" ['] ", " ")
                .replaceAll("([ ]+)", " ")
                .split(" ");

        // Store an ArrayList of Word objects that represents distinct words and their number of occurrences
        ArrayList<Word> wordFrequencies = new ArrayList<>();
        ArrayList<String> wordsList = new ArrayList<>(Arrays.asList(file));

        // Note this section is relatively inefficient, optimizations can be made
        // to make running this function on text files such as Orwell's 1984 faster.
        // Loop through words from file and add to distinct words occurrence array
        while (!wordsList.isEmpty()) {
            String word = wordsList.get(0);
            wordFrequencies.add(new Word(word, Collections.frequency(wordsList, word)));
            wordsList.removeAll(Collections.singleton(word));
        }

        // Sort ArrayList of Word objects, using compareTo function from the Word class
        Collections.sort(wordFrequencies);

        prepareScreen();
//
//        if (singleWord) {
//            displayText.setText("Most common word in “" + filename + "” is ");
//            information.setText("“" + wordFrequencies.get(0).word + "” with " + wordFrequencies.get(0).count + " occurrences.");
//        } else {
//            displayText.setText("Top 5 common words in “" + filename + "” are ");
//            information.setText("");
//            for (int i = 0; i < 5; i++) {
//                information.append((i + 1) + ". “" + wordFrequencies.get(i).word + "” with " + wordFrequencies.get(i).count + " occurrences.\n");
//            }
//        }
    }
}