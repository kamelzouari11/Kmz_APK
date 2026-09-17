# Mes Placements

Application de bureau légère pour suivre les revenus de placements financiers et préparer les informations nécessaires aux déclarations fiscales.

## Choix technique

Le projet utilise **Tauri 2 + Vite + JavaScript**. Tauri permet d’obtenir une application Linux beaucoup moins lourde qu’une application Electron, tout en gardant une interface web moderne. Tauri 2 possède aussi une cible Android pour une évolution ultérieure sans réécrire l’écran de saisie.

## Fonctionnalités actuelles

- Saisie de l’exercice, établissement, placement, type de revenu, montant, RS et imposition.
- Montants normalisés à trois décimales en TND.
- Conservation locale des entrées sur l’appareil.
- Suggestions automatiques basées sur les établissements, placements et revenus déjà utilisés.
- Historique des dernières entrées et suppression individuelle.
- Import et export CSV avec séparateur `;`.

## Développement

Prérequis : Node.js, Rust et les dépendances système Tauri pour Linux Mint.

```bash
npm install
npm run tauri dev
```

Pour vérifier uniquement l’interface web :

```bash
npm run dev
```

Pour produire l’application Linux :

```bash
npm run tauri build
```

Les données de la première version sont conservées dans le stockage local du navigateur intégré. L’import/export CSV assure une sauvegarde simple ; un stockage CSV natif Tauri pourra être ajouté avant la version de production et les rapports fiscaux.

## Améliorations de l’écran de saisie

- Modification d’une entrée et confirmation avant suppression.
- Historique complet avec recherche et filtre par exercice.
- Exercice conservé entre deux saisies ; RS à zéro par défaut.
- Montants acceptés avec virgule ou point, jusqu’à trois décimales, sans arrondi silencieux.
- Import CSV validé intégralement avant ajout : accents, guillemets, points-virgules et retours à la ligne dans les libellés sont conservés. L’en-tête et l’ordre des huit colonnes doivent correspondre à un export de l’application.
- Les doublons exacts sur les huit champs sont ignorés lors de l’import. Deux revenus réellement identiques peuvent être ajoutés manuellement.
- Erreurs de sauvegarde signalées ; les données locales illisibles ne sont pas écrasées.
- Interface utilisable hors connexion, sans chargement de polices externes.
- Icône verte avec flèche ascendante utilisée dans l’interface et déclinée en PNG pour Linux.

Le serveur de développement utilise le port fixe **1420**, conformément à la configuration Tauri.

```bash
npm test
npm run build
```

Ces vérifications concernent les fonctions de données et l’interface web. Elles ne compilent aucune application Android.

## Stockage et suite du projet

Le CSV est actuellement un format d’import/export, et non le fichier de travail automatiquement mis à jour. Exportez régulièrement vos données et conservez une copie du CSV. Les données du navigateur de développement et celles de l’application Tauri sont distinctes : utilisez un export/import pour les transférer.

Avant un usage quotidien, la priorité est d’ajouter une sauvegarde automatique sur disque avec restauration. Les rapports fiscaux et la version Android restent à définir ultérieurement. Le champ Imposition est renseigné par l’utilisateur ; aucune règle fiscale n’est calculée automatiquement.

## Protection de la saisie

La touche Entrée passe au champ suivant ; après l’imposition, elle place le focus sur Enregistrer sans l’activer. L’enregistrement se fait par activation explicite du bouton Enregistrer. Annuler reste disponible en création et en modification.

Dès qu’un champ est changé ou qu’une entrée est ouverte en modification, les actions de l’historique et d’import/export sont verrouillées jusqu’à Enregistrer ou Annuler. Une erreur de validation ou de sauvegarde conserve ce verrouillage et la saisie.

Dans le navigateur, une fermeture ou un rechargement déclenche l’avertissement standard de saisie en cours (le navigateur garde le contrôle de la fermeture). Dans Tauri, la fermeture de la fenêtre est interceptée jusqu’à Enregistrer ou Annuler. Cette dernière protection nécessite une vérification dans l’application native.


## Déclaration

Chaque revenu possède un statut « Non encore déclaré » (par défaut) ou « Déjà déclaré ». Ce statut se renseigne dans la fiche ou directement avec le sélecteur de chaque ligne de l’historique ; dans ce dernier cas, la modification est sauvegardée immédiatement. Le listing reste verrouillé lorsqu’une fiche est en cours de saisie.

Le statut est inclus dans les exports CSV, en huitième colonne « Déclaration ». Les anciennes données locales et les CSV à sept colonnes restent acceptés : leur statut initial est « Non encore déclaré ».

## Tri du listing

Le panneau « Trier les entrées » permet de combiner uniquement Exercice, Établissement, Placement et Revenu. Les critères s’appliquent dans l’ordre affiché (« D’abord », puis « Puis »), chacun avec son sens de tri. Ajoutez ou retirez des critères selon vos besoins. Par défaut : exercice le plus récent, puis établissement de A à Z. Sans critère, le listing reprend l’ordre de saisie. Le tri s’applique aux résultats filtrés et ne change pas les données ni l’ordre de l’export CSV.

## Rapports à l’écran et PDF

L’onglet **Rapports** ouvre directement le **Détail des revenus**, pièce par pièce. Le **Suivi des déclarations** présente également toutes les pièces, avec le statut indiqué sur chaque ligne. Les groupes sont de simples repères : aucun tableau récapitulatif, nombre d’entrées ou sous-total n’est affiché pour chaque groupe. Seul le total général des montants et de la RS figure à la fin du rapport.

Le dernier exercice disponible est sélectionné au premier affichage. Cochez plusieurs exercices pour les réunir, ou décochez-les tous pour afficher toutes les années. La recherche par mots et les filtres établissement, placement, revenu, imposition et déclaration se combinent. Les groupes peuvent être organisés par établissement, placement ou revenu, en ordre alphabétique croissant ou décroissant. Les exercices restent du plus récent au plus ancien, sans séparation selon l’imposition ou la déclaration.

Les sommes sont calculées en millimes entiers, avec prise en compte des pertes et total de RS distinct. Aucun calcul d’impôt ni règle de compensation fiscale n’est appliqué.

**Enregistrer en PDF** génère localement un fichier A4 sobre : filtres, date, devise, chaque pièce, total général, en-têtes répétés et pagination. **Imprimer** utilise une présentation dédiée sans commandes interactives. Le PDF reprend les filtres actifs au moment du clic. Les données ne sont pas envoyées à un service externe.

Les indications « Déjà déclaré » (vert pastel) et « Exonéré » (bleu-gris) figurent uniquement sur les pièces, jamais dans les titres de groupes. Le papier et le PDF utilisent un contour gris pour la déclaration et un fond gris léger pour l’exonération.

L’accès aux rapports reste verrouillé pendant la saisie, jusqu’à Enregistrer ou Annuler. Les tests couvrent les totaux, les filtres, la conservation de chaque pièce et le PDF multipage. Le parcours est également vérifié dans Chromium. L’impression et le téléchargement dans la fenêtre native Tauri restent à vérifier sur Linux Mint.

Le total général est présenté dans un bandeau de clôture : titre à gauche, montant total et retenue à la source alignés à droite, avec trois décimales et la devise TND. Le PDF reprend ce bandeau en gris sobre, sans coupure entre deux pages.
