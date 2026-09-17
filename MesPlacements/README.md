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

Les données de travail sont conservées automatiquement dans un fichier JSON local. Les imports et exports CSV restent disponibles pour les échanges et les archives.

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

Le CSV reste un format d’import/export. Le fichier de travail est `data/mesplacements.json`, automatiquement mis à jour après chaque opération validée. Les navigateurs et l’application Tauri lancés sur ce projet utilisent ce même fichier local.

Chaque remplacement du fichier conserve sa version précédente dans `data/backups/`. Les rapports fiscaux sont également disponibles dans la branche Android de reporting uniquement, située dans `android-reporting/`. Elle lit la sauvegarde GitHub chiffrée en lecture seule et réutilise le même moteur de rapports et de PDF que l’application principale. Le champ Imposition est renseigné par l’utilisateur ; aucune règle fiscale n’est calculée automatiquement.

### Version Android de reporting

Ouvrir `android-reporting/` dans Android Studio. Le Samsung S22 est utilisé en portrait ; les appareils dont la largeur minimale est d’au moins 600 dp, notamment les tablettes 13 pouces, sont utilisés en paysage. La version Android ne permet pas la saisie ni l’écriture GitHub : elle charge la dernière sauvegarde, permet les mêmes filtres et crée le même PDF A4 via le sélecteur de fichiers Android. Voir `android-reporting/README.md` pour le parcours détaillé.

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

**Enregistrer en PDF** génère localement un fichier A4 sobre : filtres, date, devise, chaque pièce, total général, en-têtes répétés et pagination. **Imprimer** utilise une présentation dédiée sans commandes interactives. Le PDF reprend les filtres actifs au moment du clic. Les rapports et PDF restent locaux. L’envoi de données à GitHub se fait uniquement avec la commande de sauvegarde décrite ci-dessous.

Les indications « Déjà déclaré » (vert pastel) et « Exonéré » (bleu-gris) figurent uniquement sur les pièces, jamais dans les titres de groupes. Le papier et le PDF utilisent un contour gris pour la déclaration et un fond gris léger pour l’exonération.

L’accès aux rapports reste verrouillé pendant la saisie, jusqu’à Enregistrer ou Annuler. Les tests couvrent les totaux, les filtres, la conservation de chaque pièce et le PDF multipage. Le parcours est également vérifié dans Chromium. L’impression et le téléchargement dans la fenêtre native Tauri restent à vérifier sur Linux Mint.

Le total général est présenté dans un bandeau de clôture : titre à gauche, montant total et retenue à la source alignés à droite, avec trois décimales et la devise TND. Le PDF reprend ce bandeau en gris sobre, sans coupure entre deux pages.


## Sauvegarde GitHub chiffrée

Le bouton **GitHub** permet de sauvegarder toutes les entrées ou de restaurer la dernière sauvegarde. Le dépôt proposé est `kamelzouari11/Kmz_APK`, comme TaskManager, avec un fichier distinct : `MySharedFolder/mes_placements_backup.json`.

Ce dépôt est public : seul un fichier chiffré est envoyé. Le chiffrement utilise Web Crypto (AES-256-GCM, sel aléatoire de 16 octets, IV aléatoire de 12 octets, PBKDF2-SHA256 avec 600 000 itérations). Le contenu, les statuts, les identifiants et les dates des entrées sont chiffrés ensemble. Le code ou mot de passe doit comporter au moins 6 caractères ; un code à 6 chiffres est accepté. Il n’est jamais envoyé à GitHub. Sa perte empêche le déchiffrement.

Utilisation :

1. Ouvrir **GitHub** et vérifier le dépôt.
2. Renseigner uniquement le mot de passe de sauvegarde. Le jeton GitHub est lu automatiquement depuis `local.properties` sur le PC ; il n’est pas envoyé au navigateur ni intégré aux fichiers web.
3. Cliquer sur **Sauvegarder sur GitHub**. L’application chiffre toutes les entrées, confirme la destination, envoie le fichier puis relit la révision envoyée pour la vérifier.
4. Pour récupérer les données, cliquer sur **Restaurer depuis GitHub** avec le même mot de passe. Le fichier est déchiffré et validé intégralement avant confirmation du remplacement des entrées locales.

Avant une restauration, l’état local précédent est conservé sous `mesplacements.before-github-restore`. Le bouton **Exporter la copie locale avant restauration (CSV)** permet de le récupérer. Cette copie est remplacée à la prochaine restauration ; elle reste dans le stockage du même navigateur et ne remplace pas un export sur disque. En cas de manque d’espace pour cette copie, la restauration est annulée.

Le nom du dépôt est mémorisé dans le navigateur. Le jeton reste dans `local.properties`, et le mot de passe de sauvegarde est effacé du champ à la fermeture du dialogue. La première sauvegarde effectuée le 17 septembre 2026 contient 45 entrées ; le mot de passe de récupération et une copie chiffrée ont été conservés sur ce PC dans `Documents/Mes Placements/Sauvegarde-2026-09-17/`. Garder également le mot de passe dans un gestionnaire de mots de passe ou sur un autre support privé.

Les sauvegardes sont manuelles. Chaque envoi crée une révision GitHub ; l’interface restaure la dernière version du fichier. Une écriture concurrente est refusée et doit être relancée. La sauvegarde chiffrée est limitée à 900 Ko pour rester compatible avec l’API Contents. Aucun fichier TaskManager n’est modifié. L’envoi en clair est refusé.

