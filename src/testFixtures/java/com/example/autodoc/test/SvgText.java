package com.example.autodoc.test;

import java.util.Locale;

/**
 * Shared SVG pieces. Text is monospace, so its width follows from the character count and every layout is
 * exact and byte-identical on every machine.
 */
final class SvgText {

    static final double FONT_SIZE = 13;
    static final double SMALL_FONT_SIZE = 11;
    static final String FONT_FAMILY = "DejaVu Sans Mono, Menlo, Consolas, monospace";
    static final String LINE = "#333333";

    static double width(String text) {
        return width(text, FONT_SIZE);
    }

    static double width(String text, double fontSize) {
        return text.codePointCount(0, text.length()) * fontSize * 0.6;
    }

    /** Fixed formatting: integers without decimals, otherwise one decimal place. */
    static String num(double value) {
        String s = String.format(Locale.ROOT, "%.1f", value);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static String open(double width, double height) {
        return """
                <svg xmlns="http://www.w3.org/2000/svg" width="%1$s" height="%2$s" viewBox="0 0 %1$s %2$s" font-family="%3$s" font-size="%4$s">
                <defs>
                <marker id="arrow" viewBox="0 0 10 10" refX="10" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="%5$s"/></marker>
                <marker id="cross" viewBox="0 0 10 10" refX="10" refY="5" markerWidth="10" markerHeight="10" orient="auto"><path d="M1,1 L9,9 M9,1 L1,9" stroke="%5$s" stroke-width="2"/></marker>
                </defs>
                <rect x="0" y="0" width="%1$s" height="%2$s" fill="#ffffff"/>
                """.formatted(num(width), num(height), FONT_FAMILY, num(FONT_SIZE), LINE);
    }

    static String text(double x, double y, String anchor, String text) {
        return "<text x=\"%s\" y=\"%s\" text-anchor=\"%s\" fill=\"#000000\">%s</text>\n"
                .formatted(num(x), num(y), anchor, escape(text));
    }

    static String rect(double x, double y, double width, double height, String fill, String stroke, String extra) {
        return "<rect x=\"%s\" y=\"%s\" width=\"%s\" height=\"%s\" fill=\"%s\" stroke=\"%s\"%s/>\n"
                .formatted(num(x), num(y), num(width), num(height), fill, stroke, extra);
    }

    private SvgText() {
    }
}
