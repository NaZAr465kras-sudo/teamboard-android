# Team-Board – Android-App (Anleitung)

Diese App ist ein schlanker Starter: Sie öffnet das Team-Board als Vollbild-Chrome-Tab.
Mikrofon, Sprachfunktionen und die Anmeldung bei Claude funktionieren wie in Chrome.
Wichtig: Jeder Mitarbeiter braucht ein Claude-Konto und Zugriff auf den Link.

## APK bauen (auf dem MacBook)
1. Android Studio installieren: https://developer.android.com/studio
2. „Open“ → diesen Ordner (TeamBoardAndroid) wählen. Beim ersten Öffnen lädt Android Studio
   alles nötige selbst herunter (Gradle, SDK) – das dauert einige Minuten.
   Falls es fragt, ob Gradle/Plugin aktualisiert werden sollen: „Update“ bestätigen.
3. Menü: Build → Build Bundle(s) / APK(s) → Build APK(s).
4. Fertige Datei: app/build/outputs/apk/debug/app-debug.apk

## Auf dem Handy installieren
- Datei per USB, AirDrop-Ersatz (z. B. Google Drive/Telegram) aufs Handy senden und öffnen.
- Android fragt einmalig nach „Installation aus unbekannten Quellen“ – erlauben.

## Link ändern
Datei app/src/main/java/de/teamboard/app/MainActivity.kt → Konstante BOARD_URL.

## Alternative ohne Android Studio: APK von GitHub bauen lassen
1. Auf github.com ein neues (privates) Repository anlegen.
2. Den kompletten Inhalt dieses Ordners hochladen (inkl. des versteckten Ordners .github).
3. Reiter „Actions“ → „APK bauen“ → warten (ca. 5 Minuten).
4. Im fertigen Lauf unter „Artifacts“ die Datei TeamBoard-APK herunterladen (ZIP mit app-debug.apk).
