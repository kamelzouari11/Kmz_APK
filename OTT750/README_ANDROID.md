# Guide: Exécuter l'application sur Android

Ce script `editor_favoris.py` utilise l'interface graphique **Tkinter**. 
Il existe plusieurs façons de l'utiliser sur Android, mais **Android Studio** n'est pas la méthode directe pour un script Python/Tkinter.

## Option 1 : Pydroid 3 (La plus simple et rapide)
C'est la méthode recommandée pour utiliser le script tel quel sans le réécrire.

1.  **Installer Pydroid 3** depuis le Google Play Store sur votre téléphone.
2.  **Transférer les fichiers** sur votre téléphone (dans le dossier `Download`) :
    *   `editor_favoris.py`
    *   `database.db`
3.  **Ouvrir Pydroid 3** :
    *   Allez dans le menu > Open > Navigate to `Download` > Select `editor_favoris.py`.
4.  **Lancer** : Appuyez sur le bouton "Play".

### Commande pour transférer via PC (ADB)
Si votre téléphone est connecté en USB avec le "Débogage USB" activé :
```bash
adb push editor_favoris.py /storage/emulated/0/Download/
adb push database.db /storage/emulated/0/Download/
```

---

## Option 2 : Android Studio (Pour créer une vraie application native)
**Attention :** Android Studio ne supporte pas **Tkinter**. 
Pour utiliser Android Studio, vous devez transformer ce projet.

1.  **Créer un nouveau projet** dans Android Studio (Select "Empty Views Activity").
2.  **Installer le plugin Chaquopy** dans le fichier `build.gradle` pour pouvoir exécuter du code Python.
3.  **Réécrire l'interface (UI)** :
    *   Vous ne pouvez PAS utiliser `tkinter`.
    *   Vous devez recréer les boutons, listes et menus en **XML** ou **Jetpack Compose** (Kotlin).
    *   Le code Python (`editor_favoris.py`) ne servira que de "backend" pour gérer la base de données SQLite.

## Option 3 : Buildozer (Pour créer un APK depuis Python)
Si vous voulez un fichier `.apk` installable sans passer par Android Studio :

1.  Il est fortement conseillé de réécrire l'interface avec **Kivy** ou **Flet** (car Tkinter est très mal supporté et donne un look "Windows 95").
2.  Utiliser l'outil `buildozer` sur Linux pour compiler l'APK.
