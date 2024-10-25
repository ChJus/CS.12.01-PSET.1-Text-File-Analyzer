package com.example.cs1201_pset1_text_file_analyzer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Html;
import android.text.Spanned;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
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
    String filename, contents;          // Stores filename and words in file
    ArrayList<Word> wordFrequencies;    // Stores list of unique words by frequency
    ArrayList<String> wordsList;        // Stores list of all words in file
    int sentenceCount;

    // Upload screen components
    Button uploadButton;                // Upload button
    ProgressBar progressBar;            // Progress bar

    // Statistics screen components
    ImageButton saveButton, deleteButton;    // Save, delete buttons
    TextView filenameText;              // Filename label/title
    GridView generalDataView;           // General statistics (sentence, word, unique word count)
    ListView uniqueWordsList;           // List of unique words and number of occurrences
    Spinner temperatureSpinner;         // Temperature selection dropdown
    TextView temperatureParagraph;      // Temperature paragraph textbox

    Thread t = new Thread(() -> countUnplugged(contents));

    @SuppressLint("Range")
    ActivityResultLauncher<Intent> fileSelected = // file selected listener
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
                            showLoading();
                            t.start();
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
        showUploadScreen();
    }

    void prepareScreen() {
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(ProgressBar.INVISIBLE);
    }

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

        runOnUiThread(this::prepareScreen);
        showStatisticsScreen();
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

    // https://stackoverflow.com/questions/5161951/android-only-the-original-thread-that-created-a-view-hierarchy-can-touch-its-vi
    public void showStatisticsScreen() {
        runOnUiThread(() -> {
            setContentView(R.layout.analysis_screen);
            saveButton = findViewById(R.id.saveButton);
            deleteButton = findViewById(R.id.deleteButton);
            filenameText = findViewById(R.id.filenameText);
            generalDataView = findViewById(R.id.generalDataView);
            uniqueWordsList = findViewById(R.id.uniqueWordsList);
            temperatureSpinner = findViewById(R.id.temperatureSpinner);
            temperatureParagraph = findViewById(R.id.temperatureParagraph);

            filenameText.setText(filename);

            Spanned[] values = {Html.fromHtml("<font color=#3772F1>" + wordsList.size() + "</font><font color=#000000> words</font>"),
                    Html.fromHtml("<font color=#3772F1>" + sentenceCount + "</font><font color=#000000> sentences</font>"),
                    Html.fromHtml("<font color=#3772F1>" + wordFrequencies.size() + "</font><font color=#000000>  unique words</font>")};
            ArrayAdapter<Spanned> generalStatsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, values);
            generalDataView.setAdapter(generalStatsAdapter);

            // https://stackoverflow.com/questions/16062569/how-to-construct-and-display-the-info-in-simple-list-item-2
            ArrayAdapter<Word> uniqueWordsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_2, android.R.id.text1, wordFrequencies.subList(0, 100)) {
                public View getView(int position, View convertView, ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    TextView text1 = view.findViewById(android.R.id.text1);
                    TextView text2 = view.findViewById(android.R.id.text2);
                    text1.setText(wordFrequencies.get(position).word);
                    text2.setText(wordFrequencies.get(position).count + " occurrences.");
                    return view;
                }
            };
            uniqueWordsList.setAdapter(uniqueWordsAdapter);

            String[] tempValues = new String[]{
                    "0.1", "0.2", "0.3", "0.4", "0.5", "0.6", "0.7", "0.8", "0.9", "1.0"
            };

            ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, tempValues);

            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            temperatureSpinner.setAdapter(spinnerAdapter);

            temperatureSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                    // Get temperature from the 0-indexed position of selected item in the dropdown.
                    double temperature = Double.parseDouble(tempValues[pos]);
                    // todo
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {
                    // Do nothing.
                }
            });
        });
    }
}