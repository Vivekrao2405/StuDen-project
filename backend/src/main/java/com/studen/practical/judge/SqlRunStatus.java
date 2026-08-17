package com.studen.practical.judge;

public enum SqlRunStatus {
    SUCCESS,
    QUERY_ERROR,
    TIMEOUT,
    // Failed the pre-execution single-SELECT-statement guard -- never even reached a container.
    REJECTED,
    SYSTEM_ERROR
}
