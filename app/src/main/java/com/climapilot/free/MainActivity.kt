package com.climapilot.free

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.climapilot.free.ui.ControlScreen
import com.climapilot.free.ui.DevicesScreen
import com.climapilot.free.ui.OptionsTab
import com.climapilot.free.ui.ScenesTab
import com.climapilot.free.ui.StatusTab
import com.climapilot.free.ui.DisclaimerDialog
import com.climapilot.free.ui.DisclaimerPrefs
import com.climapilot.free.ui.DonationSheet
import com.climapilot.free.ui.HistoryScreen
import com.climapilot.free.ui.LockScreen
import com.climapilot.free.ui.MideaTheme
import com.climapilot.free.ui.SettingsScreen

/**
 * EN: The app's single Activity. Sets up edge-to-edge drawing, the Material theme and the Compose
 *     content tree. An optional "demo" intent extra jumps straight into the demo control screen.
 * DE: Die einzige Activity der App. Richtet randloses Zeichnen, das Material-Theme und den
 *     Compose-Inhaltsbaum ein. Ein optionales „demo"-Intent-Extra springt direkt in den Demo-Steuer-Bildschirm.
 */
class MainActivity : FragmentActivity() {
    // EN: One-time request so the sleep-timer countdown notification can show on Android 13+. / DE: Einmal-Anfrage, damit die Sleep-Timer-Countdown-Benachrichtigung ab Android 13 erscheinen darf.
    private val requestNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // EN: Allow a launcher shortcut / test to open straight into demo mode. / DE: Erlaubt einer Verknüpfung / einem Test, direkt im Demo-Modus zu starten.
        val startDemo = intent?.getBooleanExtra("demo", false) ?: false
        // EN: Refresh the launcher long-press shortcuts (off / scene / demo). / DE: Die Launcher-Shortcuts (Aus / Szene / Demo) aktualisieren.
        AppShortcuts.refresh(this)
        // EN: Count usage days for the rare, tasteful donation prompt. / DE: Nutzungstage für den seltenen, dezenten Spenden-Hinweis zählen.
        DonationPrompt.recordUsage(this)
        // EN: Ask for notification permission once (for the sleep-timer countdown). / DE: Einmal die Benachrichtigungs-Berechtigung anfragen (für den Sleep-Timer-Countdown).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    // EN: Optional biometric/PIN gate before anything else is shown. / DE: Optionale Biometrie-/PIN-Sperre, bevor sonst etwas gezeigt wird.
    var unlocked by rememberSaveable { mutableStateOf(!SettingsRepo.appLock(context)) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!unlocked) {
            if (activity != null && BiometricLock.canAuthenticate(activity)) {
                BiometricLock.prompt(activity) { unlocked = true }
            } else {
                // EN: Lock requested but no biometric/PIN set up — don't lock the user out. / DE: Sperre gewünscht, aber keine Biometrie/PIN eingerichtet — den Nutzer nicht aussperren.
                unlocked = true
            }
        }
    }
    if (!unlocked) {
        LockScreen(onUnlock = { activity?.let { BiometricLock.prompt(it) { unlocked = true } } })
        return
    }

    // EN: Enter demo mode once if launched with the demo flag. / DE: Einmal in den Demo-Modus wechseln, falls mit Demo-Flag gestartet.
    androidx.compose.runtime.LaunchedEffect(startDemo) {
        if (startDemo && vm.connectedDevice == null) vm.connectDemo()
    }
    var accepted by remember { mutableStateOf(DisclaimerPrefs.isAccepted(context)) }

    // EN: A connected device (or demo) means we show the control screen. / DE: Ein verbundenes Gerät (oder Demo) bedeutet, wir zeigen die Steuerung.
    val onControl = vm.connectedDevice != null
    var showSettings by remember { mutableStateOf(false) }

    // EN: Show the donation sheet once, when leaving a control session (a "you just used it" moment). / DE: Das Spenden-Sheet einmal zeigen, beim Verlassen einer Steuer-Session („du hast es gerade genutzt").
    var wasControl by remember { mutableStateOf(false) }
    var showDonation by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(onControl) {
        if (onControl) {
            wasControl = true
        } else if (wasControl) {
            wasControl = false
            if (DonationPrompt.shouldShow(context)) {
                DonationPrompt.markShown(context)
                showDonation = true
            }
        }
    }
    if (showDonation) {
        DonationSheet(
            onDismiss = { showDonation = false },
            onNever = { DonationPrompt.markNever(context); showDonation = false },
        )
    }
    // EN: App-wide "update available" prompt (download + confirm install). DE: App-weiter „Update verfügbar"-Hinweis (laden + Installation bestätigen).
    UpdateDialog(vm)
    when {
        showSettings -> SettingsScreen(vm = vm, onBack = { showSettings = false })
        else -> AnimatedContent(
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
            if (control) {
                // EN: IR mode is a focused, transmit-only screen (no tabs/readback). DE: Der IR-Modus ist ein fokussierter, reiner Sende-Bildschirm (keine Reiter/Readback).
                if (vm.irMode) ControlScreen(vm) else ConnectedScaffold(vm)
            } else {
                DevicesScreen(vm, onOpenSettings = { showSettings = true })
            }
        }
    }

    if (!accepted) {
        DisclaimerDialog(onAccept = {
            DisclaimerPrefs.setAccepted(context)
            accepted = true
        })
    }
}

