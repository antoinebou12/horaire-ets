package me.imrashb.utils;

import java.text.Normalizer;

/**
 * Utility class for text normalization with minimal French support.
 * Provides accent folding and sigle normalization for search indexing.
 */
public class TextNormalization {

    /**
     * Folds French accents to their base letters (minimal French normalization).
     * Maps: é→e, è→e, ê→e, ë→e, à→a, â→a, ç→c, etc.
     * 
     * @param text Text to normalize
     * @return Text with accents removed
     */
    public static String foldAccents(String text) {
        if (text == null) {
            return null;
        }
        
        // Normalize to NFD (decomposed form: base + combining marks)
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        
        // Remove combining diacritical marks (accents)
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    /**
     * Normalizes a course code (sigle) by converting to uppercase and removing spaces.
     * 
     * @param sigle Course code to normalize
     * @return Normalized course code (uppercase, no spaces)
     */
    public static String normalizeSigle(String sigle) {
        if (sigle == null) {
            return null;
        }
        return sigle.toUpperCase().trim().replaceAll("\\s+", "");
    }

    /**
     * Normalizes text for indexing/searching by lowercasing and folding accents.
     * 
     * @param text Text to normalize
     * @return Lowercased text with accents folded
     */
    public static String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        return foldAccents(text.toLowerCase().trim());
    }
}
