# MesComptes

Application Android personnelle, locale et en mode portrait pour gérer des coordonnées bancaires et des positions en titres.

## Fonctions

- Comptes : ajout, modification, suppression et présentation plein écran.
- Journal : opérations triées de la plus récente à la plus ancienne, filtre par compte et recherche par titre.
- Ordres : Achat, Souscription, Vente et Rachat, avec contrôle du solde disponible.
- Portefeuille : quantités restantes regroupées par compte et par titre.
- Tableau de bord : nombre de comptes, dernière opération et nombre de positions.
- Base Room entièrement locale, sans permission Internet.
- Ouverture directe, comme un carnet personnel, sans verrouillage supplémentaire.
- Sauvegarde/restauration manuelle dans un fichier `.mcp` chiffré par mot de passe.

## Précautions

Le mot de passe d'une sauvegarde n'est conservé ni dans l'application ni dans le fichier. Sans celui-ci, la restauration est impossible. Une restauration valide remplace la base présente sur l'appareil.

Le projet cible Java 17 et se lance directement depuis Android Studio sur le Galaxy S22 en mode portrait.
