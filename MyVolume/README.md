# MyVolume

Mini-application Android destinée à remplacer des boutons physiques de volume défectueux.

## Fonctionnement

- Un bouton flottant reste visible au-dessus des applications grâce à l'autorisation Android
  « apparaître par-dessus d'autres applications ».
- Un toucher ouvre le panneau de volume multimédia natif de Samsung/Android.
- Un glissement déplace le bouton. Sa position est mémorisée.
- L'écran principal règle l'activation, la couleur, la taille, la transparence,
  l'aimantation au bord et le démarrage après allumage.
- Un service de premier plan et sa notification maintiennent le bouton actif.

## Premier lancement

1. Ouvrir MyVolume.
2. Accorder l'autorisation de superposition demandée.
3. Activer le bouton flottant.
4. Si Samsung interrompt le service, ouvrir les informations de l'application et régler
   **Batterie > Sans restriction**.

Le bouton reste au-dessus des applications ordinaires, mais Android conserve la priorité
pour les écrans système sensibles, l'écran de verrouillage et certaines fenêtres de sécurité.

## Environnement

- Gradle 9.7.1
- Android Gradle Plugin 9.3.2
- Kotlin 2.2.10
- minSdk 26 / targetSdk 36
