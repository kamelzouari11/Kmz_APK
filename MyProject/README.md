# 📱 MyProject - Application Android

Une application Android moderne pour gérer les budgets de rénovation de maison, avec une organisation en Lots et Sous-Lots.

## 🎯 Fonctionnalités

### Phase 1 : Budgétisation des Travaux
- ✅ Création, modification, suppression de **Lots** de travaux
- ✅ Création, modification, suppression de **Sous-Lots** par Lot
- ✅ Ajout d'**Articles** avec:
  - Quantité (nombre entier)
  - Unité (m², m, kg, l, etc.)
  - Prix unitaire (format français avec séparateur " ")
  - Calcul automatique du total (quantité × prix)
- ✅ Validation des Lots et Sous-Lots
- ✅ Totaux automatiques par niveau
- ✅ Format français des montants (###\u00A0##0 €)

### Phase 2 : Suivi des Dépenses (À venir)
- Enregistrement des dépenses réelles
- Comparaison budget vs réalité
- Historique des paiements

## 🏗️ Architecture

L'application utilise une architecture moderne **MVVM** avec:

### Couches
```
┌─────────────────────────────────────────┐
│  UI Layer (Jetpack Compose)              │
│  - Screens (Lots, SousLots, Articles)    │
│  - Components (Cards, Forms)             │
│  - Theme & Styling                       │
└─────────────┬───────────────────────────┘
              │
┌─────────────▼───────────────────────────┐
│  ViewModel Layer                         │
│  - LotViewModel                          │
│  - SousLotViewModel                      │
│  - ArticleViewModel                      │
└─────────────┬───────────────────────────┘
              │
┌─────────────▼───────────────────────────┐
│  Repository Layer                        │
│  - RenovationRepository                  │
└─────────────┬───────────────────────────┘
              │
┌─────────────▼───────────────────────────┐
│  Data Layer (Room Database)              │
│  - LotDao, SousLotDao, ArticleDao        │
│  - RenovationDatabase                    │
│  - Entities: Lot, SousLot, Article       │
└──────────────────────────────────────────┘
```

## 📦 Structure du Projet

```
app/
├── src/main/
│   ├── kotlin/fr/kmz/renovation/
│   │   ├── data/
│   │   │   ├── model/          # Entités Room
│   │   │   ├── db/             # DAOs et Database
│   │   │   └── repository/      # Repository pattern
│   │   ├── ui/
│   │   │   ├── screens/         # Écrans Compose
│   │   │   ├── components/      # Composants réutilisables
│   │   │   ├── viewmodel/       # ViewModels
│   │   │   └── theme/           # Thème Material3
│   │   └── utils/               # Utilitaires (formatage, etc.)
│   ├── java/fr/kmz/renovation/
│   │   └── MainActivity.kt      # Point d'entrée
│   └── res/                     # Ressources (strings, colors, etc.)
├── build.gradle.kts            # Configuration Gradle
└── proguard-rules.pro          # Règles ProGuard
```

## 🔧 Configuration

### Prérequis
- Android Studio Hedgehog ou plus récent
- Android SDK 24+ (API level 24)
- Gradle 8.2.0 ou plus récent
- Kotlin 1.9.10

### Dépendances principales
- **Jetpack Compose** 2023.10.01 - UI moderne
- **Room** 2.6.1 - Base de données locale
- **lifecycle-runtime-ktx** - Gestion du cycle de vie
- **DataStore** - Préférences persistantes
- **Coroutines** - Programmation asynchrone

## 🚀 Installation et Exécution

### 1. Cloner le projet
```bash
cd /media/kamel/DATA/KmzAPK/MyProject
```

### 2. Compiler
```bash
./gradlew build
```

### 3. Exécuter sur l'émulateur ou device
```bash
./gradlew installDebug
adb shell am start -n fr.kmz.renovation/.MainActivity
```

ou depuis Android Studio:
- File → Open → Sélectionner le projet
- Run → Run 'app'

## 💡 Guide d'Utilisation

### Créer un Lot
1. Cliquer sur le bouton **+** en bas à droite
2. Entrer le nom du Lot (ex: "Salle de bain")
3. Optionnel: Ajouter une description
4. Cliquer "Enregistrer"

### Ajouter des Sous-Lots
1. Cliquer sur "Détails" d'un Lot
2. Cliquer sur le bouton **+**
3. Entrer le nom du Sous-Lot (ex: "Carrelage")
4. Cliquer "Enregistrer"

### Ajouter des Articles
1. Cliquer sur "Articles" d'un Sous-Lot
2. Cliquer sur le bouton **+**
3. Remplir les champs:
   - **Nom**: Description de l'article
   - **Quantité**: Nombre entier
   - **Unité**: m², m, kg, etc.
   - **Prix unitaire**: En euros
4. Le **Total** se calcule automatiquement
5. Cliquer "Enregistrer"

### Modifier/Supprimer
- **Modifier**: Cliquer l'icône ✏️
- **Supprimer**: Cliquer l'icône 🗑️
- **Valider**: Cliquer "Valider" pour marquer comme complété

## 📊 Format des Montants

Les montants sont affichés au format français:
- Séparateur de milliers: **espace** (ex: 1 500 €)
- Pas de décimales (montants en entiers)
- Exemple: 15000 € s'affiche "15 000 €"

## 🎨 Thème

L'application utilise **Material Design 3** avec:
- Thème clair et sombre automatique
- Couleur primaire: #6C63FF (violet)
- Couleur secondaire: #B19CD9 (mauve)
- Navigation intuitive

## 📈 Améliorations Futures

- [ ] Suivi des dépenses réelles (Phase 2)
- [ ] Export en PDF/Excel
- [ ] Synchronisation Cloud
- [ ] Partage de projets
- [ ] Photos des travaux
- [ ] Notifications de rappel
- [ ] Multi-devise
- [ ] Graphiques d'analyse budgétaire

## 🐛 Signaler un Bug

Créer une issue avec:
1. Description détaillée du problème
2. Étapes pour reproduire
3. Device / OS / Version de l'app utilisés
4. Logs (logcat)

## 📝 Licence

Ce projet est privé. Tous droits réservés.

## 👨‍💼 Support

Pour questions ou support: contact@kmz.fr

---

**Version**: 1.0.0  
**Date**: Février 2026  
**Développeur**: Kamel Zouari (KMZ)
