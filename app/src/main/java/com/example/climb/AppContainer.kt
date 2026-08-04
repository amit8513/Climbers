package com.example.climb

import android.content.Context
import android.os.Environment
import com.example.climb.data.ClimbDatabase
import com.example.climb.data.ClimbRepository
import java.io.File

class AppContainer(context: Context) {
    val climbRepository: ClimbRepository by lazy {
        ClimbRepository(ClimbDatabase.getInstance(context).climbDao())
    }

    val moviesDir: File by lazy {
        (context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir).apply {
            mkdirs()
        }
    }
}
