# La pomme : escape room (pack de données vanilla)

`pomme.schem` est la salle de LordFinn. `gen.py` en fait le pack de données `craftmaster-pomme` :

```
python -I gen.py pomme.schem <dossier>/craftmaster-pomme
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

`gen.py` écrit lui-même `pomme:verif` avec les modifs de LordFinn. Ne pas lancer `verif.py` sur le pack : il réécrit cette fonction d'après le schéma seul et compte alors ses modifs comme des différences.

Les poules : après le code, le bouton bleu fait apparaître 12 poules qui ont chacune un prénom, et celles déjà dans la salle en reçoivent un aussi. Une seule s'appelle Giselle (comme sur le panneau) et elle ne peut pas mourir. Attirée avec des graines à moins de 2,5 blocs du corail (30 5 31), elle s'y pose et n'en bouge plus, le bulb (30 6 32) s'allume et un œuf arrive toutes les 10 s dans le tonneau dessous (30 4 31). Les poules restent adultes « au repos » (Age positif) : elles suivent la graine sans la manger, donc la seule graine de la salle (étagère 28 4 11) ne se perd pas. Les graines ne se plantent plus. La houe en fer ne laboure plus et casse seulement les bottes de foin (9 blés chacune ; deux sont accessibles, en 48 3 27 et 48 3 33) ; les plants sont du décor. La poudre d'os ne sert plus : celle qui resterait dans un conteneur n'a plus aucun effet.

Le sucre du gâteau vient du miel : pelle en fer → sable → four (deux parmi les hauts-fourneaux) → verre → fioles → ruches pleines (28-30 12 13-15) → fiole de miel → sucre. Le seau d'eau se vide sur le béton en poudre blanc (38 3 25), qui durcit ; la pioche en fer casse le béton blanc, ce qui ouvre le coffre en cuivre de la tête de Steve (38 2 25).

Ne pas utiliser `/cm arene reset pomme` avec un mod plus ancien que le correctif « copie sans mise à jour ».
