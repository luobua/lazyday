package com.fan.lazyday.infrastructure.utils;

/**
 * @author chenbin
 */
public class FilenameUtils2 {
    private FilenameUtils2() {
        throw new IllegalStateException("Utility class");
    }

    public static String normalizeFilename(String filename) {
        filename = filename.replaceAll("^[# ]+", "");
        filename = filename.replaceAll("[# /\\\\]", "_");
        return filename;
    }
}
