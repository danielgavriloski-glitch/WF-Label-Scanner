package com.mbidesign.terminal

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class TerminalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.getApps(this).none { it.name == FirebaseApp.DEFAULT_APP_NAME }) {
            FirebaseApp.initializeApp(this, options())
        }
    }
    companion object {
        // Public Firebase client configuration from the existing MBI project.
        // API restrictions / App Check may require registering the terminal package.
        fun options(): FirebaseOptions = FirebaseOptions.Builder()
            .setApplicationId("1:585808506580:android:688f307c42455634235541")
            .setApiKey("AIzaSyCJodXV-qSU7ll3P28J4kHLFdIAfgN4JVQ")
            .setProjectId("mbimetaldizain")
            .setGcmSenderId("585808506580")
            .build()
    }
}
