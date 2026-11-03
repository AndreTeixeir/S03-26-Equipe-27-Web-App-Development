package com.smarttrafficflow.backend.domain.exports.service;

/**
 * Rules for writing CSV fields.
 * <ul>
 *   <li>RFC 4180: a field with a comma, a double quote or a line break goes between double quotes, and the double
 *       quotes inside it are doubled.</li>
 *   <li>Formula injection: a spreadsheet runs a cell that starts with {@code =}, {@code +}, {@code -}, {@code @},
 *       a tab or a carriage return as a formula. Text fields that start with one of them get an apostrophe in front,
 *       which the spreadsheet shows as plain text.</li>
 * </ul>
 */
final class CsvFormat {

    /** UTF-8 byte order mark: without it, Excel reads the file in the local code page and breaks the accents. */
    static final String BYTE_ORDER_MARK = String.valueOf((char) 0xFEFF);

    private CsvFormat() {
    }

    /** A free-text field: protected against formulas, then quoted when needed. Null becomes an empty field. */
    static String text(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return quoteIfNeeded(startsLikeAFormula(value) ? "'" + value : value);
    }

    private static boolean startsLikeAFormula(String value) {
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r';
    }

    private static String quoteIfNeeded(String value) {
        boolean needsQuotes = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        return needsQuotes ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }
}
