# MarOS

**Un accueil personnel pour Android TV et Google TV**, avec un accueil Kotlin et Compose for TV : applications favorites, paysages, commandes musicales et compagnon sur téléphone. L'interface est actuellement principalement en français.

[Télécharger la bêta](https://github.com/Marouane4142/MarOS/releases) · [Installation et retour arrière](INSTALLATION.md) · [Contribuer](../CONTRIBUTING.md) · [README English](../README.md)

## Au quotidien

Favoris ordonnés, collections, applications masquées, bannières personnalisables et accès au Play Store installé. Cinq ambiances, douze paysages, album personnel et mode ambiant. Les commandes musicales utilisent les sessions Android compatibles ; l'historique classe les lectures réellement exploitables du plus récent au plus ancien.

Le compagnon local permet d'envoyer des photos et des recherches et de modifier les favoris depuis le navigateur du téléphone. Ouvrir **Mon téléphone** sur la TV puis scanner le QR code. La session HTTP locale dure une heure et continue quand une autre application est affichée. Utiliser un réseau de confiance, sans exposer le port 8765 sur Internet.

La météo demande de choisir une ville ; aucune localisation personnelle n'est prédéfinie. L’agenda local et les calendriers Android sélectionnés après autorisation sont disponibles ; la synchronisation dépend du compte Android. Jellyfin, Home Assistant, Wake-on-LAN, caméra et assistant compatible Ollama restent des connecteurs facultatifs qui nécessitent votre équipement.

## Version 5

La branche actuelle prépare **5.0.0-beta.1** : accueil Compose à échelle réglable, lecteur musical complet à droite, essentiels fixés en bas et cartes de reprise illustrées, Studio de personnalisation, sections réorganisables, profils locaux et compagnon enrichi. Les éditeurs détaillés et services Java restent compatibles. Android 11/API 30 minimum, cible API 36. [Migration et limites](MIGRATION-5.md).

Les utilisateurs et le mode invité se choisissent depuis l’avatar en haut à droite. Le sélecteur Android permet d’associer les comptes Google déjà présents sur la box, sans projet Cloud. L’adresse choisie fournit un libellé local et une initiale ; la photo Google n’est pas accessible par cette seule sélection. L’agenda reprend automatiquement les calendriers Google synchronisés du profil, après autorisation Android. Les réglages quotidiens se limitent à Apparence, Applications et Téléphone ; les autres connexions sont dans Plus d’options.

La navigation comporte un indicateur de menu glissant, un léger agrandissement des cartes et de courtes transitions. Cast et la touche vocale utilisent les services Google du système ; MarOS n’ajoute pas de reconnaissance vocale. La liste personnelle est accessible dans Regarder. Seules les sauvegardes Drive facultatives demandent un client OAuth TV : [configuration initiale](GOOGLE-SETUP.fr.md).

## Première publication historique

La première bêta publique est **4.0.0-beta.1**, package `io.github.marouane4142.maros`, versionCode 40000. Android 11/API 30 minimum ; target SDK de cette ancienne version 30. Le prédécesseur privé a été testé sur Xiaomi TV Box S 2e génération. Cette bêta est une installation distincte, avec sa propre signature ; elle n'écrase pas l'ancien package privé.

Un APK installe le launcher. Le nettoyage du fabricant est une opération ADB séparée, facultative et propre au modèle. Les scripts de ce dépôt sauvegardent et journalisent leurs opérations ; ils ne désinstallent rien et n'effacent pas les données des applications.

## Compiler

Installer Python 3.10+, **JDK 21**, la plateforme Android **37.0**, les Build-Tools **36.0.0** et le SDK Android, puis définir `ANDROID_HOME` et `JAVA_HOME` :

```sh
python tools/check.py
python launcher/build.py
# Version optimisée, signée localement :
python launcher/build.py --local-release
```

Le résultat de développement est `launcher/build/MarOS-debug.apk`. La compilation ne se connecte pas à une TV. La signature de développement ne permet pas de mettre à jour un APK officiel signé autrement. Les releases officielles utilisent une clé privée conservée séparément.

## Limites et crédits

Les applications de streaming contrôlent leurs métadonnées et leurs liens de reprise. Une reprise universelle de tous les abonnements n'est pas promise. Les équipements non configurés ne sont pas présentés comme testés. Voir [validation](TESTING.md), [confidentialité](../PRIVACY.md) et [architecture](ARCHITECTURE.md).

MarOS dérive de [tv-home de Mihir Gupta](https://github.com/mihirgupta0900/tv-home). Le code est sous [MIT](../LICENSE), avec la notice d'origine conservée. Lucide, Manrope, Nayuki et les paysages gardent leurs propres licences : [crédits tiers](../THIRD_PARTY_NOTICES.md).
