# Effets d'écran : noir et blanc, fish-eye, judas, lunettes floues…

Des filtres sur l'image du jeu, comme dans une émission : passer le stream en noir et blanc pour un flash-back,
une vue « judas de porte » pour une caméra cachée, un vieux film pour l'intro… et des **lunettes à porter** qui
changent la vue de celui qui les met (« faites l'épreuve avec des lunettes floues ! »).

Les effets touchent **seulement l'image du monde** : le logo, les bandeaux, les synthés et les scores de la
diffusion restent nets par-dessus (comme le chat, la barre d'objets, les menus). Chaque effet arrive et repart en
douceur (environ une demi-seconde) : un fish-eye se courbe petit à petit, le masque rond se ferme comme un iris.

## Les effets

| Effet | Ce qu'on voit |
|---|---|
| `noir_blanc` | Noir et blanc, un peu contrasté |
| `sepia` | Vieille photo brune |
| `pixel` | Gros pixels, couleurs de vieille console |
| `fisheye` | Fish-eye : image très bombée, on voit plus large |
| `vignette` | Bords assombris |
| `masque_rond` | Tout est noir autour d'un rond (avec `fisheye` : un judas de porte) |
| `citrouille` | La vue à travers une citrouille sculptée, **exactement celle du jeu** (comme quand on porte une citrouille) |
| `flou` | Tout est flou |
| `vhs` | Cassette VHS : lignes qui tremblent, bande qui défile, couleurs qui bavent |
| `vieux_film` | Film muet : noir et blanc chaud, grain, rayures, image qui saute |
| `infrarouge` | Caméra thermique : décor froid (bleu, violet), joueurs, animaux et feu chauds |
| `reve` | Rêve / flash-back : halo lumineux, couleurs pastel, bords brumeux, petites étincelles |
| `ondule` | Vertige : l'image ondule et se dédouble |
| `jumelles` | Vue aux jumelles (deux ronds) et zoom ×2 |
| `bd` | Bande dessinée : aplats, contours à l'encre, trame |
| `negatif` | Négatif photo |
| `look_intro` | Look intro d'épreuve (« vieux reportage ») : deux teintes (ombres brun sombre tirant sur le bleu-vert, hautes lumières crème), couleurs un peu éteintes, grain fin, vignette et bords flous. Plus léger que `vieux_film` |
| `macro` | Faible profondeur de champ : net sur ce qui est **au centre de l'image**, tout le reste très flou (flou en disques, comme un objectif macro). Se règle tout seul sur la distance du sujet au centre |
| `mise_au_point` | Bascule de mise au point : le plan commence net sur le fond (le sujet au centre est flou), puis après 0,4 s la mise au point glisse vers le sujet en 1,2 s et y reste. Rejouée à chaque nouveau plan |
| `stop_motion` | Stop motion : l'image ne se renouvelle que 7 fois par seconde, chaque image un peu décalée et plus ou moins exposée, comme une animation faite à la main |
| `timelapse` | Timelapse : le temps du monde file (une journée en 20 s) : ciel, soleil, lune, lumière du jour et nuages. Sur la diffusion seulement, le temps du serveur ne bouge pas ; tout revient à l'heure dès que l'effet s'arrête |

**Combiner** : on les met bout à bout avec `+`, dans n'importe quel ordre (le mod les dessine toujours dans le bon :
d'abord la profondeur de champ, le stop motion, les déformations, les couleurs, puis les masques) :
`fisheye+masque_rond+sepia`. Six effets au plus.

**Combinaisons prêtes** : `judas` (fisheye + masque rond), `camera_cachee` (fisheye + VHS + vignette),
`souvenir` (sépia + rêve + vignette), `film_muet` (vieux film + vignette), `cauchemar` (ondule + négatif + vignette).

Les noms français marchent aussi : « noir et blanc », « sépia », « fish-eye », « thermique », « pixelisé »,
« look intro » (ou « taskmaster », « vieux reportage »), « bascule de mise au point », « profondeur de champ »,
« temps accéléré »…

### Pour les intros d'épreuve

Les effets `look_intro`, `macro`, `mise_au_point`, `stop_motion` et `timelapse` sont faits pour les intros façon
Taskmaster (docs/design/intro-epreuve.md) : `look_intro` sur toute l'intro (par la régie : `/fx diffusion
look_intro`), puis un traitement par plan (`/tv intro <m> plan 3 effet macro`, `plan 4 effet mise_au_point`,
`plan 2 effet timelapse`…). Ils se combinent : `look_intro+macro`.

- `macro` et `mise_au_point` lisent la **profondeur** de l'image : ils marchent sur toutes les vues (caméras,
  trajets, joueur suivi). Le sujet est ce qui est au centre de l'image : on cadre l'objet au centre. Pour un vrai
  rendu macro, réduire aussi le champ de vision (`/cam zoom 20`).
- `mise_au_point` repart de zéro à chaque nouveau plan, même si le plan d'avant l'avait déjà.
- `timelapse` ne dessine rien : il avance l'heure **sur le jeu qui l'affiche** (le jeu de diffusion). Les joueurs
  et le serveur gardent leur heure.

## Combiner les effets
- Plusieurs d'un coup : `/fx diffusion fisheye+masque_rond+sepia` (6 au plus).
- **Ajouter sans rien enlever** : un `+` devant. `/fx diffusion +vignette` garde ce qui est déjà là et ajoute la
  vignette. À la voix : « ajoute du sépia ».
- **Retirer un seul effet** : un `-` devant. `/fx Ensuri -flou` enlève le flou et garde le reste. À la voix :
  « enlève juste le flou ».
- Tout se cumule : sur un joueur, l'effet imposé + celui de ses lunettes ; sur le stream, l'effet de la régie
  (`/fx diffusion`) + celui du plan en cours (intro, travelling).

## Sur la diffusion (le stream)

| Moyen | Exemple |
|---|---|
| Commande | `/fx diffusion noir_blanc` · `/fx diffusion judas 20` (20 secondes) · `/fx diffusion aucun` |
| Voix | « passe la diffusion en noir et blanc », « effet judas sur le stream », « enlève les effets » |

Tous les jeux en mode diffusion (F8) montrent l'effet. Il reste jusqu'au prochain ordre (ou la durée donnée), et un
jeu de diffusion qui se reconnecte le retrouve. La commande demande d'être organisateur ou d'avoir la permission
`craftmaster:camera` (comme les caméras).

## Sur la vue d'un joueur (imposé par l'organisateur)

- `/fx Ensuri flou 60` : Ensuri voit flou pendant 60 secondes (il est prévenu en haut de l'écran).
  `/fx tous sepia`, `/fx candidats pixel` (les candidats de la saison connectés), `/fx Ensuri aucun`.
- **Le joueur ne peut pas l'enlever** lui-même : c'est la façon de « forcer » un effet pour une épreuve.
- Voix : « mets Ensuri en flou », « mets tout le monde en sépia », « enlève les effets d'Ensuri ».
- `/fx aucun` (ou « enlève les effets ») : plus aucun effet nulle part, ni diffusion ni joueurs.
- `/fx etat` : qui a quoi ; `/fx liste` : tous les effets.

Réservé aux organisateurs.

## Les lunettes et masques à porter

Dans l'onglet CraftMaster, ou fabriqués à l'établi. Clic droit avec l'objet en main = on le met sur la
tête (comme une citrouille) ; on voit alors le monde à travers. Les autres le voient sur la tête du joueur, et la
diffusion aussi.

| Objet | Effet sur la vue |
|---|---|
| Lunettes floues | flou |
| Lunettes sépia | sépia |
| Lunettes noir et blanc | noir et blanc |
| Lunettes pixel | pixel |
| Lunettes judas | judas (fish-eye + masque rond) |
| Citrouille sculptée (celle du jeu) | la vue citrouille du jeu, rien à ajouter |
| Lunettes vintage | vieux film + vignette |
| Casque VHS | VHS |
| Lunettes thermiques | infrarouge |
| Lunettes cœur | rêve |
| Lunettes tourbillon | ondule |
| Jumelles frontales | jumelles (avec zoom) |

**Pour une épreuve**, l'organisateur peut les mettre lui-même sur la tête d'un candidat :
- `/fx porter Ensuri blurry_glasses` : Ensuri porte les lunettes floues (ce qu'il avait sur la tête va dans son
  inventaire) ;
- `/fx porter @a blurry_glasses verrouille` : **verrouillées**, impossible de les enlever (en survie / aventure ;
  un joueur en créatif peut toujours) ;
- `/fx retirer Ensuri` : les enlève (rendues sans le verrou).

Astuce : n'importe quel objet de tête peut porter un effet,
`/give @s carved_pumpkin[craftmaster:screen_fx="sepia+vignette"]`.

## Essayer chez soi

`/essaifx <effet[+effet]|aucun> [secondes]` (commande du jeu, rien n'est envoyé au serveur) : essaie un effet sur
**ta** vue (y compris en mode diffusion, pour régler OBS). `/essaifx` seul : ce qui est affiché et les images par
seconde.

## Pour les intros et travellings (développeurs)

`fr.lordfinn.craftmaster.client.fx.ScreenEffects.setBroadcastEffect("fisheye+masque_rond")` (ou `null` / `"aucun"`)
met un effet sur l'image des jeux en mode diffusion, avec fondu ; `setBroadcastEffect(id, secondesDeFondu)` règle le
fondu. C'est le même emplacement que `/fx diffusion` : le dernier ordre gagne.

## Performances

Chaque effet coûte une ou deux passes plein écran (quatre pour le flou et le rêve), très légères. Mesuré en jeu :
pas de baisse d'images par seconde visible, même avec six effets à la fois (fenêtre de diffusion en 1920 × 1080).
`macro` et `mise_au_point` sont les plus lourds (64 lectures par pixel pour le flou en disques) ; `stop_motion`
garde une image en mémoire ; `timelapse` ne coûte rien. (Ces cinq-là ne sont pas encore mesurés en jeu.)
