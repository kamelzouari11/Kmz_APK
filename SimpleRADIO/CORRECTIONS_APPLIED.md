# Résumé des corrections apportées

## 1. Affichage Artiste/Titre corrigé ✓

### Problème identifié :
- Les métadonnées initiales mettaient `radio.name` dans `Title` et `radio.country` dans `Artist`
- L'affichage dans le player était inversé (Titre en haut, Artiste en bas)

### Solution appliquée :
- **Métadonnées initiales** : Maintenant `Artist = radio.name`, `Title` vide au départ
- **Affichage dans le player** : Artiste en haut (plus visible), Titre en bas
- **Extraction ICY** : Quand le flux envoie "Artiste - Titre", on extrait correctement

## 2. Boutons Précédent/Suivant dans la notification

### Problème :
- Un seul bouton Play/Pause visible
- Pas de navigation entre stations

### Solution :
- Utilisation de `setMediaItems()` au lieu de `setMediaItem()` 
- Toute la liste des radios est chargée dans le player
- Media3 active automatiquement les boutons Prev/Next quand il détecte plusieurs items
- Les boutons fonctionnent via Bluetooth et notification

## 3. Pochette d'album prioritaire sur logo station

### Implémentation :
- Au démarrage : Logo de la station affiché
- Dès qu'un titre est détecté : Recherche iTunes API
- Si pochette trouvée : 
  - Affichage dans l'app (600x600 HD)
  - **Mise à jour dynamique** via `replaceMediaItem()` pour notification/Bluetooth
  - La pochette remplace le logo dans tous les affichages

## 4. Lecture en arrière-plan continue

### Triple protection mise en place :

1. **WakeLock CPU (PARTIAL_WAKE_LOCK)** :
   - Empêche le processeur de s'endormir
   - S'active automatiquement quand la lecture commence
   - Se désactive en pause pour économiser la batterie

2. **WifiLock (WIFI_MODE_FULL_HIGH_PERF)** :
   - Maintient le WiFi actif en haute performance
   - Évite les coupures de streaming
   - Gestion automatique selon l'état de lecture

3. **Service robuste (START_STICKY)** :
   - Si Android tue le service par manque de mémoire
   - Il sera automatiquement relancé
   - Media3 gère le foreground service automatiquement

### Configuration ExoPlayer :
- `setWakeMode(C.WAKE_MODE_NETWORK)` : Maintient le réseau actif
- `setHandleAudioBecomingNoisy(true)` : Pause si écouteurs débranchés
- `setAudioAttributes(DEFAULT, true)` : Gestion audio focus automatique

## État actuel :
✓ Affichage Artiste/Titre corrigé
✓ Métadonnées initiales corrigées  
✓ Playlist complète chargée pour Prev/Next
✓ WakeLock + WifiLock actifs
✓ Mise à jour dynamique pochette d'album
⚠️ À tester : Notification avec boutons Prev/Next
⚠️ À tester : Lecture continue écran éteint
