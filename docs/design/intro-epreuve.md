# Intro d'épreuve : ce que fait Taskmaster, ce qu'on en fait

Ce document part des **intros d'épreuve** de Taskmaster, à ne pas confondre avec le générique de l'émission. Ce
sont les quelques secondes qui ouvrent chaque tâche avant que le candidat la lise. Il sert de référence pour le
mode automatique et pour l'éditeur d'intros. Les observations viennent de quatre intros regardées image par image,
une image toutes les 0,7 à 0,9 s, sans le son :
- « Write the best lyrics » (début) ;
- « Discover the name of the person in the lab » (début) ;
- S4E8 à 7:30 (une cloche de service en argent) ;
- S4E8 à 21:43 (des objets alignés sur un terrain de foot) ;
- S4E8 à 29:04 (une baignoire devant la caravane).

## Ce qu'on voit

**Durée et rythme.** L'intro dure 8 à 10 s. Elle compte 5 à 8 plans, de 0,8 à 2 s chacun, coupés sur la musique.
On y entend la petite musique propre aux tâches, pas le thème complet.

**Le sujet des plans : des objets.** Les plans montrent des objets du lieu et des objets de la tâche, presque jamais
des gens :
- des instruments et des partitions pour la tâche de paroles ;
- une montre, des fioles et un microscope pour le laboratoire ;
- un sablier et une cloche de service ;
- un drapeau de corner, un canard, une balle et une noix de coco sur le terrain ;
- une horloge sur un pignon, un canard dans une baignoire.

Le décor parle de la tâche : on devine le thème avant de lire.

**Les cadrages.**
- **Très gros plan** à faible profondeur de champ : les cordes d'une guitare, le cadran d'une montre.
- **Contre-plongée à ras du sol** : objet au premier plan, ciel ou maison derrière. Exemples : une figurine de
  jardin devant la cabine rouge, le drapeau de corner.
- **Premier plan net, fond flou, puis l'inverse.** Le saxophone net devant la cage floue, puis la cage nette. Le
  visage d'Alex net devant Greg flou, puis Greg net. C'est la mise au point qui raconte le plan.
- **Vue à travers quelque chose** : un oculaire de microscope (masque rond), du verre, une loupe.
- **Plongée verticale** sur l'objet : la baignoire vue d'en haut, le canard dedans.
- **Fisheye** : une contre-plongée déformée sur la table ou le jardin.

**Les traitements.**
- Image chaude, désaturée, vert-jaune.
- Vignettage fort, grain de film.
- Parfois un **kaléidoscope / miroir** comme transition entre le plateau et l'intro.
- Des **timelapses** : nuages qui filent au-dessus d'une façade.
- Du **stop motion** : une main gantée qui avance image par image.
- Des **bascules de mise au point**.

**Les apparitions de Greg.** Un plan sombre de Greg (le Taskmaster), parfois derrière un objet net, revient une
fois dans l'intro. C'est le « maître » qui regarde.

**Le dernier plan, qui est la règle à garder.** Il montre **la tâche dans son lieu** :
- soit le lieu où le candidat arrive, avec l'objet de la tâche dans le même cadre (la baignoire devant la
  caravane, la cloche sur la table du salon) ;
- soit, juste avant, un gros plan de l'objet de la tâche, puis le lieu.

Ce dernier plan prépare le spectateur à voir le candidat entrer dans ce cadre.

**Le texte de la tâche.** On entend souvent la voix qui lit la consigne, sous-titrée, pendant les derniers plans.

## Traduction dans CraftMaster

