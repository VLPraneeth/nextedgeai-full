package com.syncari.core.service.mapper;

import com.syncari.core.model.AttributeDefinition;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

@Component("searchBasedMapper")
@Slf4j
public class LuceneAutoFieldMapper implements AutoFieldMapper {

    @SneakyThrows
    @Override
    public Map<AttributeDefinition, AttributeDefinition> automap(List<AttributeDefinition> src, List<AttributeDefinition> dest) {
        Directory memoryIndex = new ByteBuffersDirectory();
        AttributeAnalyzer analyzer = new AttributeAnalyzer();
        final Map<String, AttributeDefinition> destMap = dest.stream()
                .collect(Collectors.toMap(AttributeDefinition::getId, f -> f));

        writeIndex(dest, memoryIndex, analyzer);

        // Two-pass approach to ensure exact matches are handled first
        log.info("Starting two-pass automapping for {} sources and {} destinations", src.size(), dest.size());

        // Shared set of mapped destinations across both passes
        Set<String> globalMapped = new HashSet<>();

        // PASS 1: Exact and near-exact matches (high confidence)
        Map<AttributeDefinition, AttributeDefinition> exactMatches =
                searchIndexWithMode(src, destMap, memoryIndex, analyzer, MatchMode.EXACT, globalMapped);

        // PASS 2: Fuzzy matches for remaining sources
        List<AttributeDefinition> remainingSources = src.stream()
                .filter(s -> !exactMatches.containsKey(s))
                .collect(Collectors.toList());

        Map<AttributeDefinition, AttributeDefinition> fuzzyMatches =
                searchIndexWithMode(remainingSources, destMap, memoryIndex, analyzer, MatchMode.FUZZY, globalMapped);

        // Combine both passes
        Map<AttributeDefinition, AttributeDefinition> allMatches = new HashMap<>(exactMatches);
        allMatches.putAll(fuzzyMatches);

        log.info("{}/{} sources mapped ({} exact, {} fuzzy), {} destinations provided",
                allMatches.size(), src.size(), exactMatches.size(), fuzzyMatches.size(), dest.size());

        return allMatches;
    }

    private enum MatchMode {
        EXACT,   // Only accept very high confidence matches
        FUZZY    // Accept lower confidence matches
    }

    private Map<AttributeDefinition, AttributeDefinition> searchIndexWithMode(
            List<AttributeDefinition> src,
            Map<String, AttributeDefinition> destMap,
            Directory memoryIndex,
            AttributeAnalyzer analyzer,
            MatchMode mode,
            Set<String> globalMapped) throws IOException, ParseException {

        Map<AttributeDefinition, AttributeDefinition> mapping = new HashMap<>();
        IndexReader indexReader = DirectoryReader.open(memoryIndex);
        IndexSearcher searcher = new IndexSearcher(indexReader);
        final StoredFields storedFields = indexReader.storedFields();

        for (AttributeDefinition srcField : src) {
            String apiName = preprocess(srcField.getApiName());
            String displayName = preprocess(srcField.getDisplayName());

            // Build Lucene query with fuzzy, prefix, and exact matching
            String q = MessageFormat.format("(apiName:{0}~) OR (apiName:{0}*) OR (apiName:{0}) " +
                    "OR (displayName:{1}~) OR (displayName:{1}*) OR (displayName:{1})", apiName, displayName);

            Query query = new QueryParser("apiName", analyzer).parse(q);
            TopDocs topDocs = searcher.search(query, 5);  // Get 5 candidates to have more options

            // Score each candidate with combined Lucene + String Similarity
            List<ScoredMatch> scoredMatches = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                final String attributeId = storedFields.document(scoreDoc.doc).get("id");
                if (globalMapped.contains(attributeId)) {
                    continue;  // Skip already mapped destinations
                }

                AttributeDefinition destField = destMap.get(attributeId);
                double combinedScore = calculateCombinedScore(srcField, destField, scoreDoc.score);

                scoredMatches.add(new ScoredMatch(destField, combinedScore, scoreDoc.score));
            }

            // Sort by combined score (highest first)
            scoredMatches.sort((a, b) -> Double.compare(b.combinedScore, a.combinedScore));

            // Select best match based on mode
            for (ScoredMatch scoredMatch : scoredMatches) {
                boolean shouldAccept = isShouldAccept(mode, scoredMatch);

                if (shouldAccept) {
                    log.info("[{}] Mapped: '{}' → '{}' (score={}, lucene={})",
                            mode.name(),
                            srcField.getDisplayName(),
                            scoredMatch.destination.getDisplayName(),
                            String.format("%.2f", scoredMatch.combinedScore),
                            String.format("%.2f", scoredMatch.luceneScore));

                    mapping.put(srcField, scoredMatch.destination);
                    globalMapped.add(scoredMatch.destination.getId());
                    break;
                }
            }
        }

