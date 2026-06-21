package com.climapilot.free.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.climapilot.free.R

/**
 * EN: Full-screen lock placeholder shown while the app is biometric-locked. The system prompt is
 *     launched automatically; this screen lets the user re-trigger it if they dismissed it.
 * DE: Bildschirmfüllender Sperr-Platzhalter, solange die App biometrisch gesperrt ist. Der
 *     System-Dialog wird automatisch geöffnet; dieser Bildschirm erlaubt erneutes Auslösen, falls er
 *     weggetippt wurde.
 */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Lock, null, tint = cs.primary, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.titleLarge, color = cs.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.lock_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onUnlock) { Text(stringResource(R.string.lock_unlock)) }
    }
}
