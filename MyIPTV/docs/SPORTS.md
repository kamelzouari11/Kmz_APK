# Catalogue sports

L’entrée **Sports** réutilise le fonctionnement du catalogue Cinéma avec des
réglages indépendants. Elle lit les chaînes sportives des flux XMLTV gratuits
par pays déjà configurés : France, Italie, Espagne, Royaume-Uni, Allemagne,
Belgique, Suisse, Portugal, Pays-Bas, Pologne, États-Unis et Canada. Une chaîne
Strong absente du XMLTV ne produit aucun événement artificiel.

Les périodes Past, Now, Next et Later couvrent toute l’étendue réellement
fournie. Now place les événements commencés le plus récemment en tête et affiche
leur progression. Next conserve le premier événement futur de chaque chaîne ;
Later contient les suivants. Les pays actifs et la période sont mémorisés.
Le champ de recherche filtre toutes les périodes des pays actifs et examine le
titre, la description, les catégories XMLTV et le nom de la chaîne. Il attend
450 ms après la dernière frappe et calcule les résultats en arrière-plan.

Sur Android TV, la liste horodatée occupe la moitié gauche et l’illustration la
moitié droite. OK ouvre la fiche détaillée ; Retour revient à la liste. Sur
téléphone, les événements utilisent la mosaïque à une colonne avec l’espace de
sécurité inférieur du module Cinéma.
Dans la liste TV, Gauche rejoint le premier événement puis le bandeau supérieur ;
Droite rejoint le dernier événement puis le bandeau supérieur.

Les illustrations suivent cet ordre : image XMLTV, recherche TVMaze/IMDb ou
logos d’équipes via TheSportsDB, puis logo de la chaîne Strong. La fiche conserve
la traduction et les actions d’association. Les associations sont durables et
préservent toutes les variantes SD, HD, FHD, UHD et 4K.

Chaque pays possède un cache distinct réutilisé pendant douze heures. Quatre
téléchargements au maximum sont exécutés en parallèle. Actualiser force seulement
les pays actifs ; le dernier cache reste disponible si la source échoue.

## Validation

Aucune compilation Android ni tâche Gradle n’a été exécutée. L’intégration doit
être compilée et vérifiée sur S22 et Android TV dans Android Studio.
