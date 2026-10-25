package com.jobsearch.ai;

import java.time.Instant;

/**
 * The owner's resume, as Claude receives it: a PDF sent as a document, or plain text.
 *
 * @param pdf  the PDF bytes, or null for a text resume
 * @param text the text, or null for a PDF resume
 */
public record Resume(String fileName, Instant modifiedAt, byte[] pdf, String text) {

    boolean isPdf() {
        return pdf != null;
    }
}
