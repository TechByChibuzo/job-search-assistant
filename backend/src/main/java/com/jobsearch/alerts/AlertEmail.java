package com.jobsearch.alerts;

import java.time.Instant;

/** One job-alert email, as read from the mailbox. */
public record AlertEmail(String messageId, String from, String subject, Instant receivedAt, String html, String text) {

    /** The sender's address, lower case: {@code "LinkedIn <jobalerts-noreply@linkedin.com>"} → the address. */
    public String senderAddress() {
        if (from == null) {
            return "";
        }
        int lt = from.indexOf('<');
        int gt = from.indexOf('>', lt + 1);
        String address = lt >= 0 && gt > lt ? from.substring(lt + 1, gt) : from;
        return address.strip().toLowerCase();
    }

    /** The sender's display name, without quotes: {@code "Microsoft Careers"}. */
    public String senderName() {
        if (from == null) {
            return "";
        }
        int lt = from.indexOf('<');
        String name = lt > 0 ? from.substring(0, lt) : "";
        return name.replace("\"", "").strip();
    }
}