Validation : tests de données et chiffrement (mot de passe erroné, altération, accents, doublons légitimes), scénarios API simulés (conflit, erreur d’accès, vérification après envoi), compilation web Vite et parcours Chromium isolé. La première sauvegarde réelle a été relue depuis GitHub et déchiffrée, puis comparée aux 45 entrées locales. Le parcours dans la fenêtre native Tauri reste à vérifier ; aucune compilation Android n’est effectuée.

Références : [API Contents GitHub](https://docs.github.com/en/rest/repos/contents), [dérivation de clé Web Crypto](https://developer.mozilla.org/en-US/docs/Web/API/SubtleCrypto/deriveKey), [paramètres AES-GCM](https://developer.mozilla.org/en-US/docs/Web/API/AesGcmParams).


### Jeton GitHub permanent sur ce PC

Le jeton de TaskManager a été repris dans `MesPlacements/local.properties`, sous la clé `github.token`. Ce fichier est exclu de Git et accessible uniquement à son propriétaire (permissions 600). Le champ de saisie du jeton a été retiré.

Avec `npm run dev`, le serveur local lit le fichier à chaque requête et contacte GitHub. Seules les opérations de lecture du dépôt et de lecture/écriture du fichier de sauvegarde Mes Placements sont autorisées. Le navigateur ne reçoit pas le jeton. Les requêtes provenant d’autres origines et l’accès HTTP direct à `local.properties` sont refusés. Une mise à jour du jeton est prise en compte sans redémarrage.

Dans Tauri, la commande Rust `github_request` assure ce même rôle. Elle cherche le jeton dans le fichier du projet, puis dans `KmzAPK/local.properties`, puis dans `local.properties` du dossier de configuration Tauri (sur ce Linux : `~/.config/tn.kmzapk.mesplacements/`). Pour déplacer l’application sur un autre PC, placer sa configuration dans ce dernier dossier. Le jeton n’est pas intégré à la compilation. Le mot de passe de chiffrement reste distinct du jeton d’accès.

La connexion réelle depuis Chromium a été vérifiée sans saisie du jeton, en lisant et déchiffrant les 45 entrées distantes sans appliquer la restauration. Tests Node et compilation web réussis ; le code Rust a été inspecté et formaté, sans compilation native.

### Résultat des transferts GitHub

Un bandeau visible en haut de la fenêtre GitHub indique l’opération en cours, le succès en vert (date, nombre d’entrées et vérification après envoi), l’échec en rouge avec sa cause, ou l’annulation. Il reste visible pendant le défilement. Le dernier résultat du dépôt est conservé sur cet appareil et réaffiché à la réouverture, y compris après rechargement. Il décrit le dernier transfert effectué depuis ce navigateur, pas une synchronisation automatique des nouvelles saisies.

Une nouvelle sauvegarde utilise le code saisi sans demander le code de la précédente. La restauration nécessite toujours le code utilisé lors de l’envoi de la version à restaurer. Les anciennes versions restent chiffrées avec leur ancien mot de passe.


## Fichier local automatique

Sur ce PC, le fichier de travail est `/media/kamel/DATA/KmzAPK/MesPlacements/data/mesplacements.json`. Les 45 entrées présentes dans Chrome ont été copiées puis comparées à ce fichier. Une copie brute du stockage Chrome a été préservée avant migration.

L’application relit le fichier à chaque ouverture. Les créations, modifications, statuts de déclaration, suppressions, imports CSV et restaurations GitHub attendent la confirmation de l’écriture sur disque avant d’annoncer un succès. En cas d’échec, la saisie reste à l’écran. Le stockage du navigateur n’est plus la source principale ; il reste une copie complémentaire quand il est disponible.

Chaque écriture sauvegarde d’abord la version précédente dans `data/backups/`, écrit un fichier temporaire, le synchronise puis remplace le fichier de travail. Les copies précédentes sont conservées sans suppression automatique. Une révision et un verrou empêchent deux fenêtres d’écraser silencieusement leurs changements respectifs. En cas de conflit, copier sa saisie puis recharger la fenêtre pour relire la version récente. Après un arrêt brutal pendant une écriture, un fichier `data/.write-lock` peut subsister : ne le retirer qu’après avoir fermé les instances de l’application et vérifié qu’aucune écriture n’est en cours.

Le dossier `data/` est exclu de Git et inaccessible par les URL du serveur Vite. Ses fichiers sont lisibles sur le PC ; seule la sauvegarde GitHub est chiffrée. Pour une copie manuelle complète, copier le dossier `data/` après avoir terminé les saisies.

Lors d’un premier démarrage sans fichier, les anciennes entrées du stockage du navigateur utilisé sont reprises automatiquement. Quand un fichier existe, il fait autorité : les anciens stockages d’autres adresses ne sont pas fusionnés automatiquement. Une importation CSV permet de récupérer les entrées supplémentaires si nécessaire.

Tauri utilise le même dossier de projet sur ce PC. Sur une machine sans le projet source, il utilise le dossier de données de l’application (`~/.local/share/tn.kmzapk.mesplacements/data/` sous Linux). La commande native est implémentée et son code inspecté ; sa compilation n’a pas été effectuée, les dépendances WebKit GTK nécessaires ne sont pas installées ici. La persistance sur disque, les conflits, les erreurs d’écriture et la relecture dans un profil Chromium vierge sont vérifiés pour `npm run dev`.
