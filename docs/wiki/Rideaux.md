# Rideaux de scène

16 couleurs (`craftmaster:<couleur>_curtain`), en velours plissé. Recette : une ficelle au-dessus de deux laines de
la couleur → 2 rideaux. Ils sont aussi dans l'onglet créatif « CraftMaster ».

## Construire un rideau
- Poser les blocs côte à côte et les uns sur les autres. Un bloc posé contre un rideau en reprend le sens et l'état.
- Tous les blocs de rideau qui se touchent (à gauche, à droite, dessus, dessous) **et regardent dans le même sens**
  forment un seul rideau, quelle que soit leur couleur (jusqu'à 4096 blocs).
- On traverse un rideau (pas de collision) : les acteurs peuvent passer à travers.

## L'ouvrir et le fermer
- **Redstone** : un signal sur n'importe quel bloc du rideau l'ouvre, la perte du signal le ferme (levier, bouton,
  plaque de pression, horloge…).
- **Télécommande de rideaux** (recette : redstone au-dessus de deux lingots de fer) : clic droit sur un rideau pour
  la lier (et l'ouvrir / le fermer), puis clic droit n'importe où, à n'importe quelle distance, pour l'actionner.
  L'objet brille une fois lié, son infobulle donne la position.
- **Créatif** : clic droit main vide sur le rideau.
- **Commande** : `/craftMasterCurtain open|close|toggle <x y z>`.

## L'animation
Ouverture du milieu vers les côtés, fermeture des côtés vers le milieu, une colonne tous les 4 ticks (un rideau de
15 blocs de large s'ouvre en 1,5 s environ), avec un bruit de tissu. Un rideau ouvert est invisible ; on le revoit
(pour le casser ou lier la télécommande) en tenant un rideau ou la télécommande. Relancer une commande pendant
l'animation la fait repartir dans l'autre sens.
