package com.example.cs1201_pset1_text_file_analyzer;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Html;
import android.text.Spanned;
import android.text.method.ScrollingMovementMethod;
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

import com.itextpdf.text.Document;
import com.itextpdf.text.Image;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

public class MainActivity extends AppCompatActivity {
    Random random = new Random();       // Global RNG
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
    Spinner nGramSpinner;               // N-gram selection dropdown
    TextView nGramParagraph;            // N-gram paragraph textbox

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

    ActivityResultLauncher<Intent> saveData = // save to PDF listener
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            Uri uri = result.getData().getData();
                            try {
                                Document document = new Document();

                                // https://stackoverflow.com/questions/7200535/how-to-convert-views-to-bitmaps
                                // Convert screen view to bitmap (image)
                                View v1 = findViewById(R.id.analysisScreen).getRootView();
                                v1.setDrawingCacheEnabled(true);
                                Bitmap screen = Bitmap.createBitmap(v1.getDrawingCache());
                                v1.setDrawingCacheEnabled(false);

                                // Set PDF dimensions to be same as bitmap image
                                document.setPageSize(new Rectangle(screen.getWidth(), screen.getHeight()));

                                PdfWriter.getInstance(document, getContentResolver().openOutputStream(uri));
                                document.open();
                                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                                screen.compress(Bitmap.CompressFormat.PNG, 100, stream);
                                byte[] byteArray = stream.toByteArray();

                                // Write image to PDF
                                addImage(document, byteArray);
                                document.close();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
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

            // Bind to visible components
            saveButton = findViewById(R.id.saveButton);
            deleteButton = findViewById(R.id.deleteButton);
            filenameText = findViewById(R.id.filenameText);
            generalDataView = findViewById(R.id.generalDataView);
            uniqueWordsList = findViewById(R.id.uniqueWordsList);
            temperatureSpinner = findViewById(R.id.temperatureSpinner);
            temperatureParagraph = findViewById(R.id.temperatureParagraph);
            nGramSpinner = findViewById(R.id.nGramSpinner);
            nGramParagraph = findViewById(R.id.nGramParagraph);

            // Display filename
            filenameText.setText(filename);

            // Allow scrolling in textboxes
            temperatureParagraph.setMovementMethod(new ScrollingMovementMethod());
            nGramParagraph.setMovementMethod(new ScrollingMovementMethod());

            // Display colorful statistics/text
            Spanned[] values = {Html.fromHtml("<font color=#3772F1>" + wordsList.size() + "</font><font color=#000000> words</font>"),
                    Html.fromHtml("<font color=#3772F1>" + sentenceCount + "</font><font color=#000000> sentences</font>"),
                    Html.fromHtml("<font color=#3772F1>" + wordFrequencies.size() + "</font><font color=#000000>  unique words</font>")};
            ArrayAdapter<Spanned> generalStatsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, values);
            generalDataView.setAdapter(generalStatsAdapter);

            // https://stackoverflow.com/questions/16062569/how-to-construct-and-display-the-info-in-simple-list-item-2
            // Displays unique words and occurrence count
            ArrayAdapter<Word> uniqueWordsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_2, android.R.id.text1, wordFrequencies.subList(0, 100)) {
                public View getView(int position, View convertView, ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    TextView text1 = view.findViewById(android.R.id.text1);
                    TextView text2 = view.findViewById(android.R.id.text2);
                    text1.setText(wordFrequencies.get(position).word);
                    text2.setText(wordFrequencies.get(position).count + " occurrences");
                    return view;
                }
            };
            uniqueWordsList.setAdapter(uniqueWordsAdapter);

            // Temperature paragraph spinner values
            String[] tempValues = new String[]{
                    "0.1", "0.2", "0.3", "0.4", "0.5", "0.6", "0.7", "0.8", "0.9", "1.0"
            };

            // Initialize spinner with options
            ArrayAdapter<String> temperatureSpinnerAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, tempValues);

            temperatureSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            temperatureSpinner.setAdapter(temperatureSpinnerAdapter);

            // Add listener on selecting item
            temperatureSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                    // Get temperature from the 0-indexed position of selected item in the dropdown.
                    double temperature = Double.parseDouble(tempValues[pos]);
                    String paragraph = "";
                    for (int i = 0; i < 200; i++) {
                        int index;
                        do {
                            index = random.nextInt(wordsList.size());
                        } while (((double) wordFrequencies.indexOf(new Word(wordsList.get(index), 0)) / wordFrequencies.size()) > temperature);
                        paragraph += wordsList.get(index).toLowerCase().trim() + " ";
                    }
                    temperatureParagraph.setText(paragraph);
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {
                    // Do nothing.
                }
            });

            // N-gram spinner options
            String[] nGramValues = new String[]{
                    "2", "3", "4", "5", "6", "7", "8"
            };

            // Create spinner with N-gram options
            ArrayAdapter<String> nGramSpinnerAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, nGramValues);

            nGramSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            nGramSpinner.setAdapter(nGramSpinnerAdapter);

            // Add listener on item selected
            nGramSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                    // Get n-gram from the 0-indexed position of selected item in the dropdown.
                    int n = Integer.parseInt(nGramValues[pos]);
                    StringBuilder paragraph = new StringBuilder();

                    // Clean content of punctuation
                    String text = contents
                            .replaceAll("[\r\n”“?!,.\"()]", " ")
                            .replaceAll(" ['] ", " ")
                            .replaceAll("([ ]+)", " ")
                            .toLowerCase();

                    String[] words = text.split(" ");
                    String phrase = "";
                    int count = 0;
                    do {
                        // Check if N-gram is empty or if there aren't any words following phrase
                        // If so, generate a new N-gram phrase.
                        // Note the regex removes the first word from the N-gram
                        if ((phrase.isEmpty()) ||
                                text.split(phrase.replaceFirst("^\\S+ ", "")).length == 1) {
                            phrase = "";
                            int randVal = random.nextInt(words.length - n);
                            for (int i = 0; i < n; i++) {
                                phrase += (words[randVal++]) + " ";
                            }
                        } else {
                            // Remove first word from N-gram
                            phrase = phrase.replaceFirst("^\\S+ ", "");

                            // Find all occurrences of new phrase in text
                            String[] segments = text.split(phrase);

                            // Select a random word from possible choices.
                            String addedWord = segments[random.nextInt(segments.length - 1) + 1].split(" ")[0].trim();

                            // Add new word to N-gram and to paragraph
                            phrase += addedWord + " ";
                            paragraph.append(addedWord).append(" ");
                            count++; // Increment generated words count
                        }
                    } while (count < 100);
                    nGramParagraph.setText(paragraph.toString()); // Update interface with paragraph
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {
                    // Do nothing.
                }
            });

            // Set listener on clicking save button
            saveButton.setOnClickListener(view -> {
                createFile();
            });

            // Set listener on clicking clear data button
            deleteButton.setOnClickListener(view -> {
                showUploadScreen();
            });
        });
    }

    // https://developer.android.com/training/data-storage/shared/documents-files
    // Opens file selector menu so user can choose where to save PDF file.
    private void createFile() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_TITLE, "data.pdf");

        saveData.launch(intent);
    }

    private static void addImage(Document document, byte[] byteArray) {
        try {
            Image image = Image.getInstance(byteArray);
            image.scaleToFit(document.getPageSize());
            document.add(image);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}