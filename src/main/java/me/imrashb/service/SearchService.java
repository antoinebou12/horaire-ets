package me.imrashb.service;

import me.imrashb.domain.CoursDataWrapper;
import me.imrashb.domain.CoursSearchResult;
import me.imrashb.domain.CoursAutocompleteResult;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchService {

    // BM25 tuning parameters
    private static final double K1 = 1.2;
    private static final double B = 0.75;

    // ========== Helper Methods ==========

    /**
     * Validates input parameters for search methods
     * @param courses List of courses to search through
     * @param query Search query
     * @return Optional containing trimmed query if valid, empty otherwise
     */
    private Optional<String> validateInput(List<CoursDataWrapper> courses, String query) {
        if (query == null || query.trim().isEmpty()) {
            return Optional.empty();
        }
        if (courses == null || courses.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(query.trim());
    }

    /**
     * Creates a CoursSearchResult from a course and score
     * @param course Course data wrapper
     * @param score Relevance score
     * @return Search result object
     */
    private CoursSearchResult createSearchResult(CoursDataWrapper course, double score) {
        return new CoursSearchResult(
                course.getSigle(),
                course.getTitre(),
                course.getDescription(),
                course.getCredits(),
                score
        );
    }

    /**
     * Gets score from result with null safety
     * @param result Search result
     * @return Score or 0.0 if null
     */
    private double getScoreOrZero(CoursSearchResult result) {
        return result.getScore() != null ? result.getScore() : 0.0;
    }

    /**
     * Gets score from autocomplete result with null safety
     * @param result Autocomplete result
     * @return Score or 0.0 if null
     */
    private double getScoreOrZero(CoursAutocompleteResult result) {
        return result.getScore() != null ? result.getScore() : 0.0;
    }

    /**
     * Sorts and limits search results by score (descending)
     * @param results List of search results
     * @param limit Maximum number of results
     * @return Sorted and limited list
     */
    private List<CoursSearchResult> sortAndLimitByScore(List<CoursSearchResult> results, int limit) {
        return results.stream()
                .sorted((a, b) -> Double.compare(getScoreOrZero(b), getScoreOrZero(a)))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Sorts and limits autocomplete results by score (descending)
     * @param results List of autocomplete results
     * @param limit Maximum number of results
     * @return Sorted and limited list
     */
    private List<CoursAutocompleteResult> sortAndLimitAutocompleteByScore(List<CoursAutocompleteResult> results, int limit) {
        return results.stream()
                .sorted((a, b) -> Double.compare(getScoreOrZero(b), getScoreOrZero(a)))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Normalizes query by trimming and converting to uppercase
     * @param query Query string
     * @return Normalized query
     */
    private String normalizeQuery(String query) {
        return query != null ? query.trim().toUpperCase() : "";
    }

    /**
     * Extracts words from text, filtering by minimum length
     * @param text Text to extract words from
     * @param minLength Minimum word length
     * @return List of words meeting minimum length requirement
     */
    private List<String> extractWords(String text, int minLength) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(text.split("\\s+"))
                .filter(word -> word.length() >= minLength)
                .collect(Collectors.toList());
    }

    /**
     * Performs BM25 search on courses
     * @param courses List of courses to search through
     * @param query Search query
     * @param limit Maximum number of results to return
     * @return List of search results sorted by relevance score (descending)
     */
    public List<CoursSearchResult> searchBM25(List<CoursDataWrapper> courses, String query, int limit) {
        Optional<String> validQuery = validateInput(courses, query);
        if (validQuery.isEmpty()) {
            return Collections.emptyList();
        }

        // Tokenize query
        List<String> queryTerms = tokenize(validQuery.get());

        if (queryTerms.isEmpty()) {
            return Collections.emptyList();
        }

        // Preprocess documents: tokenize and calculate document lengths
        List<DocumentData> documentData = courses.stream()
                .map(course -> {
                    String searchText = buildSearchText(course);
                    List<String> tokens = tokenize(searchText);
                    Map<String, Integer> termFrequencies = calculateTermFrequencies(tokens);
                    return new DocumentData(course, tokens.size(), termFrequencies);
                })
                .collect(Collectors.toList());

        // Calculate average document length
        double avgDocLength = documentData.stream()
                .mapToInt(doc -> doc.length)
                .average()
                .orElse(1.0);

        // Calculate IDF for each query term
        Map<String, Double> idfMap = calculateIDF(queryTerms, documentData);

        // Calculate BM25 scores
        List<CoursSearchResult> results = new ArrayList<>();
        for (DocumentData docData : documentData) {
            double score = calculateBM25Score(queryTerms, docData, avgDocLength, idfMap);
            if (score > 0) {
                results.add(createSearchResult(docData.course, score));
            }
        }

        // Sort by score descending and limit results
        return sortAndLimitByScore(results, limit);
    }

    /**
     * Builds searchable text from course (sigle + description + title)
     * Improved with better normalization and description boosting
     */
    private String buildSearchText(CoursDataWrapper course) {
        StringBuilder text = new StringBuilder();

        // Add sigle (weighted more heavily)
        if (course.getSigle() != null) {
            // Add sigle multiple times to give it more weight
            String sigle = normalizeText(course.getSigle());
            text.append(sigle).append(" ");
            text.append(sigle).append(" ");
            text.append(sigle).append(" ");
        }

        // Add description (2x weight for boosting)
        if (course.getDescription() != null) {
            String description = normalizeText(course.getDescription());
            // Add description twice to boost its importance
            text.append(description).append(" ");
            text.append(description).append(" ");
        }

        // Add title if available
        if (course.getTitre() != null) {
            text.append(normalizeText(course.getTitre())).append(" ");
        }

        return text.toString().trim();
    }

    /**
     * Normalizes text: removes HTML tags, normalizes whitespace, handles special characters
     * Preserves French accents and other Unicode characters
     */
    private String normalizeText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Convert to lowercase
        String normalized = text.toLowerCase();

        // Remove HTML tags if present
        normalized = normalized.replaceAll("<[^>]+>", "");

        // Remove HTML entities
        normalized = normalized.replaceAll("&[a-zA-Z]+;", " ");
        normalized = normalized.replaceAll("&#\\d+;", " ");

        // Normalize whitespace (multiple spaces/tabs/newlines to single space)
        normalized = normalized.replaceAll("\\s+", " ");

        // Trim
        normalized = normalized.trim();

        return normalized;
    }

    /**
     * Tokenizes text into terms (handles French accents and special characters)
     * Enhanced to preserve French accents and handle Unicode properly
     */
    private List<String> tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

        // Normalize text first (handles HTML, whitespace, etc.)
        String normalized = normalizeText(text);

        // Preserve letters (including accented), numbers, and spaces
        // \p{L} matches all Unicode letters (including é, è, ê, etc.)
        // \p{N} matches all Unicode numbers
        normalized = normalized.replaceAll("[^\\p{L}\\p{N}\\s]", " ");

        // Normalize whitespace again after character removal
        normalized = normalized.replaceAll("\\s+", " ").trim();

        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }

        // Split by whitespace and filter out empty strings
        return Arrays.stream(normalized.split("\\s+"))
                .filter(term -> term.length() > 0)
                .collect(Collectors.toList());
    }

    /**
     * Calculates term frequencies for a document
     */
    private Map<String, Integer> calculateTermFrequencies(List<String> tokens) {
        Map<String, Integer> frequencies = new HashMap<>();
        for (String token : tokens) {
            frequencies.put(token, frequencies.getOrDefault(token, 0) + 1);
        }
        return frequencies;
    }

    /**
     * Calculates Inverse Document Frequency (IDF) for query terms
     */
    private Map<String, Double> calculateIDF(List<String> queryTerms, List<DocumentData> documents) {
        Map<String, Double> idfMap = new HashMap<>();
        int totalDocuments = documents.size();

        for (String term : queryTerms) {
            long documentsWithTerm = documents.stream()
                    .filter(doc -> doc.termFrequencies.containsKey(term))
                    .count();

            if (documentsWithTerm > 0) {
                // IDF formula: log((N - n + 0.5) / (n + 0.5)) where N = total docs, n = docs with term
                double idf = Math.log((totalDocuments - documentsWithTerm + 0.5) / (documentsWithTerm + 0.5));
                idfMap.put(term, idf);
            } else {
                idfMap.put(term, 0.0);
            }
        }

        return idfMap;
    }

    /**
     * Calculates BM25 score for a document given a query
     */
    private double calculateBM25Score(List<String> queryTerms, DocumentData doc, double avgDocLength, Map<String, Double> idfMap) {
        double score = 0.0;

        for (String term : queryTerms) {
            double idf = idfMap.getOrDefault(term, 0.0);
            if (idf <= 0) continue;

            int termFreq = doc.termFrequencies.getOrDefault(term, 0);
            if (termFreq == 0) continue;

            // BM25 formula: IDF * (f * (k1 + 1)) / (f + k1 * (1 - b + b * (|D| / avgdl)))
            double numerator = termFreq * (K1 + 1);
            double denominator = termFreq + K1 * (1 - B + B * (doc.length / avgDocLength));
            score += idf * (numerator / denominator);
        }

        return score;
    }

    /**
     * Performs fuzzy search on courses using Levenshtein distance
     * Useful for finding courses even with typos or slight variations in spelling
     * 
     * @param courses List of courses to search through
     * @param query Search query
     * @param limit Maximum number of results to return
     * @param maxDistance Maximum allowed edit distance (default: 2 for short queries, 3 for longer)
     * @return List of search results sorted by similarity score (descending)
     */
    public List<CoursSearchResult> searchFuzzy(List<CoursDataWrapper> courses, String query, int limit, Integer maxDistance) {
        Optional<String> validQuery = validateInput(courses, query);
        if (validQuery.isEmpty()) {
            return Collections.emptyList();
        }

        String queryNormalized = normalizeQuery(validQuery.get());

        // Adaptive max distance based on query length
        int effectiveMaxDistance = maxDistance != null ? maxDistance : 
            (queryNormalized.length() <= 3 ? 1 : queryNormalized.length() <= 6 ? 2 : 3);

        // Calculate fuzzy scores for each course
        List<FuzzyMatch> matches = new ArrayList<>();

        for (CoursDataWrapper course : courses) {
            double bestScore = 0.0;

            // Check sigle (highest weight)
            if (course.getSigle() != null) {
                String sigleUpper = normalizeQuery(course.getSigle());
                double sigleScore = calculateFuzzyScore(queryNormalized, sigleUpper, effectiveMaxDistance);
                if (sigleScore > bestScore) {
                    bestScore = sigleScore;
                }
            }

            // Check titre (medium weight)
            if (course.getTitre() != null) {
                String titreUpper = normalizeQuery(course.getTitre());
                // Check if query matches any word in title
                List<String> titleWords = extractWords(titreUpper, 3);
                for (String word : titleWords) {
                    double wordScore = calculateFuzzyScore(queryNormalized, word, effectiveMaxDistance);
                    if (wordScore > bestScore * 0.8) { // Slightly lower threshold for title matches
                        bestScore = Math.max(bestScore, wordScore * 0.9); // 90% weight for title matches
                    }
                }
            }

            // Check description (lower weight, only if no good match found)
            if (bestScore < 0.5 && course.getDescription() != null) {
                String descUpper = normalizeQuery(course.getDescription());
                List<String> descWords = extractWords(descUpper, 4);
                for (String word : descWords) {
                    double wordScore = calculateFuzzyScore(queryNormalized, word, effectiveMaxDistance);
                    if (wordScore > bestScore) {
                        bestScore = wordScore * 0.7; // 70% weight for description matches
                    }
                }
            }

            // Only include matches above threshold
            if (bestScore > 0.0) {
                matches.add(new FuzzyMatch(course, bestScore));
            }
        }

        // Sort by score descending and limit results
        return matches.stream()
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(limit)
                .map(match -> createSearchResult(match.course, match.score))
                .collect(Collectors.toList());
    }

    /**
     * Calculate fuzzy similarity score using Levenshtein distance
     * Returns a score between 0.0 and 1.0, where 1.0 is an exact match
     * 
     * @param query The search query
     * @param target The target string to match against
     * @param maxDistance Maximum allowed edit distance
     * @return Similarity score (0.0 to 1.0)
     */
    private double calculateFuzzyScore(String query, String target, int maxDistance) {
        if (query == null || target == null) {
            return 0.0;
        }

        // Exact match
        if (query.equals(target)) {
            return 1.0;
        }

        // Prefix match (boosted)
        if (target.startsWith(query)) {
            return 0.95;
        }

        // Contains match (moderate boost)
        if (target.contains(query)) {
            return 0.85;
        }

        // Calculate Levenshtein distance
        int distance = levenshteinDistance(query, target);

        // If distance exceeds max, return 0
        if (distance > maxDistance) {
            return 0.0;
        }

        // Convert distance to similarity score
        // Score decreases as distance increases
        int maxLen = Math.max(query.length(), target.length());
        if (maxLen == 0) {
            return 1.0;
        }

        double similarity = 1.0 - ((double) distance / maxLen);

        // Apply penalty for longer strings (prefer shorter, more exact matches)
        if (target.length() > query.length() * 1.5) {
            similarity *= 0.9;
        }

        return Math.max(0.0, similarity);
    }

    /**
     * Calculate Levenshtein distance (edit distance) between two strings
     * Returns the minimum number of single-character edits needed to transform one string into another
     */
    private int levenshteinDistance(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return Integer.MAX_VALUE;
        }

        int len1 = s1.length();
        int len2 = s2.length();

        // Early exit for empty strings
        if (len1 == 0) return len2;
        if (len2 == 0) return len1;

        // Use dynamic programming to calculate edit distance
        int[][] dp = new int[len1 + 1][len2 + 1];

        // Initialize base cases
        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        // Fill the DP table
        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(
                        dp[i - 1][j] + 1,      // deletion
                        dp[i][j - 1] + 1       // insertion
                    ),
                    dp[i - 1][j - 1] + cost    // substitution
                );
            }
        }

        return dp[len1][len2];
    }

    /**
     * Determines if fuzzy search should be used based on query characteristics
     * @param query Search query
     * @return true if fuzzy search is recommended, false for BM25
     */
    private boolean shouldUseFuzzy(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }

        String trimmed = query.trim();
        int wordCount = trimmed.split("\\s+").length;

        // Use fuzzy for short queries (≤6 chars) or single-word queries
        return trimmed.length() <= 6 || wordCount == 1;
    }

    /**
     * Determines if query should use hybrid search (both algorithms)
     * @param query Search query
     * @return true if hybrid search is recommended
     */
    private boolean shouldUseHybrid(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }

        String trimmed = query.trim();
        int wordCount = trimmed.split("\\s+").length;

        // Use hybrid for medium-length queries (7-20 chars) with 2-3 words
        return trimmed.length() > 6 && trimmed.length() <= 20 && wordCount >= 2 && wordCount <= 3;
    }

    /**
     * Normalizes BM25 scores to 0-1 range using min-max normalization
     * @param results List of search results with BM25 scores
     * @return List with normalized scores
     */
    private List<CoursSearchResult> normalizeBM25Scores(List<CoursSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return results;
        }

        // Find min and max scores
        double minScore = results.stream()
                .mapToDouble(this::getScoreOrZero)
                .min()
                .orElse(0.0);
        double maxScore = results.stream()
                .mapToDouble(this::getScoreOrZero)
                .max()
                .orElse(1.0);

        double range = maxScore - minScore;
        if (range == 0.0) {
            // All scores are the same, set to 1.0
            results.forEach(r -> r.setScore(1.0));
            return results;
        }

        // Normalize: (score - min) / range
        results.forEach(result -> {
            double score = getScoreOrZero(result);
            double normalized = (score - minScore) / range;
            result.setScore(Math.max(0.0, Math.min(1.0, normalized))); // Clamp to 0-1
        });

        return results;
    }

    /**
     * Merges results from BM25 and Fuzzy searches, combining scores
     * @param bm25Results BM25 search results
     * @param fuzzyResults Fuzzy search results
     * @param limit Maximum number of results to return
     * @return Merged and deduplicated results sorted by combined score
     */
    private List<CoursSearchResult> mergeResults(List<CoursSearchResult> bm25Results, 
                                                  List<CoursSearchResult> fuzzyResults, 
                                                  int limit) {
        // Normalize BM25 scores to 0-1 range
        List<CoursSearchResult> normalizedBM25 = normalizeBM25Scores(new ArrayList<>(bm25Results));

        // Fuzzy scores are already 0-1, no normalization needed

        // Create a map to merge results by sigle
        Map<String, CoursSearchResult> mergedMap = new HashMap<>();

        // Add BM25 results (weight: 60%)
        for (CoursSearchResult result : normalizedBM25) {
            String sigle = result.getSigle();
            if (sigle != null) {
                result.setScore(result.getScore() * 0.6); // BM25 weight
                mergedMap.put(sigle, result);
            }
        }

        // Merge with Fuzzy results (weight: 40%)
        for (CoursSearchResult result : fuzzyResults) {
            String sigle = result.getSigle();
            if (sigle != null) {
                double fuzzyScore = getScoreOrZero(result) * 0.4; // Fuzzy weight

                if (mergedMap.containsKey(sigle)) {
                    // Combine scores: BM25 (60%) + Fuzzy (40%)
                    CoursSearchResult existing = mergedMap.get(sigle);
                    double combinedScore = getScoreOrZero(existing) + fuzzyScore;
                    existing.setScore(Math.min(1.0, combinedScore)); // Cap at 1.0
                } else {
                    // New result from fuzzy only
                    result.setScore(fuzzyScore);
                    mergedMap.put(sigle, result);
                }
            }
        }

        // Sort by combined score descending and limit
        return sortAndLimitByScore(new ArrayList<>(mergedMap.values()), limit);
    }

    /**
     * Hybrid search that automatically selects the best algorithm based on query characteristics
     * - Short queries (≤6 chars) or single word → Fuzzy search
     * - Long queries (>20 chars) or many words (≥4) → BM25 search
     * - Medium queries (7-20 chars, 2-3 words) → Hybrid (both algorithms merged)
     * 
     * @param courses List of courses to search through
     * @param query Search query
     * @param limit Maximum number of results to return
     * @param maxDistance Optional maximum edit distance for fuzzy search (auto-calculated if null)
     * @return List of search results sorted by relevance score (descending)
     */
    public List<CoursSearchResult> searchHybrid(List<CoursDataWrapper> courses, String query, int limit, Integer maxDistance) {
        Optional<String> validQuery = validateInput(courses, query);
        if (validQuery.isEmpty()) {
            return Collections.emptyList();
        }

        String trimmedQuery = validQuery.get();

        // Auto-select algorithm based on query characteristics
        if (shouldUseFuzzy(trimmedQuery)) {
            // Short query or single word → Use Fuzzy
            return searchFuzzy(courses, trimmedQuery, limit, maxDistance);
        } else if (shouldUseHybrid(trimmedQuery)) {
            // Medium query → Hybrid (both algorithms, merge results)
            List<CoursSearchResult> bm25Results = searchBM25(courses, trimmedQuery, limit * 2); // Get more results for merging
            List<CoursSearchResult> fuzzyResults = searchFuzzy(courses, trimmedQuery, limit * 2, maxDistance);
            return mergeResults(bm25Results, fuzzyResults, limit);
        } else {
            // Long query or many words → Use BM25
            return searchBM25(courses, trimmedQuery, limit);
        }
    }

    /**
     * Performs autocomplete search on courses
     * Optimized for prefix matching on sigle and title
     * 
     * @param courses List of courses to search through
     * @param query Search query (typically a prefix)
     * @param limit Maximum number of results to return
     * @return List of autocomplete results sorted by relevance score (descending)
     */
    public List<CoursAutocompleteResult> autocomplete(List<CoursDataWrapper> courses, String query, int limit) {
        Optional<String> validQuery = validateInput(courses, query);
        if (validQuery.isEmpty()) {
            return Collections.emptyList();
        }

        String queryNormalized = normalizeQuery(validQuery.get());
        List<CoursAutocompleteResult> results = new ArrayList<>();

        for (CoursDataWrapper course : courses) {
            double score = 0.0;

            // Check sigle prefix match (highest priority)
            if (course.getSigle() != null) {
                String sigleUpper = normalizeQuery(course.getSigle());
                if (sigleUpper.startsWith(queryNormalized)) {
                    // Exact prefix match on sigle gets highest score
                    score = 1.0;
                    // Boost exact matches
                    if (sigleUpper.equals(queryNormalized)) {
                        score = 1.5;
                    }
                } else if (sigleUpper.contains(queryNormalized)) {
                    // Contains match on sigle gets lower score
                    score = 0.7;
                }
            }

            // Check title prefix match (medium priority)
            if (score < 1.0 && course.getTitre() != null) {
                String titreUpper = normalizeQuery(course.getTitre());
                if (titreUpper.startsWith(queryNormalized)) {
                    score = Math.max(score, 0.6);
                } else if (titreUpper.contains(queryNormalized)) {
                    // Check if any word in title starts with query
                    List<String> titleWords = extractWords(titreUpper, 0);
                    for (String word : titleWords) {
                        if (word.startsWith(queryNormalized)) {
                            score = Math.max(score, 0.5);
                            break;
                        }
                    }
                    if (score == 0.0 && titreUpper.contains(queryNormalized)) {
                        score = 0.3;
                    }
                }
            }

            // Only include matches above threshold
            if (score > 0.0) {
                CoursAutocompleteResult result = new CoursAutocompleteResult();
                result.setSigle(course.getSigle());
                result.setTitre(course.getTitre());
                result.setScore(score);
                results.add(result);
            }
        }

        // Sort by score descending and limit results
        return sortAndLimitAutocompleteByScore(results, limit);
    }

    /**
     * Internal class to hold fuzzy match results
     */
    private static class FuzzyMatch {
        final CoursDataWrapper course;
        final double score;

        FuzzyMatch(CoursDataWrapper course, double score) {
            this.course = course;
            this.score = score;
        }
    }

    /**
     * Internal class to hold document data for BM25 calculation
     */
    private static class DocumentData {
        final CoursDataWrapper course;
        final int length;
        final Map<String, Integer> termFrequencies;

        DocumentData(CoursDataWrapper course, int length, Map<String, Integer> termFrequencies) {
            this.course = course;
            this.length = length;
            this.termFrequencies = termFrequencies;
        }
    }
}
