package com.example.kanjipractice

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * One Application class for both flavours; the namespace is shared and only the
 * application id differs.
 */
@HiltAndroidApp
class PracticeApplication : Application()
