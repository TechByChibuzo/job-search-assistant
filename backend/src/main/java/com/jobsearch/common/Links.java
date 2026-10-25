package com.jobsearch.common;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Checks links the owner types before they are stored and rendered as anchors. Only absolute http(s)
 * links pass, so a stored value can never be a {@code javascript:} or {@code data:} URL.
 */
public final class Links {

    private Links() {}

    /** The link, normalised, or a 400 with {@code message} when it isn't an absolute http(s) link. */
    public static String requireHttp(String link, String message) {
        String value = link == null ? "" : link.strip();
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
                throw new BadRequestException(message);
            }
            return uri.toString();
        } catch (URISyntaxException e) {
            throw new BadRequestException(message);
        }
    }

    /** Like {@link #requireHttp} for an optional field: blank means none. */
    public static String optionalHttp(String link, String message) {
        return link == null || link.isBlank() ? null : requireHttp(link, message);
    }
}
