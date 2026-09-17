# SimpleRADIO

## Procédure d'affichage des pochettes et logos

Ce document décrit la procédure réellement utilisée par le player. Son objectif est de fournir
une image pertinente sans bloquer l'interface, tout en limitant les faux résultats et les
recherches réseau répétitives.

### Cascade générale

Pour chaque changement de station, d'artiste ou de titre, le player suit cet ordre :

```text
Métadonnées artiste + titre disponibles ?
    |
    +-- oui --> pochette exacte artiste + titre
    |               |
    |               +-- trouvée --> affichage de la pochette
    |               |
    |               +-- absente --> recherche avec titre et artiste inversés
    |                                   |
    |                                   +-- trouvée --> affichage de cette pochette
    |                                   |
    |                                   +-- absente --> procédure du logo radio
    |
    +-- non --> procédure du logo radio
```

Une seule image est finalement placée dans `currentArtworkUrl`. Elle est utilisée par le player,
la notification/média-session et Chromecast.

Code d'orchestration :
[`PlaybackManagement.kt`](app/src/main/java/com/example/simpleradio/ui/components/PlaybackManagement.kt).

## 1. Pochette exacte : artiste et titre

La recherche est effectuée seulement lorsque l'artiste **et** le titre sont présents.

### Préparation des métadonnées

Avant la recherche, le titre est nettoyé afin de supprimer les annotations courantes provenant
des flux radio :

- année finale entre parenthèses ou crochets ;
- `Remaster`, `Remastered`, `2003 Remaster`, etc. ;
- `Radio Edit`, `Single Version` et `Album Version` ;
- album ajouté à la suite du titre par certaines radios ;
- différences de casse, accents, ponctuation et préfixe facultatif `The`.

### Sources et priorité

1. **Apple Search API** : recherche gratuite et sans clé parmi les morceaux.
2. **MusicBrainz** : recherche d'un enregistrement officiel correspondant.
3. **Cover Art Archive** : récupération de la pochette `front-1200` du release-group MusicBrainz.

Un résultat Apple n'est retenu que si l'artiste correspond et si le titre normalisé correspond.
Pour les titres, une correspondance exacte est prioritaire. Une correspondance par inclusion est
acceptée uniquement lorsque les deux titres partagent au moins deux mots significatifs. Cette
règle permet de reconnaître certaines variantes sans accepter un résultat basé sur un seul mot.

La recherche complète est limitée à 9 secondes. Les succès et les échecs sont mémorisés pendant
la session afin de ne pas refaire la même requête à chaque mise à jour des métadonnées.

Implémentation :
[`CoverArtProvider.kt`](app/src/main/java/com/example/simpleradio/data/api/CoverArtProvider.kt).

## 2. Fallback : métadonnées inversées

Si la recherche `artiste + titre` échoue, certaines radios pouvant publier les deux champs dans le
mauvais ordre, la même recherche de pochette est relancée avec `titre + artiste`. Les mêmes règles
de correspondance Apple, MusicBrainz et Cover Art Archive restent appliquées.

Si cette deuxième recherche échoue également, ou si l'artiste et le titre ne sont pas tous les deux
disponibles, le player lance directement la procédure du logo radio.

## 3. Procédure du logo radio

### 3.1 Décisions locales prioritaires

Avant toute nouvelle recherche réseau :

1. Si l'utilisateur a déjà confirmé une URL pour le `stationuuid`, elle est utilisée immédiatement.
2. Si Serper n'est pas configuré, le cache Room `station_logo_cache` est consulté.
3. Une entrée de cache des sources de secours est valable pendant 30 jours.
4. Une entrée expirée est supprimée puis la recherche recommence.

### 3.2 Source principale : Google Images via Serper

La recherche principale utilise l'API `POST https://google.serper.dev/images`. La clé est lue depuis
`local.properties` et ne doit jamais être commitée :

```properties
serper.api.key=VOTRE_CLE_API
```

