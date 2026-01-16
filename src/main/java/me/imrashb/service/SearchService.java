package me.imrashb.service;

import me.imrashb.domain.CoursDataWrapper;
import me.imrashb.domain.CoursSearchResult;
import me.imrashb.domain.CoursAutocompleteResult;
import me.imrashb.domain.Programme;
import me.imrashb.domain.SearchOptions;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchService {

    // BM25 tuning parameters
    private static final double K1 = 1.2;
    private static final double B = 0.75;

    // BM25F field weights
    private static final double W_SIGLE = 3.0;
    private static final double W_TITRE = 1.8;
    private static final double W_DESC = 0.8;

    // French stopwords - expanded list for academic content
    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
        // Common French articles and prepositions
        "le", "la", "les", "de", "des", "du", "un", "une", "et", "ou",
        "pour", "par", "dans", "sur", "au", "aux", "avec", "en", "à",
        // Demonstratives and relatives
        "ce", "cette", "ces", "que", "qui", "dont", "où", "comme",
        // Quantifiers
        "tout", "tous", "toute", "toutes", "plus", "moins", "très",
        // Common verbs (conjugated forms)
        "être", "avoir", "faire", "peut", "peuvent", "sont", "est", "sera", "seront",
        // Academic filler words
        "cours", "étudiant", "étudiante", "permet", "vise", "offre",
        "notions", "présente", "terme", "mesure", "travail", "travaux",
        "introduction", "base", "bases", "principes", "principe",
        "ainsi", "aussi", "entre", "autres", "autre", "même", "mêmes"
    ));

    // Acronym expansions for common French engineering terms
    private static final Map<String, List<String>> EXPANSIONS;
    static {
        Map<String, List<String>> exp = new HashMap<>();
        // Original expansions
        exp.put("POO", List.of("programmation", "orientée", "objet"));
        exp.put("UML", List.of("uml", "modélisation"));
        exp.put("API", List.of("api", "interface", "programmation"));
        exp.put("CAO", List.of("cao", "conception", "assistée"));
        // Extended engineering/CS acronyms
        exp.put("BDD", List.of("base", "données", "bdd"));
        exp.put("IOT", List.of("internet", "objets", "iot", "connectés"));
        exp.put("IA", List.of("intelligence", "artificielle", "ia"));
        exp.put("ML", List.of("machine", "learning", "apprentissage", "automatique"));
        exp.put("SQL", List.of("sql", "requêtes", "données", "relationnel"));
        exp.put("ORM", List.of("orm", "mapping", "objet", "relationnel"));
        exp.put("REST", List.of("rest", "api", "web", "service"));
        exp.put("TDD", List.of("tdd", "test", "driven", "développement"));
        exp.put("CI", List.of("ci", "intégration", "continue"));
        exp.put("CD", List.of("cd", "déploiement", "continu"));
        exp.put("TCP", List.of("tcp", "transmission", "protocole", "réseau"));
        exp.put("IP", List.of("ip", "internet", "protocole", "réseau"));
        exp.put("HTTP", List.of("http", "web", "protocole"));
        exp.put("GUI", List.of("gui", "interface", "graphique", "utilisateur"));
        exp.put("CLI", List.of("cli", "commande", "ligne", "terminal"));
        EXPANSIONS = Collections.unmodifiableMap(exp);
    }

    // Bigram IDF boost multiplier
    private static final double BIGRAM_IDF_BOOST = 1.5;

    // Exact sigle match boost - ensures exact matches rank first
    private static final double EXACT_SIGLE_BOOST = 5.0;

    // Sigle prefix match boost - for queries that look like course codes
    private static final double SIGLE_PREFIX_BOOST = 2.0;

    // Field-specific B values for length normalization
    private static final double B_SIGLE = 0.3;   // Less normalization (short field)
    private static final double B_TITRE = 0.5;   // Moderate normalization
    private static final double B_DESC = 0.75;   // Standard normalization (long field)

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
                score,
                course.getUrl()
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
     * Uses secondary sort by sigle for stable ordering when scores are identical
     * @param results List of search results
     * @param limit Maximum number of results
     * @return Sorted and limited list
     */
    private List<CoursSearchResult> sortAndLimitByScore(List<CoursSearchResult> results, int limit) {
        return results.stream()
                .sorted((a, b) -> {
                    // Primary sort: by score (descending)
                    double scoreA = getScoreOrZero(a);
                    double scoreB = getScoreOrZero(b);
                    int scoreCompare = Double.compare(scoreB, scoreA);
                    if (scoreCompare != 0) {
                        return scoreCompare;
                    }
                    // Secondary sort: by sigle (ascending) for stable tie-breaking
                    String sigleA = a != null && a.getSigle() != null ? a.getSigle() : "";
                    String sigleB = b != null && b.getSigle() != null ? b.getSigle() : "";
                    return sigleA.compareTo(sigleB);
                })
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
     * Filters out stopwords from a list of tokens
     * @param tokens List of tokens to filter
     * @return List of tokens without stopwords
     */
    private List<String> filterStopwords(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return Collections.emptyList();
        }
        return tokens.stream()
                .filter(token -> !STOPWORDS.contains(token.toLowerCase()))
                .collect(Collectors.toList());
    }

    /**
     * Applies light French stemming to reduce word variations
     * Handles common French suffixes: plurals, -ements/-ement, -ations/-ation, etc.
     * @param token Token to stem
     * @return Stemmed token
     */
    private String stemFr(String token) {
        if (token == null || token.length() < 5) {
            return token;
        }

        String stemmed = token;

        // Handle common French suffix patterns - original patterns
        stemmed = stemmed.replaceAll("(ements|ement)$", "ement");
        stemmed = stemmed.replaceAll("(ations|ation)$", "ation");
        stemmed = stemmed.replaceAll("(iques|ique)$", "ique");
        stemmed = stemmed.replaceAll("(eurs|eur)$", "eur");
        stemmed = stemmed.replaceAll("(ités|ité)$", "ité");
        stemmed = stemmed.replaceAll("(euses|euse)$", "euse");

        // Enhanced patterns for better French stemming
        stemmed = stemmed.replaceAll("(iers|ier)$", "ier");
        stemmed = stemmed.replaceAll("(ables|able)$", "able");
        stemmed = stemmed.replaceAll("(ibles|ible)$", "ible");
        stemmed = stemmed.replaceAll("(ifs|if)$", "if");
        stemmed = stemmed.replaceAll("(ives|ive)$", "ive");
        stemmed = stemmed.replaceAll("(ances|ance)$", "ance");
        stemmed = stemmed.replaceAll("(ences|ence)$", "ence");
        stemmed = stemmed.replaceAll("(tions|tion)$", "tion");
        stemmed = stemmed.replaceAll("(elles|elle)$", "elle");
        stemmed = stemmed.replaceAll("(aux)$", "al");  // nationaux -> national

        // Remove plural -s (but preserve if it's part of the word structure)
        if (stemmed.length() > 3 && stemmed.endsWith("s") &&
            !stemmed.endsWith("ss") && !stemmed.endsWith("us") &&
            !stemmed.endsWith("is") && !stemmed.endsWith("os")) {
            stemmed = stemmed.substring(0, stemmed.length() - 1);
        }

        return stemmed;
    }

    /**
     * Applies stemming to a list of tokens
     * @param tokens List of tokens to stem
     * @return List of stemmed tokens
     */
    private List<String> stemTokens(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return Collections.emptyList();
        }
        return tokens.stream()
                .map(this::stemFr)
                .collect(Collectors.toList());
    }

    /**
     * Generates bigrams from a list of tokens
     * Format: "token1_token2"
     * @param tokens List of tokens
     * @return List of bigrams
     */
    private List<String> generateBigrams(List<String> tokens) {
        if (tokens == null || tokens.size() < 2) {
            return Collections.emptyList();
        }
        List<String> bigrams = new ArrayList<>();
        for (int i = 0; i < tokens.size() - 1; i++) {
            bigrams.add(tokens.get(i) + "_" + tokens.get(i + 1));
        }
        return bigrams;
    }

    /**
     * Expands acronyms in query tokens using the expansion map
     * Tokens are typically lowercase from tokenization, but we check both cases
     * @param tokens List of query tokens
     * @return List of tokens with acronyms expanded
     */
    private List<String> expandAcronyms(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> expanded = new ArrayList<>(tokens);

        // Check each token for acronym matches (case-insensitive)
        for (String token : tokens) {
            String upperToken = token.toUpperCase();
            if (EXPANSIONS.containsKey(upperToken)) {
                // Add expanded terms (they'll be tokenized and stemmed if needed)
                expanded.addAll(EXPANSIONS.get(upperToken));
            }
        }

        // Deduplicate while preserving order
        return expanded.stream().distinct().collect(Collectors.toList());
    }

    // ========== Filtering Methods ==========

    /**
     * Filters courses by search options (programmes, credits)
     * @param courses List of courses to filter
     * @param options Search options containing filter criteria
     * @return Filtered list of courses
     */
    private List<CoursDataWrapper> filterByOptions(List<CoursDataWrapper> courses, SearchOptions options) {
        if (options == null || courses == null || courses.isEmpty()) {
            return courses;
        }

        List<CoursDataWrapper> filtered = courses;

        // Filter by programmes if specified
        if (options.getProgrammes() != null && !options.getProgrammes().isEmpty()) {
            filtered = filterByProgrammes(filtered, options.getProgrammes());
        }

        // Filter by credits if specified
        if (options.getMinCredits() != null || options.getMaxCredits() != null) {
            filtered = filterByCredits(filtered, options.getMinCredits(), options.getMaxCredits());
        }

        return filtered;
    }

    /**
     * Filters courses by programme codes extracted from sigle prefixes
     * @param courses List of courses to filter
     * @param programmes List of programmes to filter by
     * @return Filtered list of courses
     */
    private List<CoursDataWrapper> filterByProgrammes(List<CoursDataWrapper> courses, List<Programme> programmes) {
        if (courses == null || programmes == null || programmes.isEmpty()) {
            return courses != null ? courses : Collections.emptyList();
        }

        // Extract programme name strings (e.g., "LOG", "INF", "ELE")
        Set<String> programmeNames = programmes.stream()
                .map(Programme::name)
                .collect(Collectors.toSet());

        return courses.stream()
                .filter(course -> {
                    if (course.getSigle() == null) {
                        return false;
                    }
                    // Extract programme prefix from sigle (e.g., "LOG" from "LOG100")
                    String sigle = course.getSigle().toUpperCase();
                    // Match sigle prefix against programme names
                    for (String progName : programmeNames) {
                        if (sigle.startsWith(progName)) {
                            return true;
                        }
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    /**
     * Filters courses by credits range (minCredits <= credits <= maxCredits)
     * @param courses List of courses to filter
     * @param minCredits Minimum credits (inclusive, null means no minimum)
     * @param maxCredits Maximum credits (inclusive, null means no maximum)
     * @return Filtered list of courses
     */
    private List<CoursDataWrapper> filterByCredits(List<CoursDataWrapper> courses, Integer minCredits, Integer maxCredits) {
        if (courses == null) {
            return Collections.emptyList();
        }

        // If both are null, no filtering needed
        if (minCredits == null && maxCredits == null) {
            return courses;
        }

        return courses.stream()
                .filter(course -> {
                    Integer credits = course.getCredits();
                    if (credits == null) {
                        return false;
                    }

                    // Check minimum credits
                    if (minCredits != null && credits < minCredits) {
                        return false;
                    }

                    // Check maximum credits
                    if (maxCredits != null && credits > maxCredits) {
                        return false;
                    }

                    return true;
                })
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

        // Tokenize and expand query terms
        List<String> queryTerms = tokenize(validQuery.get());
        queryTerms = expandAcronyms(queryTerms);

        if (queryTerms.isEmpty()) {
            return Collections.emptyList();
        }

        // Preprocess documents: tokenize each field separately for BM25F
        List<DocumentData> documentData = courses.stream()
                .map(course -> {
                    try {
                        // Tokenize each field separately
                        List<String> sigleTokens = course.getSigle() != null 
                            ? tokenizeField(course.getSigle()) 
                            : Collections.emptyList();
                        List<String> titreTokens = course.getTitre() != null 
                            ? tokenizeField(course.getTitre()) 
                            : Collections.emptyList();
                        List<String> descTokens = (course.getDescription() != null && !course.getDescription().trim().isEmpty())
                            ? tokenizeField(course.getDescription())
                            : Collections.emptyList();

                        // Calculate term frequencies for each field
                        Map<String, Integer> sigleTF = calculateTermFrequencies(sigleTokens);
                        Map<String, Integer> titreTF = calculateTermFrequencies(titreTokens);
                        Map<String, Integer> descTF = calculateTermFrequencies(descTokens);

                        // Calculate field lengths (minimum 1 to prevent division issues)
                        int sigleLen = Math.max(1, sigleTokens.size());
                        int titreLen = Math.max(1, titreTokens.size());
                        int descLen = Math.max(1, descTokens.size());

                        return new DocumentData(course, sigleLen, titreLen, descLen, sigleTF, titreTF, descTF);
                    } catch (Exception e) {
                        // If tokenization fails for a course, return empty document data
                        // This allows search to continue with other courses
                        return new DocumentData(course, 1, 1, 1, 
                            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
                    }
                })
                .collect(Collectors.toList());

        // Calculate average field lengths for BM25F
        double avgSigleLen = Math.max(1.0, documentData.stream()
                .mapToInt(doc -> doc.sigleLen)
                .average()
                .orElse(1.0));
        double avgTitreLen = Math.max(1.0, documentData.stream()
                .mapToInt(doc -> doc.titreLen)
                .average()
                .orElse(1.0));
        double avgDescLen = Math.max(1.0, documentData.stream()
                .mapToInt(doc -> doc.descLen)
                .average()
                .orElse(1.0));

        // Calculate IDF for each query term across all fields
        Map<String, Double> idfMap = calculateIDFBM25F(queryTerms, documentData);

        // Detect if query looks like a course code for sigle-specific boosting
        String originalQuery = validQuery.get();
        boolean isSigleQuery = looksLikeSigleQuery(originalQuery);

        // Calculate BM25F scores (field-aware) with exact match and prefix boosts
        List<CoursSearchResult> results = new ArrayList<>();
        for (DocumentData docData : documentData) {
            try {
                double score = calculateBM25FScore(queryTerms, docData, avgSigleLen, avgTitreLen, avgDescLen,
                                                  idfMap, originalQuery, isSigleQuery);
                // Include results with positive scores (using small epsilon to handle floating point precision)
                if (score > 1e-10 && Double.isFinite(score)) {
                    results.add(createSearchResult(docData.course, score));
                }
            } catch (Exception e) {
                // Skip this document if score calculation fails
                // Continue with other documents
            }
        }

        // Sort by score descending and limit results
        return sortAndLimitByScore(results, limit);
    }

    /**
     * Performs BM25 search on courses with optional filtering
     * @param courses List of courses to search through
     * @param query Search query
     * @param limit Maximum number of results to return
     * @param options Optional search filters (programmes, credits)
     * @return List of search results sorted by relevance score (descending)
     */
    public List<CoursSearchResult> searchBM25(List<CoursDataWrapper> courses, String query, int limit, SearchOptions options) {
        List<CoursDataWrapper> filteredCourses = filterByOptions(courses, options);
        return searchBM25(filteredCourses, query, limit);
    }

    /**
     * Tokenizes and processes a single field (sigle, titre, or description)
     * Returns tokens with bigrams included
     * @param fieldText Text from the field
     * @return List of tokens (unigrams + bigrams)
     */
    private List<String> tokenizeField(String fieldText) {
        if (fieldText == null || fieldText.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // Tokenize the field
        List<String> tokens = tokenize(fieldText);

        // Generate bigrams and add them to the token list
        List<String> bigrams = generateBigrams(tokens);
        tokens.addAll(bigrams);

        return tokens;
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
     * Enhanced to preserve French accents, handle Unicode, and split alphanumeric boundaries
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

        // Split at alphanumeric boundaries (e.g., "GTI320" -> "GTI 320")
        // Pattern: insert space between letter-digit and digit-letter transitions
        normalized = normalized.replaceAll("(\\p{L}+)(\\p{N}+)", "$1 $2");
        normalized = normalized.replaceAll("(\\p{N}+)(\\p{L}+)", "$1 $2");

        // Normalize whitespace again after boundary insertion
        normalized = normalized.replaceAll("\\s+", " ").trim();

        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }

        // Split by whitespace and filter out empty strings
        List<String> tokens = Arrays.stream(normalized.split("\\s+"))
                .filter(term -> term.length() > 0)
                .collect(Collectors.toList());

        // Apply stemming and filter stopwords
        tokens = stemTokens(tokens);
        tokens = filterStopwords(tokens);

        return tokens;
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
     * Legacy method for backward compatibility
     */
    private Map<String, Double> calculateIDF(List<String> queryTerms, List<DocumentData> documents) {
        Map<String, Double> idfMap = new HashMap<>();
        int totalDocuments = documents.size();

        for (String term : queryTerms) {
            long documentsWithTerm = documents.stream()
                    .filter(doc -> {
                        // Check if term appears in any field
                        return doc.sigleTF.containsKey(term) || 
                               doc.titreTF.containsKey(term) || 
                               doc.descTF.containsKey(term);
                    })
                    .count();

            if (documentsWithTerm > 0) {
                // IDF formula: log(1 + (N - n + 0.5) / (n + 0.5)) where N = total docs, n = docs with term
                // Using log(1 + ...) variant to ensure non-negative IDF values, especially for small corpora
                double idf = Math.log(1.0 + (totalDocuments - documentsWithTerm + 0.5) / (documentsWithTerm + 0.5));
                // Apply bigram boost if this is a bigram term
                if (term.contains("_")) {
                    idf *= BIGRAM_IDF_BOOST;
                }
                idfMap.put(term, idf);
            } else {
                idfMap.put(term, 0.0);
            }
        }

        return idfMap;
    }

    /**
     * Calculates Inverse Document Frequency (IDF) for BM25F (field-aware)
     * Checks if term appears in any field across documents
     */
    private Map<String, Double> calculateIDFBM25F(List<String> queryTerms, List<DocumentData> documents) {
        Map<String, Double> idfMap = new HashMap<>();
        int totalDocuments = documents.size();

        for (String term : queryTerms) {
            long documentsWithTerm = documents.stream()
                    .filter(doc -> {
                        // Check if term appears in any field
                        return doc.sigleTF.containsKey(term) || 
                               doc.titreTF.containsKey(term) || 
                               doc.descTF.containsKey(term);
                    })
                    .count();

            if (documentsWithTerm > 0) {
                // IDF formula: log(1 + (N - n + 0.5) / (n + 0.5))
                // This ensures rare terms (few documents) get higher IDF scores
                double idf = Math.log(1.0 + (totalDocuments - documentsWithTerm + 0.5) / (documentsWithTerm + 0.5));
                // Apply bigram boost if this is a bigram term
                if (term.contains("_")) {
                    idf *= BIGRAM_IDF_BOOST;
                }
                idfMap.put(term, idf);
            } else {
                // Term not found in any document - use small non-zero IDF to allow partial matches
                // This helps with rare terms that might match via substring matching
                double rareTermIdf = Math.log(1.0 + totalDocuments / 0.5) * 0.1; // Small but non-zero
                idfMap.put(term, rareTermIdf);
            }
        }

        return idfMap;
    }

    /**
     * Calculates BM25 score for a document given a query (legacy method for backward compatibility)
     */
    private double calculateBM25Score(List<String> queryTerms, DocumentData doc, double avgDocLength, Map<String, Double> idfMap) {
        // Use BM25F scoring with equal field weights as fallback
        double avgSigleLen = Math.max(1.0, avgDocLength / 3.0);
        double avgTitreLen = Math.max(1.0, avgDocLength / 3.0);
        double avgDescLen = Math.max(1.0, avgDocLength / 3.0);
        return calculateBM25FScore(queryTerms, doc, avgSigleLen, avgTitreLen, avgDescLen, idfMap, null, false);
    }

    /**
     * Calculates BM25F (field-aware BM25) score for a document given a query
     * Uses separate field weights and average lengths for sigle, titre, and description
     * Applies exact sigle match and sigle prefix boosts
     * @param queryTerms Tokenized query terms
     * @param doc Document data with field TFs
     * @param avgSigleLen Average sigle length across corpus
     * @param avgTitreLen Average titre length across corpus
     * @param avgDescLen Average description length across corpus
     * @param idfMap IDF values for query terms
     * @param originalQuery Original query string (for exact match detection)
     * @param isSigleQuery Whether query looks like a course code
     */
    private double calculateBM25FScore(List<String> queryTerms, DocumentData doc,
                                      double avgSigleLen, double avgTitreLen, double avgDescLen,
                                      Map<String, Double> idfMap, String originalQuery, boolean isSigleQuery) {
        double score = 0.0;

        // Apply effective sigle weight (boosted for sigle-like queries)
        double effectiveSigleWeight = isSigleQuery ? W_SIGLE * 2.0 : W_SIGLE;

        for (String term : queryTerms) {
            double idf = idfMap.getOrDefault(term, 0.0);
            if (idf == 0) continue;

            // Get raw TF for each field
            int sigleTF = doc.sigleTF.getOrDefault(term, 0);
            int titreTF = doc.titreTF.getOrDefault(term, 0);
            int descTF = doc.descTF.getOrDefault(term, 0);

            // Calculate field-specific BM25 scores with individual B values
            double sigleScore = 0.0;
            if (sigleTF > 0) {
                double safeSigleAvg = Math.max(1.0, avgSigleLen);
                double sigleNorm = 1 - B_SIGLE + B_SIGLE * (doc.sigleLen / safeSigleAvg);
                sigleScore = (sigleTF * (K1 + 1)) / (sigleTF + K1 * sigleNorm);
            }

            double titreScore = 0.0;
            if (titreTF > 0) {
                double safeTitreAvg = Math.max(1.0, avgTitreLen);
                double titreNorm = 1 - B_TITRE + B_TITRE * (doc.titreLen / safeTitreAvg);
                titreScore = (titreTF * (K1 + 1)) / (titreTF + K1 * titreNorm);
            }

            double descScore = 0.0;
            if (descTF > 0) {
                double safeDescAvg = Math.max(1.0, avgDescLen);
                double descNorm = 1 - B_DESC + B_DESC * (doc.descLen / safeDescAvg);
                descScore = (descTF * (K1 + 1)) / (descTF + K1 * descNorm);
            }

            // Weighted combination of field scores
            double fieldScore = effectiveSigleWeight * sigleScore +
                               W_TITRE * titreScore +
                               W_DESC * descScore;

            score += idf * fieldScore;
        }

        // Apply exact sigle match boost and partial match support
        String sigle = doc.course.getSigle();
        if (sigle != null && originalQuery != null) {
            String normalizedSigle = sigle.toUpperCase().trim();
            String normalizedQuery = originalQuery.toUpperCase().trim();

            if (normalizedSigle.equals(normalizedQuery)) {
                // Exact match - apply large boost
                score += EXACT_SIGLE_BOOST;
            } else if (normalizedSigle.startsWith(normalizedQuery) && isSigleQuery) {
                // Prefix match for sigle-like queries - apply smaller boost
                score += SIGLE_PREFIX_BOOST;
            } else if (normalizedSigle.contains(normalizedQuery) && normalizedQuery.length() >= 3) {
                // Substring match for partial words (e.g., "ject" in "object")
                score += 1.5; // Small boost for substring matches
            }
        }
        
        // For single-word queries, check if query is a substring of title/description
        if (originalQuery != null && queryTerms.size() == 1 && !isSigleQuery) {
            String normalizedQuery = originalQuery.toLowerCase().trim();
            if (normalizedQuery.length() >= 3) {
                // Check title for substring matches
                String titre = doc.course.getTitre();
                if (titre != null) {
                    String titreLower = titre.toLowerCase();
                    if (titreLower.contains(normalizedQuery)) {
                        score += 0.8; // Small boost for substring match in title
                    }
                }
                // Check description for substring matches (only if no good title match)
                if (doc.course.getDescription() != null) {
                    String descLower = doc.course.getDescription().toLowerCase();
                    if (descLower.contains(normalizedQuery)) {
                        score += 0.5; // Smaller boost for substring match in description
                    }
                }
            }
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
            if (course == null) {
                continue; // Skip null courses
            }
            
            double bestScore = 0.0;

            // Check sigle (highest weight)
            if (course.getSigle() != null) {
                String sigleUpper = normalizeQuery(course.getSigle());
                double sigleScore = calculateFuzzyScore(queryNormalized, sigleUpper, effectiveMaxDistance);
                // Also check if query matches any prefix of sigle (for cases like "MAAT" matching "MAT380")
                if (sigleScore == 0.0 && sigleUpper != null && !sigleUpper.isEmpty()) {
                    // Check prefixes of sigle that could match within max distance
                    // For query "MAAT" (length 4) with maxDistance 1, we need to check prefixes of length 3-5
                    int sigleLen = sigleUpper.length();
                    int minPrefixLen = Math.max(1, queryNormalized.length() - effectiveMaxDistance);
                    int maxPrefixLen = Math.min(sigleLen, queryNormalized.length() + effectiveMaxDistance);
                    
                    // Ensure bounds are valid
                    if (minPrefixLen <= sigleLen && maxPrefixLen >= minPrefixLen && maxPrefixLen <= sigleLen) {
                        for (int i = minPrefixLen; i <= maxPrefixLen && i <= sigleLen; i++) {
                            try {
                                String prefix = sigleUpper.substring(0, i);
                                double prefixScore = calculateFuzzyScore(queryNormalized, prefix, effectiveMaxDistance);
                                if (prefixScore > 0.0) {
                                    // Apply small penalty for prefix matches (90% weight)
                                    sigleScore = Math.max(sigleScore, prefixScore * 0.9);
                                    break;
                                }
                            } catch (StringIndexOutOfBoundsException e) {
                                // Skip this prefix if bounds are invalid
                                break;
                            }
                        }
                    }
                }
                if (sigleScore > bestScore) {
                    bestScore = sigleScore;
                }
            }

            // Check titre (medium weight)
            if (course.getTitre() != null) {
                String titreUpper = normalizeQuery(course.getTitre());
                // Check if query matches any word in title (lowered min length from 3 to 2 for better typo tolerance)
                List<String> titleWords = extractWords(titreUpper, 2);
                for (String word : titleWords) {
                    double wordScore = calculateFuzzyScore(queryNormalized, word, effectiveMaxDistance);
                    // Lower threshold for title matches to catch more partial matches
                    if (wordScore > Math.max(0.1, bestScore * 0.7)) {
                        bestScore = Math.max(bestScore, wordScore * 0.9); // 90% weight for title matches
                    }
                }
                // Also check if query is contained in title as substring (for partial word matches)
                if (bestScore < 0.7 && titreUpper.length() >= queryNormalized.length()) {
                    if (titreUpper.contains(queryNormalized)) {
                        bestScore = Math.max(bestScore, 0.6); // Boost for substring matches
                    }
                }
            }

            // Check description (lower weight, only if no good match found)
            if (bestScore < 0.5 && course.getDescription() != null) {
                String descUpper = normalizeQuery(course.getDescription());
                // Lowered min length from 4 to 3 for better typo tolerance
                List<String> descWords = extractWords(descUpper, 3);
                for (String word : descWords) {
                    double wordScore = calculateFuzzyScore(queryNormalized, word, effectiveMaxDistance);
                    if (wordScore > bestScore) {
                        bestScore = wordScore * 0.7; // 70% weight for description matches
                    }
                }
            }

            // Only include matches above threshold
            if (bestScore > 0.0 && Double.isFinite(bestScore)) {
                matches.add(new FuzzyMatch(course, bestScore));
            }
        }

        // Sort by score descending and limit results
        return matches.stream()
                .filter(match -> match != null && match.course != null && Double.isFinite(match.score))
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(Math.max(0, limit)) // Ensure limit is non-negative
                .map(match -> {
                    try {
                        return createSearchResult(match.course, match.score);
                    } catch (Exception e) {
                        return null; // Skip this match if result creation fails
                    }
                })
                .filter(result -> result != null)
                .collect(Collectors.toList());
    }

    /**
     * Performs fuzzy search on courses with optional filtering
     * @param courses List of courses to search through
     * @param query Search query
     * @param limit Maximum number of results to return
     * @param maxDistance Maximum allowed edit distance (default: 2 for short queries, 3 for longer)
     * @param options Optional search filters (programmes, credits)
     * @return List of search results sorted by similarity score (descending)
     */
    public List<CoursSearchResult> searchFuzzy(List<CoursDataWrapper> courses, String query, int limit, Integer maxDistance, SearchOptions options) {
        List<CoursDataWrapper> filteredCourses = filterByOptions(courses, options);
        return searchFuzzy(filteredCourses, query, limit, maxDistance);
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
        if (query == null || target == null || query.isEmpty() || target.isEmpty()) {
            return 0.0;
        }

        // Exact match
        if (query.equals(target)) {
            return 1.0;
        }

        // Case-insensitive exact match (bonus)
        if (query.equalsIgnoreCase(target)) {
            return 0.98;
        }

        // Prefix match (boosted) - query is prefix of target
        if (target.startsWith(query)) {
            return 0.95;
        }

        // Reverse prefix match - target is prefix of query (partial input)
        if (query.length() > 2 && query.startsWith(target)) {
            return 0.90;
        }

        // Contains match (moderate boost)
        if (target.contains(query)) {
            return 0.85;
        }
        
        // Reverse contains - query contains target (for partial words)
        if (query.length() > target.length() && query.contains(target)) {
            return 0.80;
        }

        // Calculate Levenshtein distance
        int distance;
        try {
            distance = levenshteinDistance(query, target);
        } catch (Exception e) {
            // Fallback for edge cases
            return 0.0;
        }

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

        // Use normalized distance for better scoring
        double similarity = 1.0 - ((double) distance / maxLen);
        
        // Boost similarity for shorter queries that match longer targets (typo correction)
        if (query.length() >= 3 && target.length() > query.length() && distance <= 2) {
            similarity = Math.min(1.0, similarity * 1.1); // Small boost for typo correction
        }

        // Apply penalty for longer strings (prefer shorter, more exact matches)
        if (target.length() > query.length() * 1.5) {
            similarity *= 0.9;
        }

        return Math.max(0.0, Math.min(1.0, similarity));
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
     * Determines if query looks like a course code (sigle)
     * Pattern: 2-4 uppercase letters optionally followed by digits
     * Examples: LOG, LOG100, INF1120, GTI
     * @param query Search query
     * @return true if query looks like a sigle
     */
    private boolean looksLikeSigleQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }
        String upper = query.trim().toUpperCase();
        // Pattern: 2-4 letters optionally followed by 0-4 digits
        return upper.matches("^[A-Z]{2,4}\\d{0,4}$");
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
        int length = trimmed.length();
        int wordCount = trimmed.split("\\s+").length;
        
        // Use fuzzy for:
        // 1. Course-code-like queries: contains digits AND length ≤ 6
        // 2. Single-word queries of length 3-10 (for typo tolerance)
        boolean isCourseCodeLike = trimmed.matches(".*\\d.*") && length <= 6;
        boolean isSingleWordShort = wordCount == 1 && length >= 3 && length <= 10;
        
        return isCourseCodeLike || isSingleWordShort;
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
        int length = trimmed.length();
        int wordCount = trimmed.split("\\s+").length;

        // Use hybrid for:
        // 1. Medium-length queries (7-20 chars) with 2-3 words
        // 2. Single-word queries of 4-15 chars (for better typo tolerance)
        boolean isMultiWordMedium = length > 6 && length <= 20 && wordCount >= 2 && wordCount <= 3;
        boolean isSingleWordMedium = wordCount == 1 && length >= 4 && length <= 15;
        
        return isMultiWordMedium || isSingleWordMedium;
    }

    /**
     * Normalizes BM25 scores to 0-1 range using min-max normalization
     * Preserves score differentiation even when scores are identical or very similar
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
        
        // Threshold for considering scores "too similar" (relative to max score)
        double similarityThreshold = Math.max(1e-10, maxScore * 1e-6);
        
        if (range < similarityThreshold) {
            // Scores are too similar or identical - preserve order with rank-based variations
            // This ensures differentiation while maintaining relative order
            List<CoursSearchResult> sortedResults = new ArrayList<>(results);
            sortedResults.sort((a, b) -> {
                // First sort by original score (descending)
                int scoreCompare = Double.compare(getScoreOrZero(b), getScoreOrZero(a));
                if (scoreCompare != 0) {
                    return scoreCompare;
                }
                // Then sort by sigle (ascending) for stable tie-breaking
                String sigleA = a != null && a.getSigle() != null ? a.getSigle() : "";
                String sigleB = b != null && b.getSigle() != null ? b.getSigle() : "";
                return sigleA.compareTo(sigleB);
            });
            
            // Assign normalized scores with small rank-based variations
            // Highest ranked gets 1.0, others get slightly lower values
            int size = sortedResults.size();
            for (int i = 0; i < size; i++) {
                CoursSearchResult result = sortedResults.get(i);
                if (result != null) {
                    // Base score of 1.0 for top result, decreasing slightly for lower ranks
                    // Use exponential decay to preserve differentiation
                    double rankBonus = (size > 1) ? (double) i / (size - 1) : 0.0;
                    // Normalize to 0.9-1.0 range to preserve some differentiation
                    double normalized = 1.0 - (rankBonus * 0.1);
                    result.setScore(Math.max(0.9, Math.min(1.0, normalized)));
                }
            }
            return sortedResults;
        }

        // Normalize: (score - min) / range
        // For better distribution, use a slight log scaling when range is small
        double finalRange = range;
        if (range > 0 && range < maxScore * 0.1) {
            // For small ranges relative to max, apply slight log scaling for better distribution
            // This helps differentiate scores when they're close together
            double logBase = Math.max(1.01, 1.0 + (range / maxScore) * 10.0);
            for (CoursSearchResult result : results) {
                if (result != null) {
                    try {
                        double score = getScoreOrZero(result);
                        if (Double.isFinite(score)) {
                            // Normalize first
                            double normalized = (score - minScore) / finalRange;
                            // Apply slight log scaling for better distribution
                            normalized = Math.log(1.0 + (normalized * (logBase - 1.0))) / Math.log(logBase);
                            result.setScore(Math.max(0.0, Math.min(1.0, normalized)));
                        }
                    } catch (Exception e) {
                        // Keep original score if normalization fails
                    }
                }
            }
        } else {
            // Standard min-max normalization
            for (CoursSearchResult result : results) {
                if (result != null) {
                    try {
                        double score = getScoreOrZero(result);
                        if (range > 0.0 && Double.isFinite(score)) {
                            double normalized = (score - minScore) / range;
                            result.setScore(Math.max(0.0, Math.min(1.0, normalized))); // Clamp to 0-1
                        }
                    } catch (Exception e) {
                        // Keep original score if normalization fails
                    }
                }
            }
        }

        return results;
    }

    /**
     * Merges results from BM25 and Fuzzy searches, combining scores
     * Adds tie-breaking logic to ensure unique scores while preserving relevance order
     * @param bm25Results BM25 search results
     * @param fuzzyResults Fuzzy search results
     * @param limit Maximum number of results to return
     * @return Merged and deduplicated results sorted by combined score
     */
    private List<CoursSearchResult> mergeResults(List<CoursSearchResult> bm25Results, 
                                                  List<CoursSearchResult> fuzzyResults, 
                                                  int limit) {
        // Handle null inputs
        if (bm25Results == null) {
            bm25Results = Collections.emptyList();
        }
        if (fuzzyResults == null) {
            fuzzyResults = Collections.emptyList();
        }
        if (limit < 0) {
            limit = 0;
        }

        try {
            // Normalize BM25 scores to 0-1 range
            List<CoursSearchResult> normalizedBM25 = normalizeBM25Scores(new ArrayList<>(bm25Results));

            // Fuzzy scores are already 0-1, no normalization needed

            // Create a map to merge results by sigle
            Map<String, CoursSearchResult> mergedMap = new HashMap<>();

            // Add BM25 results (weight: 60%)
            for (CoursSearchResult result : normalizedBM25) {
                if (result == null) {
                    continue;
                }
                String sigle = result.getSigle();
                if (sigle != null && !sigle.trim().isEmpty()) {
                    try {
                        double score = getScoreOrZero(result) * 0.6; // BM25 weight
                        if (Double.isFinite(score)) {
                            result.setScore(score);
                            mergedMap.put(sigle, result);
                        }
                    } catch (Exception e) {
                        // Skip this result if score calculation fails
                    }
                }
            }

            // Merge with Fuzzy results (weight: 40%)
            for (CoursSearchResult result : fuzzyResults) {
                if (result == null) {
                    continue;
                }
                String sigle = result.getSigle();
                if (sigle != null && !sigle.trim().isEmpty()) {
                    try {
                        double fuzzyScore = getScoreOrZero(result) * 0.4; // Fuzzy weight
                        if (!Double.isFinite(fuzzyScore)) {
                            continue;
                        }

                        if (mergedMap.containsKey(sigle)) {
                            // Combine scores: BM25 (60%) + Fuzzy (40%)
                            CoursSearchResult existing = mergedMap.get(sigle);
                            if (existing != null) {
                                double combinedScore = getScoreOrZero(existing) + fuzzyScore;
                                existing.setScore(Math.min(1.0, Math.max(0.0, combinedScore))); // Cap at 0-1 range
                            }
                        } else {
                            // New result from fuzzy only
                            result.setScore(fuzzyScore);
                            mergedMap.put(sigle, result);
                        }
                    } catch (Exception e) {
                        // Skip this result if merge fails
                    }
                }
            }

            // Get merged results and sort by score
            List<CoursSearchResult> mergedResults = new ArrayList<>(mergedMap.values());
            
            // Sort by combined score descending, with secondary sort by sigle for tie-breaking
            mergedResults.sort((a, b) -> {
                double scoreA = getScoreOrZero(a);
                double scoreB = getScoreOrZero(b);
                int scoreCompare = Double.compare(scoreB, scoreA);
                if (scoreCompare != 0) {
                    return scoreCompare;
                }
                // Secondary sort by sigle (ascending) for stable ordering
                String sigleA = a != null && a.getSigle() != null ? a.getSigle() : "";
                String sigleB = b != null && b.getSigle() != null ? b.getSigle() : "";
                return sigleA.compareTo(sigleB);
            });

            // Apply rank-based micro-adjustments to ensure unique scores
            // This preserves relevance order while breaking ties
            double rankAdjustmentScale = 1e-12; // Very small adjustment to preserve relevance
            
            for (int i = 0; i < mergedResults.size(); i++) {
                CoursSearchResult result = mergedResults.get(i);
                if (result != null) {
                    double currentScore = getScoreOrZero(result);
                    // Subtract a tiny amount based on rank to ensure higher-ranked results have slightly higher scores
                    // This ensures uniqueness while maintaining the relevance order
                    double adjustedScore = currentScore - (i * rankAdjustmentScale);
                    // Ensure score stays in valid range
                    result.setScore(Math.max(0.0, Math.min(1.0, adjustedScore)));
                }
            }

            // Limit results
            return mergedResults.stream()
                    .limit(Math.max(0, limit))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            // If merging fails, return empty list instead of throwing
            return Collections.emptyList();
        }
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

        // Always run hybrid when algorithm=hybrid is explicitly requested
        // This ensures users get the benefit of both algorithms regardless of query characteristics
        // Auto-select only for internal routing (when hybrid is default)
        
        // For explicit hybrid requests, always merge both algorithms
        try {
            List<CoursSearchResult> bm25Results = searchBM25(courses, trimmedQuery, Math.max(1, limit * 2)); // Get more results for merging
            List<CoursSearchResult> fuzzyResults = searchFuzzy(courses, trimmedQuery, Math.max(1, limit * 2), maxDistance);
            return mergeResults(bm25Results, fuzzyResults, limit);
        } catch (Exception e) {
            // If hybrid search fails, fallback to BM25 only
            return searchBM25(courses, trimmedQuery, limit);
        }
    }

    /**
     * Hybrid search with optional filtering
     * @param courses List of courses to search through
     * @param query Search query
     * @param limit Maximum number of results to return
     * @param maxDistance Optional maximum edit distance for fuzzy search (auto-calculated if null)
     * @param options Optional search filters (programmes, credits)
     * @return List of search results sorted by relevance score (descending)
     */
    public List<CoursSearchResult> searchHybrid(List<CoursDataWrapper> courses, String query, int limit, Integer maxDistance, SearchOptions options) {
        List<CoursDataWrapper> filteredCourses = filterByOptions(courses, options);
        return searchHybrid(filteredCourses, query, limit, maxDistance);
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
                } else {
                    // Check if any word in title starts with query
                    List<String> titleWords = extractWords(titreUpper, 0);
                    boolean wordPrefixMatch = false;
                    for (String word : titleWords) {
                        if (word.startsWith(queryNormalized)) {
                            score = Math.max(score, 0.6);
                            wordPrefixMatch = true;
                            break;
                        }
                    }
                    // If no word prefix match but title contains query
                    if (!wordPrefixMatch && titreUpper.contains(queryNormalized)) {
                        score = Math.max(score, 0.3);
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
     * Performs autocomplete search on courses with optional filtering
     * @param courses List of courses to search through
     * @param query Search query (typically a prefix)
     * @param limit Maximum number of results to return
     * @param options Optional search filters (programmes, credits)
     * @return List of autocomplete results sorted by relevance score (descending)
     */
    public List<CoursAutocompleteResult> autocomplete(List<CoursDataWrapper> courses, String query, int limit, SearchOptions options) {
        List<CoursDataWrapper> filteredCourses = filterByOptions(courses, options);
        return autocomplete(filteredCourses, query, limit);
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
     * Internal class to hold document data for BM25F (field-aware BM25) calculation
     */
    private static class DocumentData {
        final CoursDataWrapper course;
        final int sigleLen;
        final int titreLen;
        final int descLen;
        final Map<String, Integer> sigleTF;
        final Map<String, Integer> titreTF;
        final Map<String, Integer> descTF;

        DocumentData(CoursDataWrapper course, int sigleLen, int titreLen, int descLen,
                    Map<String, Integer> sigleTF, Map<String, Integer> titreTF, Map<String, Integer> descTF) {
            this.course = course;
            this.sigleLen = sigleLen;
            this.titreLen = titreLen;
            this.descLen = descLen;
            this.sigleTF = sigleTF;
            this.titreTF = titreTF;
            this.descTF = descTF;
        }

        // Backward compatibility: total length (for legacy code that might reference it)
        int getTotalLength() {
            return sigleLen + titreLen + descLen;
        }
    }
}
