package com.jobsearch.tracker;

/** Things that happen in a conversation. SENT starts one; the rest are recorded on it. */
public enum OutreachEventType {
    SENT,
    FOLLOW_UP,
    REPLIED,
    REFERRED,
    NO_REPLY,
    DECLINED,
    NOTE
}