        indexReader.close();
        return mapping;
    }

    private static boolean isShouldAccept(MatchMode mode, ScoredMatch scoredMatch) {
        if (mode == MatchMode.EXACT) {
            // In exact mode, only accept very high confidence matches
            // Exact matches score 100.0, strong semantic matches score 10-20
            // This prevents "last_modified" (score ~7) from matching "Last Name"
            return scoredMatch.combinedScore >= 10.0;  // Stricter threshold
        } else {
            // In fuzzy mode, accept lower confidence matches
            return scoredMatch.combinedScore >= 1.5;  // More lenient
        }
    }

    /**
     * Calculate combined score using Lucene score + Token Similarity + String Similarity
     * This gives semantic meaning to matches by comparing field names at word level
     */
    private double calculateCombinedScore(AttributeDefinition src, AttributeDefinition dest, float luceneScore) {
        // Get normalized field names
        String srcApi = normalizeForComparison(src.getApiName());
        String destApi = normalizeForComparison(dest.getApiName());
        String srcDisplay = normalizeForComparison(src.getDisplayName());
        String destDisplay = normalizeForComparison(dest.getDisplayName());

        // Check for exact matches (highest priority)
        boolean exactApiMatch = srcApi.equals(destApi);
        boolean exactDisplayMatch = srcDisplay.equals(destDisplay);

        if (exactApiMatch || exactDisplayMatch) {
            return 100.0;  // Exact match - always wins
        }

        // Calculate TOKEN similarity (word-level - most important for semantics)
        double apiTokenSim = calculateTokenSimilarity(src.getApiName(), dest.getApiName());
        double displayTokenSim = calculateTokenSimilarity(src.getDisplayName(), dest.getDisplayName());
        double tokenSimilarity = Math.max(apiTokenSim, displayTokenSim);

        // Calculate CHARACTER similarity (Levenshtein - catches typos)
        double apiCharSim = calculateStringSimilarity(srcApi, destApi);
        double displayCharSim = calculateStringSimilarity(srcDisplay, destDisplay);
        double charSimilarity = Math.max(apiCharSim, displayCharSim);

        // Token similarity weighted at 70%, character similarity at 30%
        // Token-level matching is more semantically meaningful than character-level
        double weightedSimilarity = (tokenSimilarity * 0.7) + (charSimilarity * 0.3);

        // If no common tokens at all, penalize heavily
        if (tokenSimilarity == 0.0) {
            return weightedSimilarity * 5.0;  // Max score = 5.0, won't pass EXACT threshold
        }

        // Combined score: Lucene + weighted similarity
        return luceneScore + (weightedSimilarity * 10.0);
    }

    /**
     * Calculate token-based similarity using Jaccard index
     * Compares words, not characters - much better for semantic understanding
     *
     * The two-pass approach with strict thresholds (score >= 10.0 for EXACT mode)
     * already prevents incorrect matches like "last_modified" → "Last Name".
     * No need for hardcoded generic token filtering which doesn't generalize well.
     *
     * Examples:
     * - "first_name" vs "First Name" = 1.0 (identical: {first, name})
     * - "full_name" vs "Last Name" = 0.5 ({full, name} ∩ {last, name} = {name})
     * - "last_modified" vs "Last Name" = 0.33 ({last, modified} ∩ {last, name} = {last})
     * - "hire_date" vs "school name" = 0.0 (no overlap: {hire,date} ∩ {school,name})
     */
    private double calculateTokenSimilarity(String s1, String s2) {
        Set<String> tokens1 = tokenize(s1);
        Set<String> tokens2 = tokenize(s2);

        if (tokens1.isEmpty() || tokens2.isEmpty()) {
            return 0.0;
        }

        // Calculate intersection
        Set<String> intersection = new HashSet<>(tokens1);
        intersection.retainAll(tokens2);

        // If no overlap at all, return 0
        if (intersection.isEmpty()) {
            return 0.0;
        }

        // Calculate Jaccard similarity: |intersection| / |union|
        Set<String> union = new HashSet<>(tokens1);
        union.addAll(tokens2);

        return (double) intersection.size() / union.size();
    }

    /**
     * Tokenize a field name into words
     * Handles camelCase, snake_case, spaces, etc.
     *
     * Examples:
     * - "firstName" → {first, name}
     * - "first_name" → {first, name}
     * - "First Name" → {first, name}
     */
    private Set<String> tokenize(String fieldName) {
        Set<String> tokens = new HashSet<>();

        // Split camelCase and normalize
        String normalized = fieldName
                .replaceAll("([a-z])([A-Z])", "$1 $2")  // firstName → first Name
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", " ")          // Replace non-alphanumeric with space
                .trim();

        for (String token : normalized.split("\\s+")) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }

        return tokens;
    }

    /**
     * Calculate string similarity using Levenshtein distance
     * Returns value between 0.0 (completely different) and 1.0 (identical)
     */
    private double calculateStringSimilarity(String s1, String s2) {
        if (s1.equals(s2)) {
            return 1.0;
        }

        int maxLength = Math.max(s1.length(), s2.length());
        if (maxLength == 0) {
            return 1.0;
        }

        int distance = levenshteinDistance(s1, s2);
        return 1.0 - ((double) distance / maxLength);
    }

    /**
     * Calculate Levenshtein distance between two strings
     * This measures the semantic similarity by counting minimum edits needed
     */
    private int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();

        // Create a matrix to store distances
        int[][] dp = new int[len1 + 1][len2 + 1];

        // Initialize first column and row
        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        // Fill the matrix
        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;

                dp[i][j] = Math.min(
                        Math.min(
                                dp[i - 1][j] + 1,      // deletion
                                dp[i][j - 1] + 1),     // insertion
                        dp[i - 1][j - 1] + cost);      // substitution
            }
        }

        return dp[len1][len2];
    }

    /**
     * Normalize field name for comparison
     * Removes underscores, spaces, converts to lowercase
     */
    private String normalizeForComparison(String fieldName) {
        return fieldName.toLowerCase()
                .replaceAll("[_\\s-]", "")
                .trim();
    }

    /**
     * Helper class to hold scored matches
     */
    private static class ScoredMatch {
        final AttributeDefinition destination;
        final double combinedScore;
        final float luceneScore;

        ScoredMatch(AttributeDefinition destination, double combinedScore, float luceneScore) {
            this.destination = destination;
            this.combinedScore = combinedScore;
            this.luceneScore = luceneScore;
        }
    }

    private void writeIndex(List<AttributeDefinition> dest, Directory memoryIndex, AttributeAnalyzer analyzer) throws IOException {
        IndexWriter writer = new IndexWriter(memoryIndex, new IndexWriterConfig(analyzer));

        for (AttributeDefinition destField : dest) {
            Document document = new Document();
            document.add(new TextField("apiName", preprocess(destField.getApiName()), Field.Store.NO));
            document.add(new TextField("id", destField.getId(), Field.Store.YES));
            document.add(new TextField("displayName", preprocess(destField.getDisplayName()), Field.Store.NO));
            writer.addDocument(document);
        }
        writer.flush();
        writer.close();
    }

    private String preprocess(String destField) {
        StringBuilder sb = new StringBuilder();
        for (char c : destField.toCharArray()) {
            if (!Character.isAlphabetic(c)) {
                sb.append(' ');
            } else if (Character.isLowerCase(c)) {
                sb.append(c);
            } else {
                sb.append(' ');
                sb.append(c);
            }
        }
        return sb.toString().toLowerCase().trim();
    }

}