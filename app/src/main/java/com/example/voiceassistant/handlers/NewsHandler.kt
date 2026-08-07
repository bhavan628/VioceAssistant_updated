package com.example.voiceassistant.handlers

import android.content.Context
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * Handler 8/8: news.
 *
 * Fetches top headlines from NewsAPI.org (swap the base URL for GNews if you prefer —
 * same shape of change either way) and reads back the top few.
 *
 * This is the one handler that's genuinely async (network call), so unlike the other
 * seven it takes a callback instead of returning a String directly — the caller
 * (AssistantForegroundService) speaks the result once the callback fires.
 *
 * API key handling: read from BuildConfig, NOT hardcoded here. Add this to
 * app/build.gradle.kts's defaultConfig block:
 *   buildConfigField("String", "NEWS_API_KEY", "\"${project.findProperty("NEWS_API_KEY") ?: ""}\"")
 * and put NEWS_API_KEY=your_key_here in local.properties (already git-ignored by
 * default in Android projects) — never commit the real key. Also requires
 * `buildFeatures { buildConfig = true }` in the android {} block.
 */
object NewsHandler {

    private val client = OkHttpClient()

    fun handle(context: Context, onResult: (String) -> Unit) {
        if (!isNetworkAvailable(context)) {
            onResult("I can't check the news right now — no internet connection")
            return
        }

        val apiKey = com.example.voiceassistant.BuildConfig.NEWS_API_KEY
        if (apiKey.isBlank()) {
            onResult("News isn't set up yet — a NewsAPI key is missing")
            return
        }

        val url = "https://newsapi.org/v2/top-headlines?country=us&pageSize=3&apiKey=$apiKey"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult("I couldn't reach the news service right now")
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                response.use {
                    if (!it.isSuccessful) {
                        onResult("The news service returned an error")
                        return
                    }
                    try {
                        val body = it.body?.string() ?: ""
                        val json = JSONObject(body)
                        val articles = json.getJSONArray("articles")
                        if (articles.length() == 0) {
                            onResult("No headlines available right now")
                            return
                        }
                        val headlines = (0 until minOf(3, articles.length())).map { i ->
                            articles.getJSONObject(i).getString("title")
                        }
                        val spoken = "Here are the top headlines. " +
                            headlines.mapIndexed { i, h -> "${i + 1}. $h" }.joinToString(". ")
                        onResult(spoken)
                    } catch (e: Exception) {
                        onResult("I had trouble reading the news response")
                    }
                }
            }
        })
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
