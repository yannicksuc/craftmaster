# Busts

Un bust est une tête de joueur posée dans le monde qui porte le skin du joueur classé à un rang d'une saison,
selon les **scores publiés** ([Classement](Classement.md)).

1. Poser une tête de joueur (`minecraft:player_head`, au sol ou au mur).
2. La regarder (8 blocs max) et taper `/cm plateau buste ajouter <saison> <rang>`.
3. La tête prend le skin du joueur à ce rang, une étiquette flotte au-dessus : « #1 Pseudo / 16 pts ».

- Mise à jour à chaque publication (et toute modification), au `/cm scores recharger` et au démarrage du serveur.
- Pendant la révélation, le bust du joueur révélé s'illumine (particules) et le podium a ses feux d'artifice.
- Personne à ce rang : tête par défaut, étiquette « #3 — ».
- Saison cachée : tête par défaut, étiquette « #1 ? » ([Classement](Classement.md)).
- Le skin est chargé par le jeu de chaque joueur à partir du pseudo (comme une tête donnée avec `/give`).
- Une étiquette dont le chunk n'était pas chargé se remet à jour quand il se charge ; celle d'un bust supprimé
  disparaît à ce moment-là.
- Les étiquettes sont des `text_display` portant le tag `craftmaster.bust` : à ne pas supprimer à la main
  (elles reviennent au prochain changement de score).