La requête contient uniquement `<nom de la radio> logo svg png <pays>` (le pays est omis
lorsqu'il est inconnu). Le pays reste dans le texte de la requête ; aucun paramètre géographique
Serper séparé n'est envoyé. L'application conserve les résultats dont `imageWidth >= 256` et
`imageHeight >= 256`, les classe par surface décroissante (`imageWidth * imageHeight`), puis retient
les dix premiers. Aucun score, filtre de domaine ou dédoublonnage n'est appliqué. Ces dix candidats
sont gardés en mémoire afin que le bouton **X** affiche immédiatement le suivant sans consommer une
nouvelle requête Serper.

Chaque candidat conserve aussi son `thumbnailUrl`. Si Coil ne peut pas charger ou décoder
`imageUrl`, l'application remplace automatiquement cette URL par `thumbnailUrl` sans avancer le
compteur. L'utilisateur valide donc l'image de secours réellement affichée.

Une clé placée dans une application Android peut être extraite de l'APK. Pour une publication
publique, il faut donc appeler Serper à travers un proxy serveur protégé par quotas.

### 3.3 Détermination du site officiel

Le repository utilise en priorité le champ `homepage` de la station. S'il est absent, il redemande
la station à Radio Browser par son UUID. En dernier recours, le domaine du favicon natif peut être
utilisé uniquement lorsqu'il correspond à un mot significatif du nom de la radio.

La résolution de la homepage est limitée à 1,5 seconde et son résultat est mémorisé en mémoire.

### 3.4 Sources de secours

Si Serper n'est pas configuré ou ne retourne aucune URL, le champ `favicon` de Radio
Browser est contrôlé : raster d'au moins `512 x 512`, SVG accepté, GIF et URL de bannière/publicité
rejetés. Un favicon natif valide est mis en cache et affiché sans confirmation.

Si ce favicon est également inutilisable, trois recherches de secours sont lancées en parallèle,
avec une limite globale de 7 secondes :

1. **Site officiel**
2. **Hunter Logo** à partir du domaine officiel
3. **Logo.dev**

La priorité de sélection effective est :

1. logo explicite provenant du site officiel ;
2. Hunter Logo ;
3. Logo.dev ;
4. `og:image` du site officiel, uniquement en dernier recours.

#### Extraction sur le site officiel

La page HTML est limitée à 600 000 caractères. Les candidats sont extraits dans cet ordre :

1. logo déclaré dans les données structurées JSON/Schema ;
2. `og:logo` ;
3. `apple-touch-icon` ;
4. icônes du Web App Manifest ;
5. `<link rel="icon">` ;
6. `/favicon.ico` ;
7. `og:image`.

Tous les candidats passent par le même contrôle d'image et la taille raster minimale de
`512 x 512`. Comme `og:image` peut être une photo d'article ou une bannière, il n'est accepté que
si son URL évoque un logo/marque/radio ou si son ratio est approximativement carré.

#### Hunter Logo

Hunter est appelé uniquement avec un domaine officiel déjà déterminé :

```text
https://logos.hunter.io/domaine-officiel.tld
```

Aucune recherche Hunter n'est tentée à partir d'un domaine de flux non vérifié.

#### Logo.dev

La clé publique est lue depuis `local.properties` :

```properties
logo.dev.publishable.key=VOTRE_CLE_PUBLIQUE
```

La clé est injectée dans `BuildConfig.LOGO_DEV_PUBLISHABLE_KEY`. Elle ne doit jamais être écrite
directement dans un fichier Kotlin ni commitée dans Git.

Les recherches par nom ajoutent systématiquement le mot `radio` :

```text
radio <nom de la radio> <pays>
radio <nom de la radio>
```

Le domaine du flux n'est essayé que s'il correspond au nom de la radio. Les images demandées à
Logo.dev utilisent le format PNG, le mode Retina et `fallback=404` afin de ne pas accepter une
image générique.

Il n'existe aucun scraping HTML direct de Google Images, Brave, Firefox ou Opera : la recherche
Google Images passe exclusivement par l'API Serper.

## 4. Validation par l'utilisateur

Les logos provenant de sources autres que le favicon natif Radio Browser doivent être validés dans
le player :

- bouton rouge **X** : rejeter l'URL ;
- bouton vert **✓** : confirmer l'URL pour cette station ;
- bouton vert de logo déjà confirmé : annuler la confirmation.

Après annulation, l'image reste affichée et les boutons confirmer/rejeter réapparaissent. Le bouton
**X** passe simplement au candidat Serper suivant, sans exclure ni mémoriser le précédent. Le
compteur `1/10`, `2/10`, etc. indique la position après classement par surface. Après le dernier
candidat, le player revient à son icône radio générique.

Seules les confirmations sont associées au `stationuuid` dans `SharedPreferences` :

```text
confirmed_station_logo_<stationuuid>
```

Le backup GitHub version 2 transporte aussi les confirmations sous forme d'une table
`stationuuid -> URL du logo`. Lors d'un téléchargement, un logo GitHub remplace le logo local de la
même station. Si GitHub ne contient aucune confirmation pour un `stationuuid`, la confirmation
locale correspondante est conservée. Les anciens backups sans table de logos restent compatibles.

Implémentation de l'état utilisateur :
[`MainViewModel.kt`](app/src/main/java/com/example/simpleradio/ui/MainViewModel.kt).

## 5. Affichage et contraintes de performance

- Coil affiche l'image finale dans `ArtworkDisplay` avec `ContentScale.Fit`.
- `coil-svg` permet de décoder les logos vectoriels.
- Les accès réseau et le décodage des dimensions sont exécutés hors du thread principal.
- Les recherches sont annulées automatiquement lorsque la station ou les métadonnées changent,
  grâce au `LaunchedEffect` du player.
- Les délais courts, le cache mémoire et le cache Room évitent qu'une recherche de logo bloque le
  player ou soit répétée à chaque recomposition.
- Si toutes les étapes échouent, aucune image distante n'est affichée et l'icône radio générique du
  player reste visible.

## 6. Résumé pour diagnostic

Lorsqu'une mauvaise image est affichée, vérifier dans cet ordre :

1. les valeurs exactes de `currentArtist` et `currentTitle` ;
2. la normalisation et la correspondance du résultat Apple/MusicBrainz ;
3. le `stationuuid`, le `favicon` et la `homepage` retournés par Radio Browser ;
4. les dimensions et le type MIME de l'image ;
5. l'entrée `station_logo_cache` et sa source ;
6. la préférence `confirmed_station_logo_*` ;
7. la présence de `serper.api.key` et, pour le secours Logo.dev, de
   `logo.dev.publishable.key` dans `local.properties`.

Pour forcer une nouvelle recherche depuis l'interface, annuler d'abord une éventuelle confirmation,
puis parcourir les dix URL affichées avec le bouton **X**. Aucune URL rejetée n'est exclue d'une
future recherche.

## 7. Recherche et affichage des paroles

Les paroles sont recherchées uniquement lorsque l'artiste et le titre sont tous les deux
disponibles. Le bouton **PAROLES** reste masqué lorsqu'une de ces deux métadonnées manque.

### Cascade LRCLIB

Lorsqu'on ouvre les paroles, l'application suit cet ordre :

```text
Artiste + titre disponibles ?
    |
    +-- non --> aucune recherche
    |
    +-- oui --> résultat déjà dans le cache de session ?
                    |
                    +-- oui --> affichage immédiat
                    |
                    +-- non --> LRCLIB /api/get
                                    |
                                    +-- paroles trouvées --> affichage et cache
                                    |
                                    +-- erreur ou paroles vides --> LRCLIB /api/search
                                                                        |
                                                                        +-- résultat avec paroles
                                                                        |      --> affichage et cache
                                                                        |
                                                                        +-- aucun résultat
                                                                               --> paroles non disponibles
```

La requête exacte utilise :

```text
https://lrclib.net/api/get?artist_name=<artiste>&track_name=<titre>
```

Le fallback utilise :

```text
https://lrclib.net/api/search?artist_name=<artiste>&track_name=<titre>
```

Dans les résultats de `/search`, la première réponse contenant des `plainLyrics` non vides est
retenue.

### User-Agent obligatoire

LRCLIB peut répondre avec une erreur HTTP `520` au User-Agent par défaut d'OkHttp, même lorsque les
paroles existent. Ce comportement a été reproduit avec :

```text
okhttp/4.12.0 --> HTTP 520
```

Le client de SimpleRADIO remplace donc explicitement cet en-tête :

```text
User-Agent: SimpleRADIO/1.0 (Android; lyrics client)
Accept: application/json
```

Avec ce User-Agent, la même recherche retourne HTTP `200`. Il ne faut pas supprimer cet
intercepteur lors d'une future refactorisation du client Retrofit.

### Délais et messages d'erreur

Le client utilise les limites suivantes :

- connexion : 5 secondes ;
- lecture : 8 secondes ;
- appel complet : 10 secondes.

Les situations sont différenciées dans l'interface :

- réponse valide sans texte exploitable : `Paroles non disponibles.` ;
- échec réseau, HTTP ou décodage après les deux tentatives :
  `Service de paroles temporairement indisponible.`

Les erreurs réelles sont écrites dans Logcat avec le tag :

```text
LyricsLookup
```

La première erreur `/get` est enregistrée au niveau `WARN`, puis l'application essaie `/search`.
Si les deux appels échouent, l'erreur finale est enregistrée au niveau `ERROR`.

### Cache et affichage

Le cache des paroles est conservé en mémoire avec la clé :

```text
<artiste> - <titre>
```

Une réponse déjà obtenue est donc réaffichée sans nouvel accès réseau pendant la même session du
player. Le texte utilisé est `plainLyrics`. La réponse synchronisée `syncedLyrics` est reçue par le
modèle, mais elle n'est pas encore utilisée pour synchroniser les lignes avec la lecture audio.

Implémentation :

- client Retrofit et endpoints LRCLIB :
  [`LyricsApi.kt`](app/src/main/java/com/example/simpleradio/data/api/LyricsApi.kt) ;
- cascade, cache et messages :
  [`PlayerScreen.kt`](app/src/main/java/com/example/simpleradio/ui/screens/PlayerScreen.kt).

### Diagnostic avec ADB

Pour vérifier les métadonnées réellement connues de la session média :

```bash
adb shell dumpsys media_session | grep -i -C 5 simpleradio
```

Pour observer les erreurs LRCLIB après avoir appuyé sur le bouton **PAROLES** :

```bash
adb logcat -s LyricsLookup:V
```

Si l'API répond dans un navigateur mais pas dans l'application, vérifier en priorité :

1. l'artiste et le titre exacts visibles dans ADB ;
2. le statut HTTP inscrit sous `LyricsLookup` ;
3. la présence du User-Agent SimpleRADIO dans l'intercepteur OkHttp ;
4. la disponibilité de `/api/get` puis de `/api/search` ;
5. l'existence de `plainLyrics` dans la réponse.
