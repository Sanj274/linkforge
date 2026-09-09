package com.linkforge.urlshortener.dto;

import java.util.ArrayList;
import java.util.List;

public class ImportResult {

    private int created = 0;
    private int skipped = 0;
    private final List<String> errors = new ArrayList<>();

    public void incrementCreated() {
        created++;
    }

    public void incrementSkipped() {
        skipped++;
    }

    public void addError(int rowNumber, String message) {
        errors.add("Row " + rowNumber + ": " + message);
    }

    public int getCreated() {
        return created;
    }

    public int getSkipped() {
        return skipped;
    }

    public List<String> getErrors() {
        return errors;
    }
}