/**
 * EN: The connected experience wrapped in a Scaffold with a bottom navigation bar — two destinations,
 *     "Steuern" (the control cards) and "Verlauf" (the per-AC charts). Replaces the old small chart icon
 *     in the hero panel with a clearly discoverable, persistent tab. The bottom inset is applied so the
 *     scrolling content never hides behind the bar.
 * DE: Die verbundene Ansicht in einem Scaffold mit unterer Navigationsleiste — zwei Ziele, „Steuern"
 *     (die Steuer-Karten) und „Verlauf" (die Charts pro Klima). Ersetzt das alte kleine Chart-Icon im
 *     Hero-Panel durch einen klar auffindbaren, festen Reiter. Der untere Rand wird berücksichtigt, damit
 *     der scrollende Inhalt nie hinter der Leiste verschwindet.
 */
@Composable
private fun ConnectedScaffold(vm: AcViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    // EN: Adaptive navigation — a bottom bar on compact width (portrait phone) and a side rail on wide
    //     width (landscape / tablet), so landscape doesn't waste vertical space on a bottom bar.
    // DE: Adaptive Navigation — untere Leiste bei kompakter Breite (Hochformat-Phone) und seitliche Leiste
    //     bei großer Breite (Querformat / Tablet), damit das Querformat keine Höhe an eine untere Leiste verliert.
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val destinations = listOf(
        Triple(0, Icons.Default.Thermostat, R.string.nav_control),
        Triple(1, Icons.Default.Tune, R.string.nav_options),
        Triple(2, Icons.Default.AutoAwesome, R.string.nav_scenes),
        Triple(3, Icons.Default.Speed, R.string.nav_status),
        Triple(4, Icons.Default.ShowChart, R.string.nav_history),
    )

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail {
                destinations.forEach { (i, icon, label) ->
                    NavigationRailItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(icon, null) },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) { ConnectedTabContent(vm, tab) }
        }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    destinations.forEach { (i, icon, label) ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { tab = i },
                            icon = { Icon(icon, null) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            },
        ) { inner ->
            Box(Modifier.fillMaxSize().padding(bottom = inner.calculateBottomPadding())) {
                ConnectedTabContent(vm, tab)
            }
        }
    }
}

/**
 * EN: The "update available" dialog, shown over any screen when the updater finds a newer GitHub
 *     release. Tapping Update downloads the APK (progress shown inline) and hands it to the system
 *     installer; the install itself is always confirmed by the user in the system UI.
 * DE: Der „Update verfügbar"-Dialog, über jedem Bildschirm gezeigt, wenn der Updater ein neueres
 *     GitHub-Release findet. Ein Tipp auf Aktualisieren lädt das APK (Fortschritt inline) und übergibt es
 *     dem System-Installer; die Installation bestätigt der Nutzer immer selbst in der System-Oberfläche.
 */
@Composable
private fun UpdateDialog(vm: AcViewModel) {
    val release = vm.updateAvailable ?: return
    val context = LocalContext.current
    val installed = remember { UpdateChecker.installedVersion(context) }
    val downloading = vm.updateProgress >= 0
    AlertDialog(
        onDismissRequest = { if (!downloading) vm.dismissUpdate() },
        icon = { Icon(Icons.Default.SystemUpdate, null) },
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.update_available_body, release.versionName, installed))
                if (release.notes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                        Text(release.notes, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (downloading) {
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { vm.updateProgress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.downloadAndInstallUpdate() }, enabled = !downloading) {
                Text(
                    if (downloading) stringResource(R.string.update_downloading, vm.updateProgress)
                    else stringResource(R.string.update_install),
                )
            }
        },
        dismissButton = {
            if (!downloading) {
                TextButton(onClick = { vm.dismissUpdate() }) { Text(stringResource(R.string.update_later)) }
            }
        },
    )
}

/** EN: Renders the content for the selected connected tab. DE: Rendert den Inhalt für den gewählten verbundenen Reiter. */
@Composable
private fun ConnectedTabContent(vm: AcViewModel, tab: Int) {
    when (tab) {
        0 -> ControlScreen(vm)
        1 -> OptionsTab(vm)
        2 -> ScenesTab(vm)
        3 -> StatusTab(vm)
        else -> HistoryScreen(vm)
    }
}
