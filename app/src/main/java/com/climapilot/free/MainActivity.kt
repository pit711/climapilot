package com.climapilot.free

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.HorizontalDivider
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
import com.climapilot.free.ui.ConnectedTopBar
import com.climapilot.free.ui.ControlScreen
import com.climapilot.free.ui.WideControlPane
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
/**
 * EN: Show or hide the status and navigation bars, following the "fullscreen" setting. Off by default:
 *     hiding the system bars costs people the clock, their notifications and the back gesture bar on a
 *     screen they usually open to change one setting and leave again. It stays available as a choice
 *     because on a wall-mounted tablet the launcher taskbar eats a strip of the control surface, and
 *     there the extra rows of pixels are worth more than the clock.
 * DE: Status- und Navigationsleiste ein- oder ausblenden, gemäß der Einstellung „Vollbild". Standardmäßig
 *     aus: Versteckte Systemleisten kosten Uhr, Benachrichtigungen und die Zurück-Geste auf einem
 *     Bildschirm, den man meist öffnet, um eine Einstellung zu ändern und wieder zu gehen. Als Wahl
 *     bleibt es erhalten, weil auf einem an der Wand hängenden Tablet die Taskleiste einen Streifen der
 *     Steuerfläche frisst — dort sind die zusätzlichen Pixelzeilen mehr wert als die Uhr.
 */
fun applySystemBars(activity: Activity) {
    WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
        // EN: In fullscreen the bars stay one swipe from the edge away and hide again by themselves,
        //     so nothing ever becomes unreachable. DE: Im Vollbild bleiben die Leisten einen Wisch vom
        //     Rand entfernt erreichbar und verbergen sich danach von selbst — nichts wird unerreichbar.
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (SettingsRepo.fullscreen(activity)) {
            hide(WindowInsetsCompat.Type.systemBars())
        } else {
            show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

class MainActivity : FragmentActivity() {
    // EN: One-time request so the sleep-timer countdown notification can show on Android 13+. / DE: Einmal-Anfrage, damit die Sleep-Timer-Countdown-Benachrichtigung ab Android 13 erscheinen darf.
    private val requestNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** EN: Ask for the notification permission, if it isn't granted already. DE: Die Benachrichtigungs-Berechtigung anfragen, sofern noch nicht erteilt. */
    fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // EN: A dialog or a pulled-down bar hands focus back; re-apply what the user asked for.
    // DE: Ein Dialog oder eine heruntergezogene Leiste gibt den Fokus zurück — wieder anwenden, was
    //     der Nutzer eingestellt hat.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBars(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applySystemBars(this)
        // EN: Allow a launcher shortcut / test to open straight into demo mode. / DE: Erlaubt einer Verknüpfung / einem Test, direkt im Demo-Modus zu starten.
        val startDemo = intent?.getBooleanExtra("demo", false) ?: false
        // EN: Refresh the launcher long-press shortcuts (off / scene / demo). / DE: Die Launcher-Shortcuts (Aus / Szene / Demo) aktualisieren.
        AppShortcuts.refresh(this)
        // EN: Count usage days for the rare, tasteful donation prompt. / DE: Nutzungstage für den seltenen, dezenten Spenden-Hinweis zählen.
        DonationPrompt.recordUsage(this)
        // EN: Ask for notification permission once (for the sleep-timer countdown). DE: Einmal die
        //     Benachrichtigungs-Berechtigung anfragen (für den Sleep-Timer-Countdown).
        askNotificationPermission()
        setContent {
            MideaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // EN: Keep the content out from under the status bar and any display cutout. The
                    //     bottom edge is deliberately left alone: the navigation bar has to reach the
                    //     screen edge and colour the strip behind the gesture bar, so each screen
                    //     handles its own bottom inset. With the bars hidden these are simply zero.
                    // DE: Den Inhalt unter Statusleiste und Display-Aussparung hervorholen. Der untere
                    //     Rand bleibt bewusst frei: Die Navigationsleiste muss bis zur Bildschirmkante
                    //     reichen und den Streifen hinter der Gestenleiste einfärben — den unteren
                    //     Rand behandelt daher jeder Bildschirm selbst. Bei versteckten Leisten sind
                    //     diese Abstände schlicht null.
                    Box(
                        Modifier.fillMaxSize().windowInsetsPadding(
                            WindowInsets.safeDrawing
                                .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                        )
                    ) {
                        App(startDemo = startDemo)
                    }
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
        showSettings -> AboveNavigationBar { SettingsScreen(vm = vm, onBack = { showSettings = false }) }
        // EN: On a tablet both screens fit side by side: the device list stays put on the left and
        //     tapping an entry only swaps the right half. No screen change, no back button — and
        //     switching between rooms costs one tap instead of three.
        // DE: Auf dem Tablet passen beide Bildschirme nebeneinander: die Geräteliste bleibt links
        //     stehen, ein Antippen tauscht nur die rechte Hälfte. Kein Bildschirmwechsel, kein
        //     Zurück — und der Wechsel zwischen Räumen kostet einen Tipper statt drei.
        LocalConfiguration.current.screenWidthDp >= 900 -> Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(360.dp).fillMaxHeight()) {
                DevicesScreen(vm, onOpenSettings = { showSettings = true })
            }
            VerticalDivider()
            Box(Modifier.weight(1f).fillMaxHeight()) {
                AnimatedContent(
                    targetState = onControl,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "detail",
                ) { control ->
                    if (control) {
                        if (vm.irMode) AboveNavigationBar { ControlScreen(vm) } else ConnectedScaffold(vm)
                    } else {
                        NoDeviceSelected()
                    }
                }
            }
        }

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
                if (vm.irMode) AboveNavigationBar { ControlScreen(vm) } else ConnectedScaffold(vm)
            } else {
                DevicesScreen(vm, onOpenSettings = { showSettings = true })
            }
        }
    }

    // EN: First-run disclaimer, accepted once. DE: Erststart-Hinweis, einmal zu bestätigen.
    if (!accepted) {
        DisclaimerDialog(onAccept = {
            DisclaimerPrefs.setAccepted(context)
            accepted = true
        })
    }
}

