# Changelog

All notable changes to **ClimaPilot (Free)** are documented here · Alle wesentlichen Änderungen an **ClimaPilot (Free)**.

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
