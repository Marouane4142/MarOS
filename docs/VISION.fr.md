# MarOS · le salon personnel

MarOS est un launcher Kotlin/Compose for TV sur Android TV. La box conserve les applications de streaming, le récepteur Cast certifié, les comptes Google, HDMI-CEC et les mises à jour système. L’accueil MarOS ne contient ni publicité, ni SDK publicitaire, ni télémétrie.

## Fonctionnalités implémentées

| Fonction | Disponible dans MarOS | Configuration ou limite |
|---|---|---|
| Accueil fixe | Musique centrée à droite, essentiels sur le fond en bas, reprise après une flèche bas depuis les essentiels. Aucune liste d’apps récemment ouvertes. | Échelle et texte indépendants. Rendu de la box vérifiée : 1080p à 59,94 Hz, même sur une dalle 4K. |
| Téléphone | Compagnon local appairé : navigation MarOS, lancement d’apps, volume, lecteurs compatibles, scènes, invités, photos, agenda, liste à regarder et sauvegardes. | Pour le clavier et la navigation dans les autres apps, utiliser la télécommande système de l’application Google TV. |
| Google | Choix d’un compte déjà présent sur Android TV, association aux profils et agenda du compte après autorisation. Drive est une sauvegarde facultative. | Comptes natifs sans projet Cloud ; libellé local et initiale, sans photo Google. Seul Drive demande un client OAuth TV : [guide de configuration](GOOGLE-SETUP.fr.md). |
| Chromecast | État du récepteur, diagnostic réseau local et accès aux réglages. | Utilise le service Google Cast existant. Le diagnostic ne certifie pas la lecture d’un contenu. |
| Agenda | Calendriers Google automatiquement associés au profil après autorisation, récurrences développées par Android, agenda local et import ICS. | Google doit synchroniser les agendas sur la box. Calendar API n’est pas accessible au client OAuth TV. L’import ICS ne développe pas les règles complexes. |
| À regarder | Titres personnels, saison/épisode, service, fiche, recherche TVmaze explicite, prochaines sorties. | Liste distincte de l’historique observé. Métadonnées optionnelles TVmaze, attribution CC BY-SA, actualisation limitée. |
| Scènes | Cinéma, musique, jeu et nuit, thème/application/scène Home Assistant configurables. | Lumières, réveil PC et serveurs à configurer. Nuit ouvre l’ambiance, sans simuler une extinction système. |
| Invités | Masque agenda, reprise, vidéothèque, compte Google, liste personnelle et photos/bannières privées. | Confidentialité locale ; ce n’est pas un contrôle parental Android. |
| Cadre photo | Albums locaux, favoris/exclusions, Immich et listes HTTPS, ambiance et synchronisation Immich facultative. | Travail automatique toutes les six heures au plus, pendant l’inactivité et sur réseau non facturé. Google Photos passe par la sélection sur téléphone. |
| Centre rapide | Réglages Android réseau/Bluetooth/son/stockage, minuteur et diagnostic. | Les paramètres système s’ouvrent dans les écrans Android. |
| Sauvegardes | ZIP personnel, validation avant fusion, profils/images/liste/agenda local/observations de lecture, copie avant restauration. | Secrets exclus. Le JSON reste un export visuel léger ; Drive est facultatif. |

Ces fonctions affichent un état vide ou une explication lorsque les services ne sont pas configurés. Installer le launcher n’autorise pas l’accès aux comptes et serveurs privés. Les comptes streaming et les données non partagées par les applications restent gérés par ces applications.

## Ressources et fluidité

La première vue n’est pas dans un conteneur vertical défilant : changer de favori ne fait plus remonter l’accueil. Les listes horizontales se déplacent seulement lorsqu’un élément sort de la zone visible. Les cartes d’apps utilisent une bordure de focus sans agrandissement, dessinée sans recomposer leur contenu. Le fond et ses dégradés sont mis en cache dans une couche graphique séparée. Les images sont chargées hors du fil principal avec concurrence et caches bornés.

L’horloge se rafraîchit à la minute. Les changements de lecteur utilisent les notifications système ; la position ne tourne à la seconde qu’en lecture. Les écouteurs se détachent lorsque le launcher est masqué. La synchronisation automatique est désactivée par défaut et ne tourne pas dans le chemin de navigation.

La cible est une navigation adaptée au rafraîchissement de la box. Les mesures de rendu ne doivent pas être confondues avec une garantie de 60 FPS dans toutes les situations.

## Références

- [Conception Android TV](https://developer.android.com/design/ui/tv/guides/foundations/design-for-tv)
- [Télécommande Google TV](https://support.google.com/googletv/answer/11136134)
- [Flux OAuth TV et portées autorisées](https://developers.google.com/identity/protocols/oauth2/limited-input-device)
- [Google Photos et autorisations](https://developers.google.com/photos/overview/authorization)
- [API et licence TVmaze](https://www.tvmaze.com/api)

## Navigation et réglages simplifiés

Le menu supérieur possède un indicateur de focus animé. Les cartes s’agrandissent légèrement et les changements de vue utilisent une courte entrée en fondu et déplacement ; les valeurs animées sont lues dans les phases de dessin/couche graphique. Les animations réduites restent disponibles. Les éléments de l’accueil restent à leur place en naviguant horizontalement.

L’avatar en haut à droite ouvre les utilisateurs et le mode invité. Le nom et la photo viennent de Google après connexion ; avant cette connexion, un profil local et son initiale restent explicitement affichés. Les jetons sont séparés par profil. Les réglages quotidiens comportent trois rubriques : Apparence, Applications et Téléphone. Les connexions facultatives et sauvegardes sont accessibles dans Plus d’options. Cast, voix, agenda, liste à regarder et scènes ne sont plus des onglets de réglages. La liste de titres se trouve dans Regarder ; la voix reste celle de Google.
