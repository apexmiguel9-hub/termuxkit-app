package com.termux.shared.settings.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.NonNull
import androidx.annotation.Nullable

/** A class that holds [SharedPreferences] objects for apps. */
open class AppSharedPreferences(
    @NonNull val context: Context,
    @Nullable val mSharedPreferences: SharedPreferences?,
    @Nullable val mMultiProcessSharedPreferences: SharedPreferences?
) {

    protected constructor(
        context: Context,
        sharedPreferences: SharedPreferences?
    ) : this(context, sharedPreferences, null)

    /** Get [context]. */
    fun getContext(): Context = context

    /** Get [mSharedPreferences]. */
    fun getSharedPreferences(): SharedPreferences? = mSharedPreferences

    /** Get [mMultiProcessSharedPreferences]. */
    fun getMultiProcessSharedPreferences(): SharedPreferences? = mMultiProcessSharedPreferences
}
