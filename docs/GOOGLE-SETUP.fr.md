# Connecter MarOS à Google

MarOS utilise en priorité les comptes déjà présents sur Android TV. **Avatar en haut à droite → Utiliser un compte de la box** ouvre le sélecteur Android ; **Ajouter** associe un compte à un nouveau profil. La sélection donne à MarOS la visibilité de ce compte, sans lire de mot de passe ni de jeton système. Sélectionner à nouveau un compte déjà associé retrouve son profil au lieu de créer un doublon.

L’adresse choisie reste dans le stockage privé du launcher. Le nom affiché est dérivé de la partie locale de l’adresse ; l’avatar affiche une initiale. Ce parcours ne récupère pas automatiquement le nom complet ni la photo Google et n’est pas présenté comme une authentification OAuth. **Dissocier ce profil** retire l’association MarOS en conservant le compte Android et les réglages du profil.

**Afficher mon agenda Google** demande séparément l’autorisation Android de lire les calendriers. MarOS ne lit ensuite que les calendriers visibles et synchronisés du compte choisi. Si ce compte n’est plus visible pour MarOS, les calendriers de cette association ne sont plus lus. L’invité ne voit pas ces données.

Cast, la voix Google, le Play Store et la télécommande Google TV utilisent les services Android déjà installés. **Aucun projet Google Cloud n’est nécessaire pour ce parcours.**

## Sauvegardes Drive facultatives

Les sauvegardes locales et le compagnon fonctionnent sans Google Cloud. Pour conserver aussi une archive dans Drive, ouvrir **Sauvegardes → Google Drive · facultatif**. Seule cette fonction demande le client OAuth personnel décrit ci-dessous. Aucun compte Cloud ni identifiant fictif n’est fourni dans l’APK.

## 1. Créer le projet

