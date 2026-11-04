# Salati Time – Horaires de prière pour Android TV

Application Android TV (Kotlin) qui affiche les horaires de prière selon votre position et vous avertit
à l'heure de chaque prière, **quel que soit l'écran affiché** (chaîne TV, Netflix, YouTube…).

## Fonctionnalités

- **Horaires calculés hors-ligne** (algorithme astronomique PrayTimes) : Fajr, Chourouk, Dhuhr, Asr, Maghrib, Icha.
  - 14 méthodes : Ligue islamique mondiale, UOIF 12°, France 15°/18°, ISNA, Égypte, Oumm al-Qura, Maroc, Algérie, Tunisie, Turquie, Golfe, etc.
  - Asr standard ou hanafite, règle pour latitudes élevées, ajustement manuel ±15 min par prière.
- **Localisation** : détection automatique par la connexion internet (les TV n'ont pas de GPS), recherche de ville (OpenStreetMap) ou saisie des coordonnées.
- **Écran principal** : horloge, date grégorienne et hégirienne, prochaine prière avec compte à rebours.
- **Alertes, réglables pour chaque prière** :
  - 🔔 *Notification* : bannière affichée par-dessus n'importe quelle application + bip.
  - 🔊 *Adhan* : lecture de l'appel à la prière avec un panneau « Arrêter » (OK ou Retour sur la télécommande). Les autres applis sont mises en pause pendant l'adhan.
  - Rappel optionnel 5 à 30 min avant chaque prière.
- **Bibliothèque d'adhans** :
  - importer un **MP3** (sélecteur de fichiers, ou fichiers de la TV / clé USB) ;
  - coller un **lien YouTube** : seul l'audio est extrait et enregistré dans l'appli (lecture hors-ligne, sans vidéo ni pub) ;
  - lien direct vers un MP3 ;
  - un adhan intégré à l'application (islamcan.com – azan1), utilisé par défaut dès l'installation ;
  - un adhan par défaut au choix + un adhan spécifique possible pour Fajr (ou n'importe quelle prière).
- **Allumer la TV pour l'adhan** : si la TV est en veille à l'heure de la prière, l'appli rallume l'écran puis joue l'adhan
  (réglable : non / adhan seulement / adhan et notifications, avec un bouton de test). Nécessite le « démarrage rapide »
  de la TV ; une TV débranchée ou en veille profonde ne peut pas être rallumée.
- **Blocage de la TV après la prière** : écran plein écran avec compte à rebours, de 10 min à 2 h, réglable pour chaque prière.
  Le son est coupé et rien n'est utilisable. Le blocage reprend après extinction, redémarrage ou débranchement de la TV.
  Code de déblocage d'urgence facultatif.
- Reprogrammation automatique après redémarrage de la TV, changement d'heure ou de fuseau.

## Obtenir l'APK sans rien installer (GitHub, gratuit)

1. Créer un compte sur github.com, puis **New repository** (ex. `salati-time`), en privé si vous voulez.
2. Sur la page du dépôt : **uploading an existing file**, puis glisser **tout le contenu** du dossier `SalatiTimeTV`
   (y compris le dossier caché `.github`). Sur Mac, appuyer sur Cmd+Maj+. pour afficher les fichiers cachés.
   Ou, en ligne de commande : `git init && git add . && git commit -m init && git remote add origin <url> && git push -u origin main`
3. La compilation démarre seule (onglet **Actions**, ~5 min).
4. L'APK apparaît dans **Releases** → `SalatiTime.apk`.
   Lien toujours valable vers la dernière version :
   `https://github.com/smamos-cyber/salat-time/releases/latest/download/SalatiTime.apk`
   Sur la TV, l'application gratuite *Downloader* (code AFTVnews) permet d'ouvrir ce lien et d'installer l'APK.

Les APK sont signés avec une clé fixe (`app/salati-release.jks`) : chaque nouvelle version s'installe
par-dessus la précédente en conservant les réglages et les adhans importés.

## Compiler et installer avec Android Studio

1. Ouvrir le dossier `SalatiTimeTV` dans **Android Studio** (Ladybug ou plus récent, JDK 17).
2. Laisser Gradle synchroniser (télécharge Android Gradle Plugin 8.7, Kotlin 2.1, NewPipeExtractor depuis JitPack).
3. Générer la bannière et l'icône une fois : `pip install pillow && python3 tools/make_icons.py app/src/main/res`
   et télécharger l'adhan intégré : `curl -L -o app/src/main/res/raw/adhan_default.mp3 https://www.islamcan.com/audio/adhan/azan1.mp3`
   (Android Studio crée lui-même le wrapper Gradle à partir de `gradle/wrapper/gradle-wrapper.properties`.)
   Puis menu *Build > Build APK(s)*, ou en ligne de commande : `gradle assembleDebug`
   → `app/build/outputs/apk/debug/app-debug.apk`
4. Installer sur la TV :
   - activer *Options pour les développeurs > Débogage USB/ADB* sur la TV,
   - `adb connect <IP_DE_LA_TV>` puis `adb install -r app-debug.apk`,
   - ou copier l'APK sur une clé USB et l'ouvrir avec un gestionnaire de fichiers (autoriser les sources inconnues).

## Première utilisation

1. Au lancement, la position est détectée automatiquement (modifiable via **Lieu**).
2. Accepter **« Afficher par-dessus les autres applications »** : c'est ce qui permet de voir l'alerte sur n'importe quel écran.
   Sur certaines TV (Google TV notamment) cet écran n'existe pas ; l'accorder depuis un ordinateur :
   ```
   adb shell appops set com.salatitime.tv SYSTEM_ALERT_WINDOW allow
   ```
   Sans cette autorisation, l'appli affiche un message court (toast) à la place.
3. **Adhans** → ajouter un MP3 ou un lien YouTube. Le premier son importé devient le son par défaut.
4. Sur l'écran principal, cliquer une prière pour choisir son mode (Désactivé / Notification / Adhan), son son et son ajustement.
5. **Tester** pour vérifier la bannière et l'adhan par-dessus l'écran d'accueil.

## Bon à savoir

- Si la TV est **complètement éteinte** (et non en veille), aucune application ne peut jouer l'adhan.
  En veille, cela dépend du modèle : certaines TV suspendent toutes les applis.
- L'import YouTube utilise la bibliothèque open source NewPipeExtractor. YouTube modifie régulièrement son site :
  si l'import échoue, mettre à jour la version dans `app/build.gradle.kts`. Les conditions d'utilisation de YouTube
  n'autorisent pas le téléchargement ; privilégiez des adhans dont vous avez les droits ou un usage strictement personnel.
- Le calcul hégirien utilise Oumm al-Qura ; il peut différer d'un jour de l'observation locale du croissant.

## Structure du code

```
app/src/main/java/com/salatitime/tv/
├── prayer/   PrayTimes.kt (calcul), CalcMethod.kt, Prayer.kt
├── data/     Prefs.kt, LocationRepository.kt (IP / OpenStreetMap), AdhanRepository.kt, Http.kt
├── youtube/  YoutubeAudioImporter.kt, OkHttpDownloader.kt (NewPipeExtractor)
├── alarm/    PrayerScheduler.kt (AlarmManager), AlarmReceiver.kt, BootReceiver.kt,
│             AlertService.kt (son + notification), OverlayManager.kt (fenêtre par-dessus les applis)
└── ui/       MainActivity.kt, SettingsActivity.kt, AdhanActivity.kt + dialogues
```
