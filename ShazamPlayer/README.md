# ShazamPlayer — importer sa bibliothèque sur Android

## Fichier source

Utiliser **SyncedSongs.csv**, extrait de l'archive officielle Shazam. Aucune conversion
n'est nécessaire. L'application lit `date`, `artist` et `title` par leur en-tête et
ignore les champs de statut et de localisation. Les identifications répétées sont
conservées avec leur date. Les liens et identifiants Shazam ne sont pas obligatoires :
la recherche NewPipe utilise l'artiste et le titre.

Un CSV UTF-8 personnel est également accepté :

```csv
date,artist,title
2026-09-06T15:00:25.286Z,Outkast,Hey Ya!
```

L'ordre des colonnes est libre. Une date doit commencer par `AAAA-MM-JJ`.
Les champs contenant des virgules doivent être entre guillemets ; un guillemet
interne s'écrit `""`. L'ancien format `Index,TagTime,Title,Artist,URL,TrackKey`
reste pris en charge.

## Installation et utilisation sur Samsung S22

1. Installer l'APK compilé `app/build/outputs/apk/debug/app-debug.apk`.
   Pour une mise à jour, conserver la même signature que l'installation existante.
2. Copier **SyncedSongs.csv** dans **Stockage interne → Download** sur le téléphone
   (dossier affiché comme **Téléchargements**, chemin `/storage/emulated/0/Download/SyncedSongs.csv`).
3. Ouvrir ShazamPlayer, toucher **Importer SyncedSongs.csv**, puis sélectionner le
   fichier dans Téléchargements via le sélecteur Android.
4. Installer NewPipe officiel (`org.schabi.newpipe`). Dans ShazamPlayer, utiliser
   **Autoriser le moteur NewPipe** et activer l'accès aux notifications de
   ShazamPlayer, nécessaire au mécanisme de recherche existant, puis revenir à l'app.
5. Filtrer la bibliothèque si souhaité, puis utiliser **Compléter avec NewPipe**
   et les commandes de lecture existantes. La recherche et la lecture nécessitent
   une connexion Internet. Le parcours existant traite au plus 100 morceaux par lot.

Après import, une copie du CSV est conservée dans le stockage privé de l'application :
aucun chemin permanent ni autorisation d'accès global au stockage n'est nécessaire.
Le fichier original peut rester dans Téléchargements pour sauvegarde. Le remplacer
sur le téléphone ne met pas automatiquement la bibliothèque à jour : **réimporter**
le nouvel export. Chaque import valide remplace la bibliothèque précédente ; un
fichier refusé la laisse intacte. Effacer les données de l'app ou la désinstaller
supprime sa copie interne.

## Vérification

```sh
./gradlew testDebugUnitTest assembleDebug
```
