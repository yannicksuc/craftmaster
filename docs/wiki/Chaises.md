# Chaises

12 essences : chêne, sapin, bouleau, acajou, acacia, chêne noir, palétuvier, cerisier, chêne pâle, bambou, carmin,
biscornue (`craftmaster:<bois>_chair`). Recette : 4 planches et 2 bâtons en forme de chaise → 2 chaises.

- Un seul modèle pour les 12 essences, dans les textures du jeu (planches et bûche écorcée de l'essence) : pieds et
  montants en bûche, assise en planches, dossier à traverse, deux barreaux et bandeau.
- Posée face à soi (le dossier de l'autre côté), dans **16 directions** comme un panneau debout (pas de 22,5°) ; le
  joueur assis regarde dans le sens de la chaise. Propriétés `facing` (le quart) et `tilt` (0 à 3 pas de 22,5° dans le
  sens horaire) : les chaises posées avant gardent leur sens. Le trône et la chaise de candidat aussi.
- Clic droit (sans être accroupi) : on s'assoit. Le corps suit le sens de la chaise, la tête peut tourner d'environ
  100° de chaque côté.
- S'accroupir pour se lever : on se retrouve devant la chaise (ou dessus s'il n'y a pas la place).
- Une chaise occupée affiche « Quelqu'un est déjà assis ici ».
- Casser la chaise lève la personne assise. Se déconnecter assis : on se reconnecte assis.
- Techniquement : le joueur monte sur une entité invisible `craftmaster:seat` qui disparaît dès qu'elle est vide
  (pas d'invocation possible par `/summon`).
