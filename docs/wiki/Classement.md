# Classement

Le total d'un joueur sur une saison est la somme de tous ses scores de cette saison (y compris ceux d'une tâche
retirée). Égalité : l'ordre dans lequel les joueurs ont reçu leur premier score.

## L'écran (touche K)
- Touche **K** (modifiable dans Options > Commandes > CraftMaster) ou `/craftMasterRanking`.
- Podium or / argent / bronze, tête de chaque joueur, barre proportionnelle au premier, sa propre ligne surlignée.
- Survol d'une ligne : points par tâche ; « Autres tâches » regroupe les points de tâches retirées ou inconnues.
- ◀ ▶ (boutons ou flèches du clavier) : saison précédente / suivante. Molette : défiler. K ou Échap : fermer.
- Se met à jour en direct quand un organisateur change un score.

## Saisons cachées
`/craftMasterHideRanking <saison> true` garde le suspense :
- les joueurs ne voient ni l'écran ni le classement dans le chat pour cette saison (« Aucun classement à afficher ») ;
- les busts deviennent anonymes : tête sans skin, étiquette « #1 ? » ;
- les organisateurs voient tout, avec la mention « cachée aux joueurs ».

`/craftMasterHideRanking <saison> false` révèle tout d'un coup et l'annonce dans le chat.
L'état est enregistré dans le fichier (`seasons.<n>.hidden: true`) et survit aux redémarrages.
