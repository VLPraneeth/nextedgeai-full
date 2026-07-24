package com.syncari.utils.file;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.regex.Pattern;

/**
 * A Reader that filters out lines that start with a given regex pattern.
 * This class wraps another Reader and provides line-by-line filtering.
 */
public class PatternFilteringReader extends Reader {
    private final BufferedReader delegate;
    private final Pattern pattern;
    private String nextLine;
    private int nextLinePos;

    /**
     * Creates a new PatternFilteringReader.
     *
     * @param delegate The underlying Reader to filter
     * @param pattern  The regex pattern to match against line starts
     */
    public PatternFilteringReader(Reader delegate, String pattern) {
        this.delegate = new BufferedReader(delegate);
        this.pattern = Pattern.compile(pattern);
        this.nextLinePos = 0;
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        if (len <= 0) {
            return 0;
        }

        int charsRead = 0;
        while (charsRead < len) {
            // If we don't have a current line or have finished the current line
            if (nextLine == null || nextLinePos >= nextLine.length()) {
                if (!advanceToNextLine()) {
                    break; // End of stream
                }
                // Add line separator after each line except the last one
                nextLine += "\n";
            }

            // Copy characters from the current line
            int availableChars = nextLine.length() - nextLinePos;
            int charsToCopy = Math.min(len - charsRead, availableChars);
            nextLine.getChars(nextLinePos, nextLinePos + charsToCopy, cbuf, off + charsRead);
            nextLinePos += charsToCopy;
            charsRead += charsToCopy;
        }

        return charsRead == 0 && nextLine == null ? -1 : charsRead;
    }

    /**
     * Advances to the next non-filtered line.
     *
     * @return true if a line was found, false if end of stream
     */
    private boolean advanceToNextLine() throws IOException {
        nextLine = null;
        nextLinePos = 0;

        String line;
        while ((line = delegate.readLine()) != null) {
            if (!pattern.matcher(line).lookingAt()) {
                nextLine = line;
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}