# Rideaux de scène

16 couleurs (`craftmaster:<couleur>_curtain`), en velours à plis profonds (un pli tous les 4 pixels : creux sombre,
flanc, crête claire), ourlet cousu sur le bloc du bas seulement. Recette : une ficelle au-dessus de deux laines de
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
- **Commande** : `/cm plateau rideau bloc ouvrir|close|toggle <x y z>`.

## L'animation
Le tissu **glisse en continu** : les deux moitiés partent du milieu et vont se ranger sur les côtés, où elles se
**rassemblent en plis serrés et profonds** (environ 16 % de la largeur de chaque moitié, 6 pixels au moins) ; la
fermeture fait l'inverse. Le départ et l'arrivée sont adoucis, le bas du rideau suit le haut avec un léger retard
(le tissu penche dans le sens du mouvement), ondule un peu pendant qu'il bouge et se balance une fois à l'arrêt.
Durée : celle d'avant (un rideau de 15 blocs de large s'ouvre en 1,6 s environ), avec un bruit de tissu.

Ouvert, il ne reste que les deux paquets de plis sur les côtés ; on peut passer partout. Les blocs du milieu
restent visables pour les casser ou lier la télécommande en tenant un rideau ou la télécommande. Relancer une
commande pendant l'animation repart dans l'autre sens, depuis l'endroit où est le tissu.

Comment c'est fait : le serveur change toujours l'état des blocs colonne par colonne (ce qui se sauvegarde, ce
qu'on vise), et envoie aux joueurs qui voient le rideau un paquet `craftmaster:curtain_slide` ; chaque jeu dessine
alors le tissu (un rendu de bloc-entité par bloc, `CurtainRenderer`) là où le glissement l'a amené. Tout le monde
voit donc la même chose, en multijoueur comme en solo ; un joueur qui arrive pendant l'animation voit le rideau dans
l'état de ses blocs. Les rideaux posés avant cette version sont repris automatiquement (rien à refaire).