1. Ouvrir la [console Google Cloud](https://console.cloud.google.com/) avec votre compte et créer un projet nommé, par exemple, **MarOS personnel**.
2. Dans **API et services → Bibliothèque**, activer **Google Drive API**. Les sauvegardes restent déclenchées manuellement.
3. Ouvrir **Google Auth Platform** et configurer les informations de l’application : nom MarOS, adresse d’assistance et adresse de contact.
4. Choisir l’audience **Externe** pour un compte Google personnel. Pendant les essais, garder le statut **Test** et ajouter votre propre adresse dans les utilisateurs de test. Pour un compte Workspace, les règles de votre organisation peuvent s’appliquer.
5. Dans **Accès aux données**, déclarer uniquement les autorisations demandées par cette version :

   ```text
   openid
   email
   profile
   https://www.googleapis.com/auth/drive.file
   ```

`drive.file` limite l’accès aux fichiers créés ou explicitement ouverts avec l’application. MarOS liste uniquement ses propres archives identifiées comme telles. Il ne demande pas l’accès général à votre Drive.

## 2. Créer le client TV

1. Dans **Google Auth Platform → Clients**, choisir **Créer un client**.
2. Sélectionner le type **Téléviseurs et appareils à saisie limitée** (*TVs and Limited Input devices*).
3. Nommer le client **MarOS Xiaomi TV**, puis le créer.
4. Télécharger immédiatement le JSON du client et le conserver dans `launcher/.local/google-client.json`, un dossier exclu de Git. Google ne garantit pas de réafficher le secret après la création. Il contient l’**ID client** (terminant par `.apps.googleusercontent.com`) et le **secret client** ; ne pas les coller dans une conversation ou un ticket.
5. Sur la TV, ouvrir **Sauvegardes → Google Drive · facultatif → Configurer le client Google TV** et renseigner ces deux valeurs. La télécommande Google TV permet de saisir le texte depuis le téléphone dans les champs Android.

Ce client ne demande ni URI de redirection, ni nom de package Android, ni empreinte SHA de l’APK. Ne pas choisir le type Android ou Application Web. Les identifiants sont stockés localement avec Android Keystore, exclus des exports et absents du compagnon HTTP. Un client installé dans une APK ne peut pas être considéré comme un secret de serveur.

## 3. Se connecter

1. Choisir **Autoriser les sauvegardes Drive** dans les services Google facultatifs sur la TV.
2. Sur votre téléphone, ouvrir l’adresse Google affichée, saisir le code et vérifier le nom de votre projet.
3. Autoriser l’identité Google et les fichiers Drive de MarOS. Aucun mot de passe Google n’est saisi dans MarOS.
4. Laisser le sélecteur de profils ouvert sur la TV jusqu’au résultat. Quitter la page ou choisir **Annuler** interrompt l’attente.
5. Dans **Réglages → Apparence → Plus d’options → Sauvegardes**, tester **Sauvegarder sur mon Drive**, puis **Mes sauvegardes Drive**. Avant une restauration, MarOS télécharge et valide l’archive, affiche son contenu et demande de confirmer la fusion.

Les archives contiennent vos réglages, photos locales, bannières, profils, liste de titres, agenda local et observations de lecture. Les jetons Google, clés Immich, mots de passe et autres secrets en sont exclus. Une copie locale de l’état précédent est conservée avant chaque restauration. Vos photos et données personnelles se retrouvent sur votre Drive lorsque vous choisissez de les y envoyer.

Une application en mode Test peut demander une nouvelle autorisation après expiration de ses jetons. Les contraintes de publication et de vérification dépendent des autorisations et de l’audience choisies ; consulter la console avant de distribuer publiquement un client partagé. Pour déconnecter MarOS et révoquer son accès cloud, utiliser **avatar → Dissocier ce profil**. Le compte Android de la box est indépendant.

## Agenda Google

L’agenda ne possède plus de page de réglages ni de liste de calendriers à cocher. Après le choix du compte de la box, **Afficher mon agenda Google** permet d’autoriser la lecture Android des agendas. MarOS affiche automatiquement les calendriers visibles et synchronisés dont le compte Google correspond exactement au profil actif. Les occurrences récurrentes sont développées par Android. Le mode invité masque ces données ; changer de profil change le compte recherché.

L’autorisation Google et la présence du compte dans Android restent deux éléments distincts : un nouveau compte connecté uniquement à MarOS n’ajoute pas silencieusement un compte système à la Xiaomi. Si Android ne synchronise aucun calendrier pour ce compte, MarOS n’invente pas de rendez-vous. Le bouton contextuel **Comptes Android** du profil permet d’accéderder à la synchronisation native. Le flux OAuth TV de Google ne permet pas Calendar API ; une connexion cloud directe indépendante du fournisseur Android demanderait un autre parcours OAuth autorisé.

## Cast, voix, photos et téléphone

- **Cast** : conserver le récepteur Google Cast intégré, connecter téléphone et box au même réseau, puis choisir la box depuis l’icône Cast d’une application compatible. Aucune page de réglages Cast n’est nécessaire dans MarOS ; la diffusion utilise directement le récepteur système.
- **Télécommande** : l’application Google TV sur téléphone fournit navigation globale, clavier et commandes système. Le compagnon MarOS contrôle son propre accueil, lance les applications et pilote les lecteurs qui exposent une session multimédia.
- **Voix Google** : MarOS n’ajoute plus de micro, de dictée ou de reconnaissance vocale. Le bouton vocal de la télécommande reste géré par Google et le firmware de la box.
- **Google Photos** : dans le compagnon du téléphone, sélectionner les images via le sélecteur du téléphone, puis les envoyer à la TV. Ce choix peut donner accès à Google Photos selon le téléphone. MarOS ne synchronise pas automatiquement toute une photothèque Google.

## En cas d’erreur

| Erreur ou symptôme | Vérification |
|---|---|
| Client invalide / type non autorisé | Recréer un client de type TV et vérifier l’ID et le secret. |
| Accès refusé | Ajouter le compte dans les utilisateurs de test, vérifier l’audience et les règles Workspace. |
| Portée refusée | Déclarer les quatre portées ci-dessus ; ne pas ajouter Calendar ou Photos au flux TV. |
| Drive indisponible | Activer Google Drive API dans le même projet et vérifier l’autorisation accordée. |
| Code expiré | Relancer la connexion ; chaque code a une durée limitée. |
| Aucune sauvegarde listée | La liste concerne les archives créées par MarOS avec ce client ; les ZIP téléchargés ailleurs se restaurent via le compagnon. |

## Références

- [Flux OAuth officiel pour TV](https://developers.google.com/identity/protocols/oauth2/limited-input-device)
- [Autorisations Google Drive](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)
- [Configurer le consentement](https://developers.google.com/workspace/guides/configure-oauth-consent)
- [Télécommande Google TV](https://support.google.com/googletv/answer/11136134)
- [Google Photos et autorisations disponibles](https://developers.google.com/photos/overview/authorization)

Vérification documentaire : 31 octobre 2026. Le parcours de connexion et Drive doit encore être testé avec votre propre client OAuth ; aucun compte Google n’a été connecté automatiquement pendant le développement.

## Coût et limites

L’usage standard de l’API Drive est actuellement sans supplément, dans ses quotas. Google prévoit une évolution des dépassements : consulter la [tarification et les quotas officiels](https://developers.google.com/workspace/drive/api/guides/limits). La configuration personnelle MarOS doit rester sans compte de facturation associé et sans service d’hébergement. Les sauvegardes utilisent l’espace Drive déjà disponible sur votre compte ; une extension Google One reste un achat distinct et facultatif.

La configuration Cloud est faite via l’outil officiel Google lorsque la console automatisée refuse la connexion. L’authentification se fait dans le navigateur habituel, jamais en envoyant un mot de passe ou un code dans la conversation. Aucun projet ni client n’est annoncé comme créé avant vérification réelle.
