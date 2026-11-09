# Émission : déroulé du direct, plateau, révélation

Tout ce qu'il faut pour tourner une émission CraftMaster sans taper de longues commandes : la saison est préparée
avant, le direct se mène avec quelques commandes courtes (ou à la voix), et la fin est une révélation mise en scène.

## Organisation des données
```
Saison (Saison 4)            joueurs de la saison + surnoms, scores publiés
 └─ Partie (Partie 1)        invisible par défaut : créée toute seule, on n'en parle jamais s'il n'y en a qu'une
     └─ Manches              Le Pont, La Tour… dans l'ordre ; chrono, points de TP, scène / décor, surnoms
```
Une soirée = une partie. Plusieurs soirées dans une saison : `/craftMasterPartie create "Finale"` (le classement de
la saison reste la somme de toutes ses parties).

## Préparer le plateau (une fois)
1. **Rideau de scène** : construire le rideau ([Rideaux](Rideaux.md)), le viser et
   `/craftMasterStage curtain set stage`. D'autres rideaux se nomment comme on veut (`fond`, `coulisses`…).
2. **Décors** (facultatif) : construire un décor quelque part, puis
   `/craftMasterStage scene save foret <coin1> <coin2>` (64 blocs max par côté ; les blocs de vide de structure ne
   remplacent rien). Choisir l'**ancre** : le bloc de la scène où se pose le coin bas nord-ouest des décors,
   `/craftMasterStage anchor` en le visant. `/craftMasterStage scene place foret` le pose (rideau fermé conseillé) ;
   le décor posé avant est d'abord effacé.
3. **Busts** du podium ([Busts](Busts.md)).
4. `/craftMasterStage info` résume le tout.

## Préparer la saison
```
/craftMasterSeason create Saison 4
/craftMasterPlayer add LordFinn Finn
/craftMasterPlayer add Ensuri Ensu, Enzo
/craftMasterManche create "Le Pont" 180        (chrono de 3 min)
/craftMasterManche create Finale
/craftMasterManche tp add "Le Pont"             (sur chaque emplacement de départ, regard compris)
/craftMasterManche stage Finale true
/craftMasterManche scene Finale foret
```
Les surnoms (joueurs et manches) servent à la régie vocale. `/craftMasterManche list` et `/craftMasterPlayer list`
vérifient le tout.

## Le direct
- **Lancer une manche** : `/cfma next` (ou `/cfma start "Le Pont"`). Tout le monde voit le grand titre
  « Manche 1 » avec le nom en dessous, entend un cor ; les joueurs de la saison connectés sont téléportés sur les
  points de la manche (répartis à tour de rôle) ; une manche sur scène pose son décor.
- **Chrono** : barre de boss jaune qui se vide (« Le Pont — 2:31 »), rouge et décompte en grand avec un son sur les
  10 dernières secondes, puis « Temps écoulé ! » avec cor et cloche. `/cfti stop` l'arrête.
- **Donner des points** : `/cfg Ensu 3` (pseudo ou surnom, manche en cours). Tout le monde lit
  « Ensuri +3 (Le Pont) » dans la barre d'action, le joueur voit « +3 » en grand. Pour une autre manche :
  `/cfg Ensuri 3 Finale`.
- **Fin de manche** : `/cfma end` (titre « Fin de la manche »), ou directement `/cfma next`.
- **Mettre à jour le classement** : `/craftMasterPublish`. Jusque-là, joueurs, busts et tableaux montrent les
  scores de la dernière publication ; les organisateurs voient le direct avec « +3 non publié ».
- **Erreur** : `/craftMasterUndo` (ou `/cfun 3` pour les 3 dernières). Une liste d'actions de la voix s'annule
  d'un coup. `/craftMasterHistory` montre ce qui sera annulé.
- **Où en est-on ?** `/craftMasterStatus`.
- **Rideaux** : `/cfsg curtain close` (scène) / `/cfsg curtain open fond`.

## La révélation
`/craftMasterReveal start` :
1. titre « La révélation ! », le rideau de scène se ferme ;
2. roulement de tambour qui accélère ;
3. publication des scores (et la saison n'est plus cachée) ;
4. du dernier au premier : titre « 4e — Ensuri » / « 12 pts », son (plus fort pour le podium), le bust du rang
   s'illumine ; les trois premiers restent plus longtemps ;
5. feux d'artifice sur les busts du podium (or pour le premier) ;
6. le rideau s'ouvre.

Durées : `/craftMasterReveal timing <rideau> <tambour> <par joueur> <podium>` (secondes ; défaut 3 4 3 5).
`/craftMasterReveal stop` interrompt (titres effacés, rideau rouvert ; ce qui est publié le reste).
Sans rideau `stage` enregistré, la révélation se fait sans rideau.

## À savoir
- Le chrono et la révélation en cours sont oubliés si le serveur redémarre ; la manche en cours, elle, est
  enregistrée.
- Le journal d'annulation (50 modifications) est en mémoire : il repart à zéro au redémarrage et au
  `/craftMasterReloadScores`. TP, titres, rideaux et décors posés ne s'annulent pas (seules les données).
