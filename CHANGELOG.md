# Changelog

All notable changes to **ClimaPilot (Free)** are documented here · Alle wesentlichen Änderungen an **ClimaPilot (Free)**.

## [0.6.6] — 2026-07-25

### 🇬🇧 English
**New**
- **Indoor temperature calibration** (Options tab) — many units read a degree or two off the real room temperature. Compare against a room thermometer and dial in a correction of ±5 K in 0.5 K steps. The corrected value is shown and recorded everywhere (hero readout, widget, history), marked with a small amber delta, and a "measured → shown" line makes the effect concrete while dialling. Saved per device. Suggested on [r/MideaPortaSplit](https://www.reddit.com/r/MideaPortaSplit/).
- The correction also **shifts the setpoint sent to the unit** by the same whole degrees. The AC regulates against its own sensor, so a sensor reading 1 K low makes it stop 1 K short of the target; sending a setpoint lowered by 1 K cancels that out and the room actually reaches what you set. The Options card shows the shifted value ("Sent to the unit: 22° — target 23°").
- **Translations** — German, Spanish, French, Italian, Dutch and Portuguese, alongside English. The app language can be picked separately from the phone language (System settings ▸ Apps ▸ ClimaPilot ▸ Language).

**Fixed**
- **Target temperature now steps in whole degrees.** The protocol carries a half-degree bit, but Midea units ignore it and report the setpoint back rounded — so a ".5" step looked like it had applied and then silently snapped back on the next poll. Whole degrees everywhere now: in-app stepper, scene editor, home-screen widget, Wear OS and background scene/plan runs. Scenes saved with a half degree are snapped when applied.

### 🇩🇪 Deutsch
**Neu**
- **Innentemperatur-Kalibrierung** (Optionen-Reiter) — viele Geräte messen ein bis zwei Grad neben der echten Raumtemperatur. Mit einem Raumthermometer vergleichen und eine Korrektur von ±5 K in 0,5-K-Schritten einstellen. Der korrigierte Wert wird überall angezeigt und aufgezeichnet (Hero-Anzeige, Widget, Verlauf), gekennzeichnet mit einem kleinen bernsteinfarbenen Delta; eine Zeile „gemessen → angezeigt" macht die Wirkung beim Einstellen greifbar. Wird pro Gerät gespeichert. Angeregt auf [r/MideaPortaSplit](https://www.reddit.com/r/MideaPortaSplit/).
- Die Korrektur **verschiebt auch den an das Gerät gesendeten Sollwert** um dieselben ganzen Grad. Die Klima regelt gegen ihren eigenen Fühler; misst dieser 1 K zu niedrig, hört sie 1 K vor dem Ziel auf. Ein um 1 K abgesenkter Sollwert hebt das auf, und der Raum erreicht wirklich das Eingestellte. Die Optionen-Karte zeigt den verschobenen Wert („An das Gerät: 22° — Ziel 23°").
- **Übersetzungen** — Deutsch, Spanisch, Französisch, Italienisch, Niederländisch und Portugiesisch, zusätzlich zu Englisch. Die App-Sprache lässt sich getrennt von der Telefonsprache wählen (Systemeinstellungen ▸ Apps ▸ ClimaPilot ▸ Sprache).

**Behoben**
- **Die Zieltemperatur springt jetzt in ganzen Grad.** Das Protokoll führt ein Halbgrad-Bit mit, Midea-Geräte ignorieren es aber und melden den Sollwert gerundet zurück — ein „,5"-Schritt sah dadurch aus, als hätte er gegriffen, und sprang beim nächsten Poll still zurück. Jetzt überall ganze Grad: Regler in der App, Szenen-Editor, Homescreen-Widget, Wear OS und Szenen-/Plan-Ausführung im Hintergrund. Mit halbem Grad gespeicherte Szenen werden beim Anwenden eingerastet.

## [0.6.5] — 2026-07-20

### 🇬🇧 English
**New**
- **Configurable update interval** (Settings → Update interval): choose how often state, energy and diagnostics are polled while connected — 2/6/10/30 s (default 6 s, as before).
- **Diagnostics history (beta)** — when history recording is on, enabled diagnostic groups are recorded too, with three new charts on the History tab: compressor frequency, outdoor-unit power and indoor fan speed. Also sampled by the ~15-min background poll. Existing history data is preserved.

**Changed**
- **Beta diagnostics are now on by default** (each group can still be turned off under Settings → Beta features).

### 🇩🇪 Deutsch
**Neu**
- **Konfigurierbares Aktualisierungsintervall** (Einstellungen → Aktualisierungsintervall): wählbar, wie oft Zustand, Energie und Diagnose bei bestehender Verbindung abgefragt werden — 2/6/10/30 s (Standard 6 s, wie bisher).
- **Diagnose-Verlauf (Beta)** — bei aktiver Verlaufsaufzeichnung werden aktivierte Diagnose-Gruppen mit aufgezeichnet, mit drei neuen Charts im Verlauf-Reiter: Kompressorfrequenz, Leistung Außengerät und Innenlüfterdrehzahl. Wird auch vom ~15-min-Hintergrund-Poll erfasst. Bestehende Verlaufsdaten bleiben erhalten.

**Geändert**
- **Beta-Diagnose ist jetzt standardmäßig an** (jede Gruppe bleibt unter Einstellungen → Beta-Funktionen abschaltbar).

## [0.6.4] — 2026-07-20

### 🇬🇧 English
**New**
- **Diagnostics (beta)** — opt-in extra telemetry on the Status tab, enabled per group under Settings → Beta features: compressor frequency/current/voltage + refrigerant-circuit temperatures (T1–T4, discharge pipe), actual indoor fan speed + condensate-pump state, and real-time outdoor-unit power in watts. Ported from [midea-msmart PR #278](https://github.com/mill1000/midea-msmart/pull/278); verified on a PortaSplit. Not every unit reports these values.

**Fixed**
- **Outdoor Silent releases the compressor again** — turning silent mode off left the compressor capped at its low silent frequency until some other command was sent; the unit only re-evaluates the cap on a full state command, so the app now re-asserts the state when disabling silent mode ([#6](https://github.com/pit711/climapilot/issues/6)).
- **Outdoor Silent state is read back on connect** ([#6](https://github.com/pit711/climapilot/issues/6)) — the toggle now shows the unit's real silent-mode state after (re)connecting, instead of always starting at "off".
- **Compressor throttle 50 % / 75 % were swapped** ([#9](https://github.com/pit711/climapilot/issues/9)) — "50 %" sent the mild throttle and "75 %" the strong one. Re-measured against the compressor frequency and a power meter; the buttons now match msmart-ng's mapping, so "50 %" really is the stronger limit.
- **Imported tokens connect offline** ([#8](https://github.com/pit711/climapilot/issues/8)) — credentials imported from an msmart-ng export could be missed at connect time (different device-id encoding), causing a needless cloud fetch. Cached credentials are now also matched by IP.

**Changed**
- **Device search reaches more networks** ([#8](https://github.com/pit711/climapilot/issues/8)) — discovery now also sends subnet-directed broadcasts (e.g. 192.168.1.255) for routers that drop the global broadcast.

### 🇩🇪 Deutsch
**Neu**
- **Diagnose (Beta)** — freiwillige Zusatz-Telemetrie im Status-Reiter, je Gruppe unter Einstellungen → Beta-Funktionen aktivierbar: Kompressor-Frequenz/-Strom/-Spannung + Kältekreis-Temperaturen (T1–T4, Druckrohr), tatsächliche Innenlüfterdrehzahl + Kondensatpumpen-Status und Echtzeit-Leistung des Außengeräts in Watt. Portiert aus [midea-msmart PR #278](https://github.com/mill1000/midea-msmart/pull/278); an einer PortaSplit verifiziert. Nicht jedes Gerät liefert diese Werte.

**Behoben**
- **Außengerät leise gibt den Kompressor wieder frei** — nach dem Ausschalten des Leise-Modus blieb der Kompressor auf der niedrigen Leise-Frequenz begrenzt, bis irgendein anderer Befehl gesendet wurde; das Gerät bewertet die Begrenzung erst bei einem vollständigen Zustandsbefehl neu, daher sendet die App diesen beim Deaktivieren jetzt mit ([#6](https://github.com/pit711/climapilot/issues/6)).
- **Außengerät-leise-Zustand wird beim Verbinden zurückgelesen** ([#6](https://github.com/pit711/climapilot/issues/6)) — der Schalter zeigt nach dem (Neu-)Verbinden den echten Zustand des Geräts statt immer „aus".
- **Kompressor-Drossel 50 % / 75 % waren vertauscht** ([#9](https://github.com/pit711/climapilot/issues/9)) — „50 %" sendete die milde Drossel und „75 %" die starke. Gegen die Kompressorfrequenz und ein Leistungsmessgerät nachgemessen; die Knöpfe folgen jetzt msmart-ngs Zuordnung, „50 %" ist also wirklich die stärkere Begrenzung.
- **Importierte Tokens verbinden offline** ([#8](https://github.com/pit711/climapilot/issues/8)) — aus einem msmart-ng-Export importierte Zugangsdaten wurden beim Verbinden teils nicht gefunden (andere Geräte-ID-Kodierung) und unnötig aus der Cloud nachgeladen. Gecachte Zugangsdaten werden jetzt auch per IP zugeordnet.

**Geändert**
- **Gerätesuche erreicht mehr Netzwerke** ([#8](https://github.com/pit711/climapilot/issues/8)) — die Suche sendet zusätzlich subnetz-gerichtete Broadcasts (z. B. 192.168.1.255) für Router, die den globalen Broadcast verwerfen.

## [0.6.3] — 2026-07-01

### 🇬🇧 English
**Fixed**
- **Outdoor Silent** toggle now actually takes effect ([#6](https://github.com/pit711/climapilot/issues/6)). The app sent the wrong on-value for the outdoor-silent property, which the unit silently discarded — turning it on did nothing (no beep), while turning it off worked. It now sends the value the device expects (same as msmart-ng).

### 🇩🇪 Deutsch
**Behoben**
- **Außengerät leise** wirkt jetzt tatsächlich ([#6](https://github.com/pit711/climapilot/issues/6)). Die App sendete den falschen Ein-Wert für die Leise-Eigenschaft, den das Gerät stumm verwarf — Einschalten bewirkte nichts (kein Piep), Ausschalten funktionierte. Jetzt wird der vom Gerät erwartete Wert gesendet (wie msmart-ng).

## [0.6.2] — 2026-06-28

### 🇬🇧 English
**Fixed**
- **Beep / prompt tone** was lost after reconnecting (the toggle showed on, but the unit stayed silent). The setting is now saved and reapplied to every new connection.

**Changed**
- Added a note under the Beep toggle clarifying that many units (incl. the PortaSplit) only chirp when turning on/off, not on other changes.

### 🇩🇪 Deutsch
**Behoben**
- **Signalton** ging nach dem Neuverbinden verloren (der Schalter zeigte „an", das Gerät blieb aber stumm). Die Einstellung wird jetzt gespeichert und auf jede neue Verbindung angewendet.

**Geändert**
- Hinweis unter dem Beep-Schalter ergänzt: Viele Geräte (auch die PortaSplit) piepen nur beim Ein-/Ausschalten, nicht bei anderen Änderungen.

## [0.6.1] — 2026-06-28

### 🇬🇧 English
**Fixed**
- **Fan "Auto"** no longer jumps to full speed — selecting Auto now stays on Auto (and survives temperature/mode changes).
- **Ionizer** is no longer switched off whenever you change temperature, mode or apply a scene — its state is now carried in every command.
- **Swing, Display/LED and Ionizer** now reflect the unit's real state, read back from the AC (including changes you make on the physical remote), instead of a default guess.

**Changed**
- **Eco**: enabling eco now raises the target to 24 °C if it's lower, because the unit only accepts eco from 24 °C upward (this matches the Midea remote/app).
- **Filter reminder** clarified as an in-app run-time reminder — it does not read or reset the unit's own filter indicator (the local protocol has no such command).

### 🇩🇪 Deutsch
**Behoben**
- **Lüfter „Auto"** springt nicht mehr auf Volllast — „Auto" bleibt jetzt auf Auto (und überlebt Temperatur-/Modus-Änderungen).
- **Ionisierer** wird nicht mehr ausgeschaltet, wenn du Temperatur/Modus änderst oder eine Szene anwendest — sein Zustand wird jetzt in jedem Befehl mitgeführt.
- **Swing, Anzeige/LED und Ionisierer** zeigen jetzt den echten Gerätezustand, vom Gerät zurückgelesen (auch Änderungen an der Fernbedienung), statt einer Default-Annahme.

**Geändert**
- **Eco**: Beim Einschalten wird das Soll auf 24 °C angehoben, falls niedriger — das Gerät akzeptiert Eco erst ab 24 °C (wie die Midea-Fernbedienung/-App).
- **Filter-Erinnerung** als App-interner Laufzeit-Reminder klargestellt — sie liest/löscht nicht die Filteranzeige des Geräts (das lokale Protokoll hat dafür keinen Befehl).

## [0.6] — 2026-06-22

### 🇬🇧 English
**Added**
- **In-app updater** — ClimaPilot now checks GitHub for newer versions and installs them with a single tap (you confirm every install yourself — no silent updates). The automatic check on launch can be turned off under Settings.
- **Display / LED toggle** in Options — turn the indoor unit's panel light on or off.

### 🇩🇪 Deutsch
**Neu**
- **In-App-Updater** — ClimaPilot prüft jetzt GitHub auf neuere Versionen und installiert sie mit einem Tipp (jede Installation bestätigst du selbst — keine stillen Updates). Die automatische Prüfung beim Start lässt sich in den Einstellungen abschalten.
- **Anzeige-/LED-Schalter** in den Optionen — die Anzeige am Innengerät ein- oder ausschalten.

## [0.5] — 2026-06-22

### 🇬🇧 English
**Added**
- **Weekly day-planner** — assign scenes to recurring weekday + time windows (e.g. max cooling on Mondays, 6–18) on a visual week calendar. Each window applies its scene at the start and can switch the AC off at the end; enable/disable per window. The plan runs in the background even while the phone is idle on Wi-Fi (Doze-proof alarm-clock), survives reboots, and after a missed transition (deep sleep / phone off) it reconciles to the state the plan says should be active now.

**Notes / trade-offs**
- While a plan is active, Android shows the alarm-clock icon in the status bar (the cost of reliable, Doze-proof timing).
- For dependable firing, allow ClimaPilot in the background / disable battery optimisation (Settings ▸ Reliable timers), especially on Samsung/Xiaomi.
- The plan acts on your first/primary connected AC; saving or editing a plan never sends a command — windows only act at their scheduled times.

### 🇩🇪 Deutsch
**Neu**
- **Wochen-Tagesplaner** — Szenen wiederkehrenden Wochentag-+Zeit-Fenstern zuweisen (z. B. maximal kühlen montags 6–18) auf einem visuellen Wochenkalender. Jedes Fenster wendet beim Start seine Szene an und kann die Klima am Ende ausschalten; pro Fenster aktivierbar/deaktivierbar. Der Plan läuft im Hintergrund, auch wenn das Handy im WLAN ruht (Doze-fester Wecker-Alarm), übersteht Neustarts, und nach einer verpassten Umschaltung (Tiefschlaf / Handy aus) gleicht er auf den Zustand ab, den der Plan jetzt vorsieht.

**Hinweise / Trade-offs**
- Solange ein Plan aktiv ist, zeigt Android das Wecker-Symbol in der Statusleiste (Preis für zuverlässiges, Doze-festes Timing).
- Für verlässliches Auslösen die App im Hintergrund erlauben / Akku-Optimierung deaktivieren (Einstellungen ▸ Zuverlässige Timer), besonders bei Samsung/Xiaomi.
- Der Plan wirkt auf die erste/primäre verbundene Klima; Speichern oder Bearbeiten sendet nie einen Befehl — Fenster wirken nur zu ihren geplanten Zeiten.

## [0.4.2] — 2026-06-22

### 🇬🇧 English
**Fixed**
- The all-in-one home-screen widget failed to load ("Problem loading widget"): its connection dot was a bare `<View>`, which RemoteViews cannot inflate in the launcher process. It is now an `ImageView`.

**Added**
- IR remote: Swing, Quiet, Turbo and Eco toggles. The IR remote also remembers its last sent state (power, mode, temperature, fan and these toggles) and restores it when you re-enter IR mode.
- Fan speed: a 1% quick preset for minimal airflow.

### 🇩🇪 Deutsch
**Behoben**
- Das Alles-Widget auf dem Homescreen ließ sich nicht laden („Problem beim Laden des Widgets"): Der Verbindungspunkt war ein nacktes `<View>`, das RemoteViews im Launcher-Prozess nicht inflaten kann. Jetzt eine `ImageView`.

**Neu**
- IR-Fernbedienung: Swing-, Leise-, Turbo- und Eco-Schalter. Die IR-Fernbedienung merkt sich zudem ihren zuletzt gesendeten Zustand (Ein/Aus, Modus, Temperatur, Lüfter und diese Schalter) und stellt ihn beim erneuten Betreten des IR-Modus wieder her.
- Lüftergeschwindigkeit: 1%-Schnellwahl für minimalen Luftstrom.

## [0.4.1] — 2026-06-21

### 🇬🇧 English
**Added**
- IR-remote mode (infrared): control the AC like a remote on phones with an IR blaster — no Wi-Fi needed.

**Fixed**
- The app could not be updated over an earlier version; the app signing key was restored.

### 🇩🇪 Deutsch
**Neu**
- IR-Fernbedienungs-Modus (Infrarot): die Klima wie mit einer Fernbedienung steuern, auf Handys mit IR-Blaster — ohne WLAN.

**Behoben**
- Die App ließ sich nicht über eine ältere Version aktualisieren; der App-Signatur-Key wurde wiederhergestellt.

## [0.4] — 2026-06-21

### 🇬🇧 English
**Added**
- Tabbed navigation: Control · Options · Scenes · Status · History (side rail on tablets/landscape).
- Energy & filter history with charts (per hour/day/week/month or a chosen day, per AC); optional background recording (~15 min, Wi-Fi).
- Wear OS companion app.
- Reliable off/sleep timer with retries, reboot survival and a live countdown notification.
- Auto power-off after a maximum runtime; app lock (fingerprint/PIN); Quick Settings mode tile; launcher shortcuts; "reliable timers" setup helper.
- Redesigned ON/OFF slider and more compact mode & fan controls.

### 🇩🇪 Deutsch
**Neu**
- Reiter-Navigation: Steuern · Optionen · Szenen · Status · Verlauf (Seitenleiste auf Tablets/im Querformat).
- Energie- & Filter-Verlauf mit Diagrammen (pro Stunde/Tag/Woche/Monat oder gewähltem Tag, je Klima); optionale Hintergrund-Aufzeichnung (~15 min, WLAN).
- Wear-OS-Begleit-App.
- Zuverlässiger Aus-/Sleep-Timer mit Wiederholungen, Neustart-Überstehen und Live-Countdown-Benachrichtigung.
- Auto-Aus nach maximaler Laufzeit; App-Sperre (Fingerabdruck/PIN); Schnelleinstellungen-Modus-Kachel; Launcher-Shortcuts; „Zuverlässige Timer"-Einrichtungshelfer.
- Überarbeiteter EIN/AUS-Schieber und kompaktere Modus- & Lüfter-Steuerung.

## [0.3.2] — 2026-06-14

### 🇬🇧 English
**Fixed**
- Crash when starting the sleep timer on some devices (e.g. Xiaomi/HyperOS): the app now holds the USE_EXACT_ALARM permission and never crashes if exact alarms are unavailable.

### 🇩🇪 Deutsch
**Behoben**
- Absturz beim Starten des Sleep-Timers auf manchen Geräten (z. B. Xiaomi/HyperOS): Die App hält jetzt die USE_EXACT_ALARM-Berechtigung und stürzt nie ab, falls exakte Alarme nicht verfügbar sind.

## [0.3.1] — 2026-06-14

### 🇬🇧 English
**Fixed**
- Sleep timer reliability: the AC now powers off on time even when the app is closed and the phone is idle — the timer is scheduled as an exact alarm-clock that wakes the device from Doze. Previously the power-off could be deferred until you next opened the app.

### 🇩🇪 Deutsch
**Behoben**
- Zuverlässigkeit des Sleep-Timers: Die Klima schaltet jetzt pünktlich aus, auch wenn die App geschlossen ist und das Handy im Standby ist — der Timer wird als exakter Wecker-Alarm geplant, der das Gerät aus dem Doze weckt. Vorher konnte das Ausschalten verzögert werden, bis man die App das nächste Mal öffnete.

## [0.3] — 2026-06-14

### 🇬🇧 English
**Fixed**
- A saved device could appear twice after "Search devices" — discovery results are now de-duplicated against saved devices.
- The sleep timer only ran while the app was open; it now powers the AC off reliably even when the app is closed (via a system alarm).

**Changed — Home-screen widgets**
- Widgets reorganised by function: dedicated **power**, **temperature** and **mode** widgets, plus a redesigned **all-in-one** widget.
- Mode picker instead of cycling: all modes (Auto · Cool · Dry · Heat · Fan) as chips with the active one highlighted — tap to select directly.
- All-in-one widget redesigned: centered power button, a connection light (green = connected) instead of the device name, temperature with − / +, and the mode chips.

### 🇩🇪 Deutsch
**Behoben**
- Ein gespeichertes Gerät konnte nach „Geräte suchen" doppelt erscheinen — Suchtreffer werden jetzt gegen gespeicherte Geräte dedupliziert.
- Der Sleep-Timer lief bisher nur, solange die App geöffnet war. Er schaltet die Klima jetzt zuverlässig aus, auch wenn die App geschlossen ist (über einen System-Alarm).

**Geändert — Homescreen-Widgets**
- Widgets nach Funktion neu aufgeteilt: eigene Widgets für **Ein/Aus**, **Temperatur** und **Modus** — plus ein überarbeitetes **Alles-Widget**.
- Modus-Auswahl statt Durchschalten: alle Modi (Auto · Kühlen · Trocknen · Heizen · Lüften) als Chips, der aktive hervorgehoben — direkt antippbar.
- Alles-Widget neu gestaltet: zentrierter Ein/Aus-Knopf, ein Verbindungslicht (grün = verbunden) statt des Gerätenamens, Temperatur mit − / + und die Modus-Chips.

## [0.2] — 2026-06-14

### 🇬🇧 English
**Added**
- Quick scenes with a full editor (name, power, mode, temperature, fan, eco, swing) and an optional **daily schedule**.
- Live status: real-time **power**, total consumption, and an **estimated cost** from a configurable price per kWh.
- Options: swing, eco, beep — plus device-specific **ionizer**, **outdoor-silent** and **self-clean** (shown only when the unit reports them).
- Compressor throttle (where supported).
- Sleep timer with preset **and custom, remembered** durations.
- **Offline operation:** per-device token caching — internet is only needed the *first* time you connect a device; afterwards control is fully local. **Export & import** a device token in Settings.
- Saved devices on the device list for instant offline reconnect.
- **Reorderable** control cards via a drag handle (the order is saved).
- **Home-screen widgets** in 1×1, 2×2 and 4×2 sizes (power, temperature, mode) — control the AC offline from the home screen.
- **Quick Settings tile** to toggle power from the notification shade.
- Settings: **°C/°F** switch and **price per kWh**.

**Changed**
- Power button centered in the control hero.
- Live status no longer duplicates indoor/outdoor temperature (already shown in the hero).
- First-run notice explains the one-time online token fetch.
- Source code is now public, with bilingual (EN/DE) comments throughout.

**Fixed**
- Duplicate entries for the same saved device.

### 🇩🇪 Deutsch
**Neu**
- Schnell-Szenen mit vollem Editor (Name, Ein/Aus, Modus, Temperatur, Lüfter, Eco, Swing) und optionalem **Tagesplan**.
- Live-Status: **Leistung** in Echtzeit, Gesamtverbrauch und **geschätzte Kosten** über einen einstellbaren Preis pro kWh.
- Optionen: Swing, Eco, Signalton — plus gerätespezifisch **Ionisierer**, **Außen-Leise** und **Selbstreinigung** (nur wenn das Gerät sie meldet).
- Kompressor-Drossel (wo unterstützt).
- Sleep-Timer mit Vorgaben **und eigener, gemerkter** Dauer.
- **Offline-Betrieb:** Token-Caching pro Gerät — Internet nur beim *ersten* Verbinden nötig; danach vollständig lokal. Token in den Einstellungen **exportieren & importieren**.
- Gespeicherte Geräte in der Liste für sofortiges Offline-Wiederverbinden.
- **Umsortierbare** Steuer-Karten per Zieh-Griff (Reihenfolge wird gespeichert).
- **Homescreen-Widgets** in 1×1, 2×2 und 4×2 (Ein/Aus, Temperatur, Modus) — Klima offline vom Startbildschirm steuern.
- **Schnelleinstellungen-Kachel** zum Ein/Ausschalten aus der Statusleiste.
- Einstellungen: **°C/°F**-Umschaltung und **Preis pro kWh**.

**Geändert**
- Ein/Aus-Knopf in der Steuerung mittig.
- Live-Status zeigt Innen/Außen nicht mehr doppelt (steht schon im Hero).
- Erststart-Hinweis erklärt den einmaligen Online-Token-Abruf.
- Quellcode ist jetzt öffentlich, durchgehend zweisprachig (EN/DE) kommentiert.

**Behoben**
- Doppelte Einträge für dasselbe gespeicherte Gerät.

## [0.1] — 2026-06-09

- 🇬🇧 Initial release: local Wi-Fi control of Midea V3 air conditioners — power, mode, temperature, fan; demo mode.
- 🇩🇪 Erste Veröffentlichung: lokale WLAN-Steuerung von Midea-V3-Klimageräten — Ein/Aus, Modus, Temperatur, Lüfter; Demo-Modus.
