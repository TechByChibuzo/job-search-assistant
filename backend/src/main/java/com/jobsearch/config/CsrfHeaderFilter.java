package com.jobsearch.config;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cross-site request forgery protection for an app with no login. A page on another site can make the
 * browser send a plain POST to localhost (it just can't read the answer), which could start a sync or spend
 * Claude credit. Every request that changes something must carry this header; a browser only lets another
 * site add a custom header after a CORS preflight, which this API refuses for any origin but its own.
 */
@Component
public class CsrfHeaderFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Requested-With";
    public static final String VALUE = "job-search-assistant";
    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") || SAFE.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!VALUE.equals(request.getHeader(HEADER))) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"title\": \"Forbidden\", \"status\": 403, "
                    + "\"detail\": \"Requests that change data must come from the app (missing " + HEADER + " header).\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
