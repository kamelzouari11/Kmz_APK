# Football Schedule App ⚽

Application Android (Jetpack Compose / Material 3) qui affiche le programme des matchs, les scores en direct et les badges des équipes pour les principales compétitions mondiales.

## Fonctionnalités

- Liste des matchs groupés par compétition, pour n'importe quelle date
- Navigation par jour (précédent / suivant) + sélecteur de date
- Statuts visuels colorés : **LIVE** (pulse rouge), À venir, Mi-temps, Terminé, Reporté
- Minute en direct affichée pour les matchs en cours
- Heure locale automatique (au lieu de l'UTC brut)
- Logos d'équipes via TheSportsDB, fetch parallélisé + cache mémoire
- Initiales en fallback dans une pastille dégradée (plus de carré gris)
- Material You : couleurs dynamiques sur Android 12+, dark theme automatique
- Mode mock complet si aucune clé API configurée

## Sources de données (par ordre de priorité)

| Priorité | API | Pourquoi |
|---|---|---|
| 1 | [API-FOOTBALL](https://www.api-football.com/) | 1100+ ligues, live, lineups, stats, prédictions. Plan gratuit 100 req/jour. |
| 2 | [football-data.org v4](https://www.football-data.org/) | Backup simple. Limité aux compétitions majeures, 10 req/min. |
| Logos | [TheSportsDB](https://www.thesportsdb.com/) | Badges HD libres avec clé `3`. |

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
  TheSportsDbApi.kt    ← logos
  model/MatchModels.kt ← Match / Team / Score / MatchStatus
repository/
  MatchRepository.kt   ← orchestration, cache logos, fallback
```
