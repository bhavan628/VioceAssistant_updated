package com.example.voiceassistant.handlers

import android.content.Context
import android.provider.ContactsContract

/** Shared by MessageHandler and CallHandler — looks up a contact's phone number by
 *  fuzzy-matching a spoken name against the device's contact list. */
object ContactLookup {

    data class Contact(val name: String, val phoneNumber: String)

    fun find(context: Context, spokenName: String): Contact? {
        val resolver = context.contentResolver
        val cursor = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        ) ?: return null

        var best: Contact? = null
        var bestScore = Int.MAX_VALUE
        val target = spokenName.lowercase().trim()

        cursor.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = it.getString(nameIdx) ?: continue
                val number = it.getString(numberIdx) ?: continue
                val score = editDistance(target, name.lowercase())
                if (score < bestScore) {
                    bestScore = score
                    best = Contact(name, number)
                }
            }
        }

        val threshold = (target.length * 0.65).toInt().coerceAtLeast(3)
        return if (bestScore <= threshold) best else null
    }

    private fun editDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        return dp[a.length][b.length]
    }
}