/**
 * EN: Holds a full-screen page clear of the navigation bar. Screens that carry their own bottom bar
 *     get that spacing from their Scaffold; the ones that don't — settings, the IR remote — would
 *     otherwise end their last row underneath the gesture bar.
 * DE: Hält eine bildschirmfüllende Seite von der Navigationsleiste frei. Bildschirme mit eigener
 *     unterer Leiste bekommen diesen Abstand von ihrem Scaffold; die ohne — Einstellungen, die
 *     IR-Fernbedienung — ließen ihre letzte Zeile sonst unter der Gestenleiste enden.
 */
@Composable
private fun AboveNavigationBar(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().navigationBarsPadding()) { content() }
}

/**
 * EN: The connected experience wrapped in a Scaffold with a bottom navigation bar. The bar sits at the
 *     bottom on every screen size: a side rail put the destinations in a different place depending on
 *     how you held the device, and on a tablet it also competed with the device list already occupying
 *     the left edge. The bottom inset is applied so scrolling content never hides behind the bar.
 * DE: Die verbundene Ansicht in einem Scaffold mit unterer Navigationsleiste. Die Leiste sitzt auf jeder
 *     Bildschirmgröße unten: Eine seitliche Leiste legte die Ziele je nach Geräthaltung woandershin und
 *     stritt auf dem Tablet zusätzlich mit der Geräteliste, die den linken Rand schon belegt. Der untere
 *     Rand wird berücksichtigt, damit scrollender Inhalt nie hinter der Leiste verschwindet.
 */
@Composable
private fun ConnectedScaffold(vm: AcViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val destinations = listOf(
        Triple(0, Icons.Default.Thermostat, R.string.nav_control),
        Triple(1, Icons.Default.Tune, R.string.nav_options),
        Triple(2, Icons.Default.AutoAwesome, R.string.nav_scenes),
        Triple(3, Icons.Default.Speed, R.string.nav_status),
        Triple(4, Icons.Default.ShowChart, R.string.nav_history),
    )

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

/**
 * EN: Renders the content for the selected connected tab. Where there is room — a tablet in
 *     landscape — the control tab stops being a single column and shows options and live status
 *     beside it, so the three things you look at while adjusting the unit are visible at once
 *     instead of one tab apart. Measured on the actual pane, not the window, because the device
 *     list already takes its share of the width.
 * DE: Rendert den Inhalt für den gewählten Reiter. Wo Platz ist — Tablet im Querformat — hört der
 *     Steuer-Reiter auf, eine einzelne Spalte zu sein, und zeigt Optionen und Live-Status daneben:
 *     die drei Dinge, auf die man beim Einstellen schaut, gleichzeitig statt einen Reiter
 *     auseinander. Gemessen wird die tatsächliche Fläche, nicht das Fenster, denn die Geräteliste
 *     hat sich ihren Teil der Breite schon genommen.
 */
@Composable
private fun ConnectedTabContent(vm: AcViewModel, tab: Int) {
    // EN: One control surface for every screen size — it lays itself out in one column or two. It used
    //     to switch to a completely different screen below 820dp, so the redesign only ever reached
    //     tablets while phones kept the old gradient-and-tiles look.
    // DE: Eine Steuerfläche für jede Bildschirmgröße — sie ordnet sich selbst ein- oder zweispaltig.
    //     Bisher wechselte sie unter 820dp auf einen völlig anderen Bildschirm, wodurch die
    //     Neugestaltung nur Tablets erreichte und Handys beim alten Verlauf-und-Kacheln-Look blieben.
    Box(Modifier.fillMaxSize()) {
        when (tab) {
            0 -> WideControlPane(vm)
            1 -> OptionsTab(vm)
            2 -> ScenesTab(vm)
            3 -> StatusTab(vm)
            else -> HistoryScreen(vm)
        }
    }
}


/**
 * EN: Right-hand pane before a device is chosen. It names what to do rather than apologising for
 *     being empty — the list is right there on the left.
 * DE: Die rechte Hälfte, solange kein Gerät gewählt ist. Sie sagt, was zu tun ist, statt sich für
 *     ihre Leere zu entschuldigen — die Liste steht ja links daneben.
 */
@Composable
private fun NoDeviceSelected() {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Thermostat,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = cs.onSurfaceVariant.copy(alpha = 0.35f),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.pane_pick_device),
                style = MaterialTheme.typography.titleMedium,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.pane_pick_device_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant.copy(alpha = 0.75f),
            )
        }
    }
}
