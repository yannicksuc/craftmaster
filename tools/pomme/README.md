# La pomme : escape room (pack de données vanilla)

`pomme.schem` est la salle de LordFinn. `gen.py` en fait le pack de données `craftmaster-pomme` :

```
python -I gen.py pomme.schem <dossier>/craftmaster-pomme
python -I verif.py pomme.schem <dossier>/craftmaster-pomme/data/pomme/function/verif.mcfunction
```

Sur le serveur, le pack est dans `craftmaster/datapacks/` et la salle est dans le monde `minecraft:pomme` :

- la référence est en `0 60 0` ;
- les 4 salles des joueurs sont en `65`, `130`, `195` et `260`.

Commandes :

- `/cm arene code pomme poules` montre le code du panneau « ? : ». Il se règle ainsi (une fois, déjà fait sur le serveur à l'installation) :
  `/cm arene code pomme poules reponses riricococo`, `… syllabe co`, `… syllabe cot cote | cotte`, `… syllabe ri`.
  La réponse est lue en syllabes co / cot / ri : séparateurs libres, majuscules, accents et ponctuation ignorés, mais il faut les mêmes syllabes, dans le même ordre et en même nombre ;
- `function pomme:construire/reference` colle la référence depuis le schéma : `setblock`/`fill … strict`, sans aucune mise à jour, puis les entités ;
- `function pomme:tout_refaire` refait les 4 salles depuis la référence : `clone … strict` puis les entités ;
- `execute in minecraft:pomme as @e[type=marker,tag=pomme_salle] at @s align xyz run function pomme:salle/sans_canne` met à jour les salles construites avant le 20/11 (référence comprise) : plus de canne à sucre sur l'étagère ni de sable dans les tonneaux, les 3 sables posés (un sur la terre cuite jaune du poulailler, deux dans le sol du couloir qui y mène) ;
- `… run function pomme:salle/cachettes` ajoute, une fois par salle, les objets de `CACHETTES` (gen.py ; vide pour l'instant) ;
- **Changements de LordFinn dans la référence** : `save-all flush` sur le serveur, rapatrier
  `craftmaster/dimensions/minecraft/pomme/region/r.0.0.mca` et `entities/r.0.0.mca`, puis
  `python -I import_ref.py gen.py pomme.schem <region r.0.0.mca> <entities r.0.0.mca> modifs.json` : les blocs qui
  diffèrent, les contenus de conteneurs et les décors (cadres, tableaux, displays, avec leurs passagers) vont dans
  `modifs.json`, que gen.py applique par-dessus le schéma ; régénérer le pack, `verif` donne 0 sur la référence, puis
  `pomme:salle/refaire` sur chaque copie (personne dedans) ;
- `execute in minecraft:pomme positioned <x>.0 60 0.0 run function pomme:verif` puis `scoreboard players get #diff pomme_t` compte les blocs différents du schéma (0 attendu).

Le sucre du gâteau vient du miel : pelle en fer → sable → four (deux parmi les hauts-fourneaux) → verre → fioles → ruches pleines (28-30 12 13-15) → fiole de miel → sucre. Le seau d'eau se vide sur le béton en poudre blanc (38 3 25), qui durcit ; la pioche en fer casse le béton blanc, ce qui ouvre le coffre en cuivre de la tête de Steve (38 2 25).

Ne pas utiliser `/cm arene reset pomme` avec un mod plus ancien que le correctif « copie sans mise à jour ».
