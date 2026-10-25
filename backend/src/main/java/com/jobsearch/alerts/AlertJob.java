package com.jobsearch.alerts;

/** A role found in an alert email. Location may be null when the email doesn't say. */
public record AlertJob(String url, String title, String company, String location) {}
