# Changelog

All notable changes to **ClimaPilot (Free)** are documented here · Alle wesentlichen Änderungen an **ClimaPilot (Free)**.

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
