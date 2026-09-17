# Mes Placements - Android Reporting

Cette branche Android est limitée à la consultation des données et à la
création du même PDF que l'application principale. Elle ne contient aucune
saisie, aucun jeton GitHub et aucune écriture dans le dépôt.

## Utilisation dans Android Studio

1. Ouvrir le dossier `android-reporting` comme projet Gradle.
2. Dans la boîte de dialogue Gradle JDK, sélectionner **JVM 21**. Ne pas
	sélectionner JVM 25 : Gradle 8.11.1 ne prend pas en charge Java 25.
3. Utiliser le SDK Android avec `compileSdk 35`.
4. Sélectionner le module `app`, puis un Samsung S22 ou une tablette Android.
5. Lancer l'application depuis Android Studio.

Le projet fixe également Gradle sur un JDK 21 dans `gradle.properties`. Sur
cette machine, le JDK système est `/usr/lib/jvm/java-21-openjdk-amd64` et le
JDK JetBrains 21 fourni par Android Studio est généralement disponible dans
`~/.gradle/jdks/jetbrains_s_r_o_-21-amd64-linux.2`. Si Android Studio continue
à lancer Java 25, ouvrir `Settings > Build, Execution, Deployment > Build Tools
> Gradle`, choisir le JDK JetBrains 21 dans **Gradle JDK**, puis recharger le
projet. Le message `Unsupported class file major version 69` indique Java 25.

Le téléphone est forcé en portrait. Les appareils dont la largeur minimale est
d'au moins 600 dp sont forcés en paysage, ce qui correspond aux tablettes
13 pouces. Le PDF reste en A4 portrait, comme dans l'application principale,
puis est enregistré avec le sélecteur de fichiers Android.

## Parcours utilisateur

1. Saisir le même code de sauvegarde que sur le PC.
2. Charger la sauvegarde chiffrée depuis GitHub, ou ouvrir la copie locale.
3. Sélectionner les exercices et filtres du rapport.
4. Appuyer sur `Enregistrer en PDF`, puis choisir la destination Android.

La copie locale est chiffrée et conservée uniquement dans le stockage privé de
l'application. La source GitHub est lue en mode public et lecture seule.

## Mise à jour des assets

Après une modification du web ou du PDF, régénérer les assets avec la commande
suivante depuis la racine du projet principal :

```text
npm run prepare:android-reports
```

La tâche Gradle `prepareReportsWeb` sait aussi l'exécuter avant `preBuild`.