package com.termux.shared.termux.models

enum class UserAction(val displayName: String) {

    CRASH_REPORT("crash report"),
    PLUGIN_EXECUTION_COMMAND("plugin execution command");

    fun getName(): String = displayName
}
