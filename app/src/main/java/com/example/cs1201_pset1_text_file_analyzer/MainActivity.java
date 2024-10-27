package com.example.cs1201_pset1_text_file_analyzer;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Button;
import android.widget.ProgressBar;

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
    static String filename, contents;          // Stores filename and words in file
    static ArrayList<Word> wordFrequencies;    // Stores list of unique words by frequency
    static ArrayList<String> wordsList;        // Stores list of all words in file
    static int sentenceCount;

    // Upload screen components
    Button uploadButton;                // Upload button
    ProgressBar progressBar;            // Progress bar

    // Separate thread to perform count unplugged operations
    // I separate it into another thread to run so that the file chooser dialog can close after
    // executing code that reads contents of the file.
    // This way, users are not stuck on the file chooser dialog, they are shown
    // back to the upload page with the progress bar visible.
    // Note that threads can only run a single time, so after each execution, I reinitialize
    // the variable to a new thread performing the same action.
    Thread t = new Thread(() -> countUnplugged(contents));

    ActivityResultLauncher<Intent> fileSelected = // file selected listener
            // https://developer.android.com/training/data-storage/shared/documents-files
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            // https://stackoverflow.com/questions/29763405/android-get-text-from-pdf
                            // result.getData() returns Intent; Intent.getData() returns Uri
                            Uri uri = result.getData().getData();
                            String content = null;
                            try {
                                // https://developer.android.com/training/secure-file-sharing/retrieve-info#java
                                if (getContentResolver().getType(uri).contains("pdf")) { // PDF
                                    // Assembles text content from PDF by going page-by-page
                                    PdfReader reader = new PdfReader(getContentResolver().openInputStream(uri));
                                    int n = reader.getNumberOfPages();
                                    for (int i = 0; i < n; i++) {
                                        content += PdfTextExtractor.getTextFromPage(reader, i + 1).trim() + "\n";
                                    }
                                    reader.close();
                                } else { // plain-text file
                                    // Reads plain-text file
                                    content = new String(getContentResolver().openInputStream(uri).readAllBytes());
                                }

                                // https://developer.android.com/training/secure-file-sharing/retrieve-info
                                // Gets filename information
                                Cursor data = getContentResolver().query(uri, null, null, null, null);
                                data.moveToFirst();
                                filename = data.getString(Math.max(data.getColumnIndex(OpenableColumns.DISPLAY_NAME), 0));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            contents = content;
                            showLoading(); // Show loading progress bar
                            t.start();     // Start text analysis in separate thread (to exit out of file selector dialog)
                            t = new Thread(() -> countUnplugged(contents));
                        }
                    });

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

        // Show upload screen initially on opening application
        showUploadScreen();
    }

    // Hides progress bar after completing processing
    void prepareScreen() {
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(ProgressBar.INVISIBLE);
    }

    // Shows application is running by giving visual progress bar indicator.
    void showLoading() {
        progressBar.setVisibility(ProgressBar.VISIBLE);
    }

    public void countUnplugged(String contents) {
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
        ArrayList<String> clone = new ArrayList<>(wordsList);

        while (!clone.isEmpty()) {
            String word = clone.get(0);
            wordFrequencies.add(new Word(word, Collections.frequency(clone, word)));
            clone.removeAll(Collections.singleton(word));
        }

        // Sort ArrayList of Word objects, using compareTo function from the Word class
        Collections.sort(wordFrequencies);
        this.wordFrequencies = wordFrequencies;
        this.wordsList = wordsList;
        this.sentenceCount = contents.length() - contents
                .replace(".", "")
                .replace("?", "")
                .replace("!", "")
                .length();

        // Hide progress bar after finish
        runOnUiThread(this::prepareScreen);

        // Change view
        startActivity(new Intent(MainActivity.this, AnalysisScreen.class));
    }

    public void showUploadScreen() {
        setContentView(R.layout.activity_main);

        // Bind variables to components on screen
        uploadButton = findViewById(R.id.uploadButton);
        progressBar = findViewById(R.id.progressBar);

        // Hides progress bar and text indicator (show when file is selected)
        prepareScreen();

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
}