| Taskmaster | CraftMaster |
|---|---|
| plans d'objets du lieu | caméras de la manche qui visent des blocs ou des entités précis (un « sujet » par caméra) |
| contre-plongée à ras du sol, objet au premier plan | caméra posée à 0,2-0,5 bloc du sol, visant vers le haut, un bloc ou une entité entre elle et le fond |
| premier plan net, fond flou, puis l'inverse | effet « bascule de mise au point » : flou selon la profondeur, qui glisse du premier plan au fond pendant le plan |
| très gros plan, faible profondeur | champ de vision réduit (`/cam zoom 20`) et flou du fond |
| oculaire, loupe | effet `masque_rond` (existe) |
| fisheye | effet `fisheye` (existe) |
| image chaude désaturée, vignette, grain | un « look Taskmaster » : `sepia` léger + `vignette` + grain, appliqué à toute l'intro |
| kaléidoscope en transition | nouvelle transition « miroir » |
| timelapse | le temps du monde accéléré pour ce plan seulement (nuages, soleil, ombres), sur la diffusion |
| stop motion | effet « saccadé » : l'image ne se rafraîchit que 6 à 8 fois par seconde |
| plan sombre du Taskmaster | caméra « trône » du studio (rôle `trone`), assombrie |
| dernier plan : la tâche dans son lieu | caméra « lieu de la tâche » : le point de TP du joueur et l'enveloppe posée dans le même cadre |
| musique propre aux tâches | une musique d'intro d'épreuve, plus courte que le thème (à fournir, ou un extrait du thème) |
| voix et sous-titre de la consigne | le texte de la tâche tapé à la machine en bas de l'image, si la tâche est révélée au lancement |

### La tâche est un objet posé

La tâche est **l'enveloppe posée** sur une table, dans le lieu. Le dernier plan la montre. Le candidat arrive
(téléporté pendant le noir), la voit et la lit :
- **clic droit** : elle se lit sur place, comme en main ;
- **la casser** : on la récupère.

C'est déjà en place (docs/wiki/Taches.md). Pour la mise en scène automatique, l'enveloppe posée la plus proche du
point de TP est « l'objet de la tâche ».

## Mode automatique (proposition)

À partir de ce que l'épreuve contient déjà, sans réglage :
1. **Sujets** : les caméras de la manche, les blocs et entités remarquables de sa zone (enveloppes posées, objets
   d'épreuve, tablettes chrono, horloges, animaux, blocs rares) et les mots de la tâche. Une tâche qui parle de
   moutons fait chercher les moutons de la zone, ou en faire apparaître un qui traverse.
2. **Plans** : 5 à 7 plans d'une mesure (environ 2 s), dans cet ordre :
   - un plan d'ambiance (contre-plongée ou timelapse du ciel) ;
   - 2 à 4 plans d'objets (gros plans, bascule de mise au point, à travers un masque) ;
   - un plan sombre du trône ;
   - le gros plan de l'objet de la tâche ;
   - le plan « lieu de la tâche » (point de TP et enveloppe dans le cadre).
3. **Caméras générées** quand l'épreuve n'en a pas assez : placées automatiquement autour des sujets (à ras du sol,
   en plongée, à 2-4 blocs), sans gêner les joueurs (elles ne sont que des points de vue de la diffusion).
4. **Variété** : jamais deux fois le même cadrage de suite, un effet différent par plan, une contre-plongée et un
   plan fixe au moins.

L'organisateur peut ensuite tout retoucher dans l'éditeur : remplacer un plan, changer un effet, fixer une caméra
générée.

## Ce qui existe déjà / ce qui manque

Existe :
- intro de manche (plans, durées, transitions, effets) ;
- coupe sur les temps de la musique ;
- lancement enchaîné (noir, TP, titre tapé, révélation) ;
- effets `sepia`, `noir_blanc`, `vignette`, `fisheye`, `masque_rond`, `pixel` ;
- zoom par caméra, rôles de caméra (`trone`, `large`) ;
- enveloppe posée, lisible sur place.

Manque, dans l'ordre proposé :
1. le « look » d'intro (grain + teinte chaude) et l'effet bascule de mise au point ;
2. le plan « lieu de la tâche » et la recherche de l'enveloppe posée ;
3. le mode automatique (choix des sujets, caméras générées, ordre des plans) ;
4. timelapse, stop motion, transition miroir ;
5. l'éditeur (frise sur la musique, aperçu, remplacer un plan) ;
6. une musique d'intro d'épreuve courte.
