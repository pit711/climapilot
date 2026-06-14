package com.climapilot.free.ui

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.climapilot.free.R

/**
 * EN: Persists whether the user accepted the first-run disclaimer.
 * DE: Speichert, ob der Nutzer den Erststart-Hinweis akzeptiert hat.
 */
object DisclaimerPrefs {
    private const val PREFS = "midea_app"
    private const val KEY = "disclaimer_accepted"

    fun isAccepted(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setAccepted(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()
    }
}

/**
 * EN: First-run disclaimer (independent app, not affiliated with Midea; use at your own risk). Must
 *     be accepted to continue; it cannot be dismissed by tapping outside.
 * DE: Erststart-Hinweis (unabhängige App, nicht mit Midea verbunden; Nutzung auf eigene Gefahr). Muss
 *     zum Fortfahren akzeptiert werden; lässt sich nicht durch Tippen außerhalb schließen.
 */
@Composable
fun DisclaimerDialog(onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = { /* non-dismissable: requires explicit acceptance */ },
        title = { Text(stringResource(R.string.disclaimer_title)) },
        text = { Text(stringResource(R.string.disclaimer_body)) },
        confirmButton = {
            Button(onClick = onAccept) { Text(stringResource(R.string.disclaimer_accept)) }
        },
    )
}
