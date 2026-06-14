package com.climapilot.free

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.climapilot.free.ui.ControlScreen
import com.climapilot.free.ui.DevicesScreen
import com.climapilot.free.ui.DisclaimerDialog
import com.climapilot.free.ui.DisclaimerPrefs
import com.climapilot.free.ui.MideaTheme
import com.climapilot.free.ui.SettingsScreen

/**
 * EN: The app's single Activity. Sets up edge-to-edge drawing, the Material theme and the Compose
 *     content tree. An optional "demo" intent extra jumps straight into the demo control screen.
 * DE: Die einzige Activity der App. Richtet randloses Zeichnen, das Material-Theme und den
 *     Compose-Inhaltsbaum ein. Ein optionales „demo"-Intent-Extra springt direkt in den Demo-Steuer-Bildschirm.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // EN: Allow a launcher shortcut / test to open straight into demo mode. / DE: Erlaubt einer Verknüpfung / einem Test, direkt im Demo-Modus zu starten.
        val startDemo = intent?.getBooleanExtra("demo", false) ?: false
        setContent {
            MideaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    App(startDemo = startDemo)
                }
            }
        }
    }
}

/**
 * EN: Root composable. Decides which screen to show — devices list, control screen or settings —
 *     based on whether a device is connected, and overlays the first-run disclaimer until accepted.
 * DE: Wurzel-Composable. Entscheidet anhand der Verbindung, welcher Bildschirm gezeigt wird —
 *     Geräteliste, Steuerung oder Einstellungen — und blendet den Erststart-Hinweis ein, bis er akzeptiert ist.
 */
@Composable
private fun App(vm: AcViewModel = viewModel(), startDemo: Boolean = false) {
    // EN: Enter demo mode once if launched with the demo flag. / DE: Einmal in den Demo-Modus wechseln, falls mit Demo-Flag gestartet.
    androidx.compose.runtime.LaunchedEffect(startDemo) {
        if (startDemo && vm.connectedDevice == null) vm.connectDemo()
    }
    val context = LocalContext.current
    var accepted by remember { mutableStateOf(DisclaimerPrefs.isAccepted(context)) }

    // EN: A connected device (or demo) means we show the control screen. / DE: Ein verbundenes Gerät (oder Demo) bedeutet, wir zeigen die Steuerung.
    val onControl = vm.connectedDevice != null
    var showSettings by remember { mutableStateOf(false) }
    if (showSettings) {
        SettingsScreen(vm = vm, onBack = { showSettings = false })
    } else {
        AnimatedContent(
            targetState = onControl,
            transitionSpec = {
                if (targetState) {
                    (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "screen",
        ) { control ->
            if (control) ControlScreen(vm) else DevicesScreen(vm, onOpenSettings = { showSettings = true })
        }
    }

    if (!accepted) {
        DisclaimerDialog(onAccept = {
            DisclaimerPrefs.setAccepted(context)
            accepted = true
        })
    }
}
