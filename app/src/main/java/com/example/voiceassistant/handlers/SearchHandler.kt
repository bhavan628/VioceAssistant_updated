package com.example.voiceassistant.handlers

import android.app.SearchManager
import android.content.Context
import android.content.Intent

object SearchHandler {

    fun handle(context: Context, remainder: String): String {
        val query = remainder.trim()
        if (query.isBlank()) return "What should I search for?"

        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
        }
        return if (intent.resolveActivity(context.packageManager) != null) {
            ActivityLauncher.launch(context, intent)
            "Searching for $query"
        } else {
            "I couldn't find an app to search with"
        }
    }
}
