package com.example.collegeroitool.service;

import java.util.regex.Pattern;

/** Lowercases, strips punctuation, and collapses whitespace in a school name for exact-match
 *  comparison against {@code low_earning_school_earnings.normalized_name}. Used by both the
 *  seeder (to precompute the column) and LowEarningSchoolMatchService (to normalize user input
 *  before comparing). Deliberately conservative — it doesn't strip words like "university" or
 *  "college", since that risks conflating distinct institutions (e.g. "Miami University" vs
 *  "University of Miami"). */
public final class SchoolNameNormalizer {

    private static final Pattern NON_ALNUM_SPACE = Pattern.compile("[^a-z0-9 ]");
    private static final Pattern MULTI_SPACE = Pattern.compile(" +");

    private SchoolNameNormalizer() {}

    public static String normalize(String name) {
        if (name == null) return "";
        String lower = name.toLowerCase();
        String stripped = NON_ALNUM_SPACE.matcher(lower).replaceAll(" ");
        return MULTI_SPACE.matcher(stripped).replaceAll(" ").trim();
    }

    /** First token that isn't a generic stopword — used to narrow DB candidates before an
     *  expensive full-table scan. Falls back to the first token if every token is a stopword. */
    public static String firstSignificantToken(String normalizedName) {
        String[] tokens = normalizedName.split(" ");
        for (String t : tokens) {
            if (!STOPWORDS.contains(t) && t.length() > 1) return t;
        }
        return tokens.length > 0 ? tokens[0] : "";
    }

    private static final java.util.Set<String> STOPWORDS = java.util.Set.of(
        "the", "of", "university", "college", "institute", "school", "at", "campus"
    );
}
