# Classement

Le total d'un joueur sur une saison est la somme de tous ses scores de toutes les parties de la saison (manches et
parties archivées comprises). Égalité : l'ordre des joueurs de la saison (ordre d'arrivée).

## Scores en direct et scores publiés
- **En direct** : ce que les organisateurs donnent pendant la soirée (`/cm points`, la voix, `/cm scores fixer`…). Le « +3 » est
  annoncé tout de suite à tout le monde dans la barre d'action, mais le classement des joueurs ne bouge pas.
- **Publiés** : une copie figée faite par `/cm scores publier` (« mettez à jour les scores »), la révélation ou
  `/cm classement cacher <saison> false`. C'est ce que voient les **joueurs** (écran K, classement dans le chat),
  les **busts** et leurs **étiquettes** (et les tableaux suspendus).
- Les **organisateurs** voient le direct : l'écran affiche « · scores en direct » et, à côté d'un total qui n'est pas
  encore publié, « +3 non publié » ; l'infobulle donne le total publié. `/cm statut` dit combien de joueurs ont des
  points non publiés.
- **Un joueur de la saison sans aucun point compte pour 0** : il est classé (dernier, ex aequo avec les autres à 0),
  en direct tout de suite, et dans le classement publié dès la première publication.
- **À la publication**, chaque joueur reçoit dans le chat ce qui change pour lui depuis la publication d'avant : les
  épreuves dont le score a bougé (« Le Tas de terre : +3 (4 → 7) »), son total et son rang (▲ / ▼). Rien de détaillé
  pendant qu'une saison est cachée (la révélation garde la surprise).
- Une nouvelle saison n'a rien de publié : les joueurs voient un classement vide jusqu'à la première publication.
  Les saisons venues de l'ancien format sont publiées à la conversion (rien ne change pour les joueurs).

## L'écran (touche K)
- Touche **K** (modifiable dans Options > Commandes > CraftMaster) ou `/cm classement`.
- Podium or / argent / bronze, tête de chaque joueur, barre proportionnelle au premier, sa propre ligne surlignée.
- Survol d'une ligne : points par manche (préfixées par la partie s'il y en a plusieurs) ; « Autres manches »
  regroupe les points de manches archivées ou supprimées.
- ◀ ▶ (boutons ou flèches du clavier) : saison précédente / suivante. Molette : défiler. K ou Échap : fermer.
- Se met à jour en direct (les joueurs à chaque publication).

## Saisons cachées
`/cm classement cacher <saison> true` garde le suspense :
- les joueurs ne voient ni l'écran ni le classement dans le chat pour cette saison (« Aucun classement à afficher ») ;
- les busts deviennent anonymes : tête sans skin, étiquette « #1 ? » ;
- les organisateurs voient tout, avec la mention « cachée aux joueurs ».

`/cm classement cacher <saison> false` publie les scores, révèle tout d'un coup et l'annonce dans le chat. La
révélation mise en scène (`/cm revelation lancer`, [Émission](Emission.md)) le fait aussi, avec spectacle.
