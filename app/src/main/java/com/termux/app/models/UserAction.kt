package com.termux.app.models

enum class UserAction(val displayName: String) {
    ABOUT("about"),
    REPORT_ISSUE_FROM_TRANSCRIPT("report issue from transcript");

    /** Get the name for the user action */
    fun getName(): String = displayName
}
