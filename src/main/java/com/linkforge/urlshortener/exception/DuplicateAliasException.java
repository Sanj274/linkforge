package com.linkforge.urlshortener.exception;

public class DuplicateAliasException extends RuntimeException {
    public DuplicateAliasException(String alias) {
        super("The alias '" + alias + "' is already taken. Please choose another one.");
    }
}
