# État actuel des corrections

## ✅ Corrections appliquées

### 1. Lecture en arrière-plan
- **Supprimé** : FLAG_KEEP_SCREEN_ON de MainActivity (ne fonctionne pas en arrière-plan)
- **Ajouté** : WakeLock CPU (PARTIAL_WAKE_LOCK) dans PlaybackService
- **Ajouté** : WifiLock (HIGH_PERF) dans PlaybackService  
- **Stratégie** : Les locks sont acquis dès la lecture et **jamais relâchés** jusqu'à la destruction du service
- **Bonus** : onMediaItemTransition réacquiert les locks au changement de station

### 2. Playlist et boutons Prev/Next
- **Confirmé** : setMediaItems() est utilisé (lignes 582 et 613)
- **Confirmé** : Toute la liste est chargée dans le player
- **Théorie** : Media3 devrait afficher automatiquement les boutons Prev/Next

### 3. Affichage Artiste/Titre

#### Structure actuelle du player :
1. **75%** : Logo/Pochette (artworkUrl ?? radioStation.favicon)
2. **12.5%** : Nom radio / Country / Bitrate
3. **12.5%** : **Artiste** (en haut, primary color) / **Titre** (en bas, white)

#### Métadonnées initiales :
- Ligne 610 : `setArtist(r.name)` - Le nom de la radio est mis dans Artist
- Ligne 643 : `setArtist(radio.name)` - Idem pour onRadioSelected

#### Extraction ICY (lignes 767-788) :
- Si `rawArtist` est vide et `rawTitle` contient "Artiste - Titre"
  - On split et met `parts[0]` dans `artist`, `parts[1]` dans `title`
- Sinon on utilise directement `rawTitle` et `rawArtist`

## ❓ Questions pour l'utilisateur

**Dans la section métadonnées du player (en bas), que voyez-vous exactement ?**

Option A : Vous voyez le nom de la radio en haut et le pays en bas
Option B : Vous voyez les vraies métadonnées mais dans le mauvais ordre
Option C : Vous ne voyez rien du tout
Option D : Autre chose ?

**Exemple concret** : Si vous écoutez "Radio Paradise" qui joue "Pink Floyd - Comfortably Numb", que voyez-vous dans la section métadonnées ?

## 🔧 Prochaines étapes

1. Compiler et tester la lecture en arrière-plan
2. Vérifier si les boutons Prev/Next apparaissent dans la notification
3. Ajuster l'affichage Artiste/Titre selon le retour utilisateur
4. Vérifier la mise à jour de la pochette d'album dans la notification
