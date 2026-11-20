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
- `execute in minecraft:pomme as @e[type=marker,tag=pomme_salle] at @s align xyz run function pomme:salle/cachettes` ajoute, une fois par salle, les objets cachés ajoutés au schéma après sa construction (3 sables : `CACHETTES` de gen.py ; ils cuisent dans les deux fours parmi les hauts-fourneaux) ;
- `execute in minecraft:pomme positioned <x>.0 60 0.0 run function pomme:verif` puis `scoreboard players get #diff pomme_t` compte les blocs différents du schéma (0 attendu).

Ne pas utiliser `/cm arene reset pomme` avec un mod plus ancien que le correctif « copie sans mise à jour ».
