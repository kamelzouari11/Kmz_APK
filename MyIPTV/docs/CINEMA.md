# Catalogue cinéma

Entrée **Cinéma** dans les menus portrait et TV. Le catalogue présente une
mosaïque de pochettes avec le titre sous chaque image sur téléphone. En paysage
Android TV, la moitié gauche présente la liste chronologique et la moitié droite
la pochette entière du film qui a le focus. La sélection d'une carte ou OK ouvre
la fiche détaillée avec la chaîne, les horaires, le résumé, la traduction et les
actions de lecture et d'association. Retour système, ou le bouton Retour, revient
à la mosaïque à sa position précédente.

Dans la liste TV, un film en cours affiche une barre de progression : une barre
encore courte permet de repérer immédiatement une diffusion proche de son début.
Depuis la liste, Gauche rejoint son premier élément puis le bandeau supérieur ;
Droite rejoint son dernier élément puis le bandeau supérieur.

## Périmètre

France (XMLTV France, EPGShare01 en secours), Italie, Espagne, Royaume-Uni,
Allemagne, Belgique, Suisse, Portugal, Pays-Bas, Pologne, USA et Canada
(EPGShare01). Tous les pays sont actifs au premier lancement. « All » vert
signifie que tous sont actifs ; il permet de tout désactiver ou réactiver. Chaque
pays peut ensuite être activé indépendamment, en vert, ou désactivé, en blanc.
La dernière période et la dernière combinaison de pays, y compris une sélection
vide, sont restaurées après fermeture ou redémarrage de l’application.
Le champ de recherche ignore temporairement la période et filtre toute l’étendue
chargée des pays actifs. Il examine titre, résumé, genres XMLTV et chaîne. Le
filtrage démarre 450 ms après la dernière frappe et s’exécute hors de l’interface.
La couverture dépend des programmes effectivement présents chez ces sources.

Les vues Past, Now, Next et Later utilisent Africa/Tunis, comme le guide EPG
existant. Past contient les films terminés, du plus récent au plus ancien. Now
contient les films en cours, avec ceux qui viennent de commencer en premier. Next contient le premier film à venir de chaque
chaîne. Later contient toutes les diffusions futures restantes. Les données sont
classées chronologiquement et couvrent tout l’intervalle réellement fourni par
chaque source ; l’interface affiche ses bornes. Ouvrir une chaîne lance
uniquement son direct, sans replay ni programmation future de la lecture.

Les familles CINE+, CanalPlay, À LA CARTE, beIN Cinema, LuxPlay, Premium Play,
Prime et Netflix, avec leurs variantes numérotées, sont reconnues comme chaînes
cinéma. Leur apparition reste conditionnée à la présence réelle de leurs horaires
dans un flux XMLTV ; un service de vidéo à la demande sans grille linéaire ne peut
pas produire de programme horodaté.

Le catalogue ne dépend pas de l’existence d’un epg_channel_id chez Strong.
Le filtrage utilise les noms des chaînes cinéma et retire les programmes
explicitement classés séries, sport, informations ou magazines. Les sources sans
catégories peuvent encore laisser passer des émissions non cinématographiques.

## Correspondances et variantes

Le profil porte le nom STRONG IPTV (insensible à la casse). Les propositions
exactes comparent pays et nom normalisé, ou l’identifiant EPG. Les indications de
qualité sont ignorées uniquement pour cette comparaison. +1, +24, East/West et
numéros restent distincts. Les associations approximatives ne sont pas validées
automatiquement : la recherche manuelle permet de sélectionner plusieurs flux.

Les associations mémorisent des streamId par profil et chaîne externe. SD, HD,
FHD, UHD, 4K et même les flux homonymes restent distincts. Le choix de variante
place la dernière utilisée en tête ; il ne supprime aucune autre variante.
Après lecture, Retour retrouve le catalogue. Après une resynchronisation Strong
qui modifie ses identifiants, les associations peuvent devoir être corrigées.

## Stockage et images

CinemaRepository lit les XMLTV gzip progressivement puis stocke les programmes
cinéma de toute la période fournie dans filesDir/cinema. Écriture atomique, cache
par pays réutilisé pendant douze heures, y compris après minuit. Les pays sans
cache récent sont téléchargés en parallèle, avec quatre connexions simultanées.
Le bouton Actualiser ignore ce délai et force tous les téléchargements. Un échec
conserve le dernier cache disponible et affiche un message. Les comptes IPTV ne sont jamais
transmis aux sources EPG ou d’images.

L’image EPG est prioritaire. En cas d’absence ou d’échec, une recherche IMDb
ciblée sur les films utilise uniquement le titre, sans pays ni année. Une correspondance incertaine
ou une image impossible à charger utilise le logo de la chaîne Strong associée
comme dernier secours. Le premier résultat cinéma IMDb reste accepté lorsque
IMDb traduit le titre international dans une autre langue. Les autres cartes EPG
conservent leur recherche habituelle. Coil gère le cache des images.

## Validation

Aucune compilation Android ni tâche Gradle exécutée. Tests unitaires ajoutés
pour les quatre périodes, l’horizon complet, les fuseaux et la conservation des variantes ;
ils restent à exécuter dans Android Studio. L’intégration UI et la lecture sur
appareil restent à vérifier dans Android Studio, notamment Retour après lecture,
les différentes variantes et les correspondances manuelles.
