# Football Schedule App ⚽

Application Android (Jetpack Compose / Material 3) qui affiche le programme des matchs, les scores en direct et les badges des équipes pour les principales compétitions mondiales.

## Fonctionnalités

- Liste des matchs groupés par compétition, pour n'importe quelle date
- Navigation par jour (précédent / suivant) + sélecteur de date
- Statuts visuels colorés : **LIVE** (pulse rouge), À venir, Mi-temps, Terminé, Reporté
- Minute en direct affichée pour les matchs en cours
- Heure locale automatique (au lieu de l'UTC brut)
- Logos d'équipes via API-FOOTBALL / football-data.org quand disponibles
- Calendrier Coupe du Monde 2026 futur via OpenFootball / worldcup-live JSON gratuit
- Chargement à la demande d'une seule journée, puis cache disque `YYYY-MM-DD.json`
- Recherche d'une équipe domicile ou extérieur dans toutes les journées en cache
- Chaînes TV issues du microservice de scraping, avec cache serveur quotidien
- Cloche par match pour activer ou annuler un rappel 15 minutes avant le coup d'envoi
- Initiales en fallback dans une pastille dégradée (plus de carré gris)
- Material You : couleurs dynamiques sur Android 12+, dark theme automatique
- Mode mock complet si aucune clé API configurée

## Sources de données (par ordre de priorité)

| Priorité | API | Pourquoi |
|---|---|---|
| 1 | [API-FOOTBALL](https://www.api-football.com/) | Source principale, interrogée par date uniquement dans la fenêtre `today ± 1 jour`. |
| 2 | Serveur `/schedule` (LiveSoccerTV daté) | Complète ensuite la même journée, notamment pour les matchs amicaux. |
| Logos | API-FOOTBALL / football-data.org | Badges officiels quand la source les expose. |

L'application lit uniquement le cache de la date sélectionnée. S'il existe, elle
l'affiche immédiatement. La synchronisation interroge d'abord l'endpoint journalier
d'API-FOOTBALL, puis LiveSoccerTV. Aucune autre source de calendrier n'est fusionnée.

Le serveur TV utilise également un fichier par date sous `tv_cache/`. La variable
`TV_CACHE_DIR` permet de placer ce dossier sur un volume persistant en production.
Les programmes futurs non vides sont conservés six heures ; les réponses vides
n'y sont mises en cache que deux minutes. Le collecteur LiveSoccerTV demande à Jina
uniquement `table.schedules` pour la date ciblée et accepte son cache futur six heures,
au lieu de convertir la page complète à chaque actualisation.

Dans le détail d'un match, la couverture TV est limitée aux diffuseurs d'Europe,
du MENA et d'Amérique du Nord. Les variantes pan-régionales de beIN SPORTS sont
regroupées sous une seule section MENA afin d'éviter leur répétition pays par pays.

Autres pistes selon les besoins :
- **Sportmonks Football API** — stats avancées (xG, heatmaps) ; payant
- **LiveScore-API** — focus live
- **OpenFootball** (github.com/openfootball) — JSON statique, 100 % libre, mode offline
- **Football-Data-API.com** — stats avancées tier gratuit

## Configuration des clés API

Dans `gradle.properties` (ou via `-PCLE=...`) :

```properties
# API-FOOTBALL (recommandée)
API_FOOTBALL_KEY=VOTRE_CLE
API_FOOTBALL_USE_RAPID=false     # true si la clé est issue de RapidAPI

# football-data.org (backup)
FOOTBALL_DATA_API_KEY=VOTRE_CLE
```

Sans aucune clé, l'app utilise les données mock pour le prototype.

## Lancer le projet

```bash
./gradlew assembleDebug
```

Min SDK 24, compile SDK 34.

## Architecture

```
ui/
  MatchListScreen.kt   ← Composables (TopBar, DateNavigator, Cards, LiveIndicator)
  MatchViewModel.kt    ← StateFlow + viewModelScope
  theme/               ← Material 3 + dynamic color
data/
  ApiFootballApi.kt    ← source principale
  FootballDataApi.kt   ← backup
  OpenFootballApi.kt   ← calendriers JSON gratuits + Coupe du Monde
  model/MatchModels.kt ← Match / Team / Score / MatchStatus
repository/
  MatchRepository.kt   ← orchestration, cache logos, fallback
```
