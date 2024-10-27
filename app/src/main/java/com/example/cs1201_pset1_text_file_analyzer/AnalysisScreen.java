package com.example.cs1201_pset1_text_file_analyzer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ListView;
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
import com.itextpdf.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.util.Random;

public class AnalysisScreen extends AppCompatActivity {
    // Statistics screen components
    Random random = new Random();       // Global RNG
    ImageButton saveButton, deleteButton;    // Save, delete buttons
    TextView filenameText;              // Filename label/title
    GridView generalDataView;           // General statistics (sentence, word, unique word count)
    ListView uniqueWordsList;           // List of unique words and number of occurrences
    Spinner temperatureSpinner;         // Temperature selection dropdown
    TextView temperatureParagraph;      // Temperature paragraph textbox
    Spinner nGramSpinner;               // N-gram selection dropdown
    TextView nGramParagraph;            // N-gram paragraph textbox

    ActivityResultLauncher<Intent> saveData = // save to PDF listener
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            Uri uri = result.getData().getData();
                            try {
                                // Effectively screenshots application screen and saves to a PDF.
                                Document document = new Document();

                                // https://stackoverflow.com/questions/7200535/how-to-convert-views-to-bitmaps
                                // https://stackoverflow.com/questions/29730402/how-to-convert-android-view-to-pdf
                                // Convert screen view to bitmap (image)
                                View v1 = findViewById(R.id.analysisScreen).getRootView();
                                v1.setDrawingCacheEnabled(true);
                                Bitmap screen = Bitmap.createBitmap(v1.getDrawingCache());
                                v1.setDrawingCacheEnabled(false);

                                // Set PDF dimensions to be same as bitmap image
                                document.setPageSize(
                                        new Rectangle(screen.getWidth() + document.leftMargin() + document.rightMargin(),
                                                screen.getHeight() + document.topMargin() + document.bottomMargin()));

                                PdfWriter.getInstance(document, getContentResolver().openOutputStream(uri));
                                document.open();
                                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                                screen.compress(Bitmap.CompressFormat.PNG, 100, stream);
                                byte[] byteArray = stream.toByteArray();

                                // Write image to PDF
                                addImage(document, byteArray, new Rectangle(screen.getWidth(), screen.getHeight()));

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
        setContentView(R.layout.analysis_screen);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.analysisScreen), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        showStatisticsScreen();
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
            filenameText.setText(MainActivity.filename);

            // Allow scrolling in textboxes
            temperatureParagraph.setMovementMethod(new ScrollingMovementMethod());
            nGramParagraph.setMovementMethod(new ScrollingMovementMethod());

            // Display colorful statistics/text
            Spanned[] values = {Html.fromHtml("<font color=#3772F1>" + MainActivity.wordsList.size() + "</font><font color=#000000> words</font>"),
                    Html.fromHtml("<font color=#3772F1>" + MainActivity.sentenceCount + "</font><font color=#000000> sentences</font>"),
                    Html.fromHtml("<font color=#3772F1>" + MainActivity.wordFrequencies.size() + "</font><font color=#000000>  unique words</font>")};
            ArrayAdapter<Spanned> generalStatsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, values);
            generalDataView.setAdapter(generalStatsAdapter);

            // https://stackoverflow.com/questions/16062569/how-to-construct-and-display-the-info-in-simple-list-item-2
            // Displays unique words and occurrence count
            ArrayAdapter<Word> uniqueWordsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_2, android.R.id.text1, MainActivity.wordFrequencies.subList(0, 100)) {
                public View getView(int position, View convertView, ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    TextView text1 = view.findViewById(android.R.id.text1);
                    TextView text2 = view.findViewById(android.R.id.text2);
                    text1.setText(MainActivity.wordFrequencies.get(position).word);
                    text2.setText(MainActivity.wordFrequencies.get(position).count + " occurrences");
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

                        // Randomly select a word from the list of words and check to see if
                        // the selected word is within the top temperature% of words by
                        // frequency. Add to paragraph when such a word is found and continue.
                        do {
                            index = random.nextInt(MainActivity.wordsList.size());
                        } while (((double) MainActivity.wordFrequencies.indexOf(new Word(MainActivity.wordsList.get(index), 0)) / MainActivity.wordFrequencies.size()) > temperature);
                        paragraph += MainActivity.wordsList.get(index).toLowerCase().trim() + " ";
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
                    String text = MainActivity.contents
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
                startActivity(new Intent(this, MainActivity.class));
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

    private static void addImage(Document document, byte[] byteArray, Rectangle dimension) {
        try {
            Image image = Image.getInstance(byteArray);
            image.scaleToFit(dimension);
            document.add(image);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
