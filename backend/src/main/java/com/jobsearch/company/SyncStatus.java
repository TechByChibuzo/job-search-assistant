package com.jobsearch.company;

public enum SyncStatus {
    OK,
    /** The board answered 404: the company has probably moved to another ATS. */
    BOARD_NOT_FOUND,
    FAILED
}
