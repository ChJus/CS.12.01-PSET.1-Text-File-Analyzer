package com.example.cs1201_pset1_text_file_analyzer;

// Class that stores a word pair, which contains the word and the number of occurrences
// Implement comparable to override implementation of compareTo() method to compare
// words first by their occurrence count, then their lexicographical order
public class Word implements Comparable<Word> {
    String word;
    int count;

    // Initialize word object with string of word and count of 1.
    public Word(String word) {
        this.word = word;
        count = 1;
    }

    public Word(String word, int count) {
        this.word = word;
        this.count = count;
    }

    // This method is used by the contains() method in ArrayList
    // We simply set all word objects with the same string word
    // to be equal.
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Word) {
            return this.word.equals(((Word) obj).word);
        } else {
            return false;
        }
    }

    // If the occurrence count is equal, then compare by lexicographic order.
    // Otherwise, compare by occurrence count. For sorting by descending order, returns:
    // 1: if this word's count is less than the given word
    // -1 if this word's count is greater than the given word
    @Override
    public int compareTo(Word o) {
        if (o.count == this.count) {
            return this.word.compareTo(o.word);
        } else {
            if (this.count < o.count) return 1;
            else return -1;
        }
    }

    // Overriding for better printing view
    @Override
    public String toString() {
        return "{" + word + ": " + count + "}";
    }
}