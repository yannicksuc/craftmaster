# Régie des modérateurs — proposition (à valider par LordFinn, rien codé)

But : des modos pilotent l'émission comme le Stream Deck de LordFinn (caméras, manches, points, télés, replays),
**dans le jeu** (connectés au serveur) et **depuis un site web**, sans recoder chaque action une 3e et 4e fois et
sans jamais ouvrir l'API locale du Stream Deck (127.0.0.1) sur Internet.

À faire après la fusion des branches clips/télés (`claude/friendly-keller-d41f2f`) et replays
(`claude/happy-gauss-795d17`).

## 1. Ce qui existe déjà (lu dans le code)

- **Une couche d'actions côté serveur existe déjà** : `show.Actions` + `show.ActionExecutor` (schéma
  `emission.md` §4). La voix (`RegieServer`), les commandes, le gong, les tâches et la plupart des routes du
  Stream Deck y passent : le Stream Deck envoie `TrialNetwork.RunActions` (liste JSON : `score.add`,
  `manche.start`, `manche.end`, `timer.stop`, `scores.publish`, `reveal.start`, `camera.cut`, `camera.auto`…).
  Toute la liste est vérifiée, appliquée d'un coup et annulable (`undo`).
- **Permissions** : `craftmaster:admin` (ou op 2), `craftmaster:camera`, `craftmaster:image`. Mais
  `RunActions` et `ActionExecutor.execute(ServerPlayer…)` ne connaissent que « admin » : quelqu'un qui n'a que
  `craftmaster:camera` peut couper par commande, pas par paquet.
- **Ce qui ne passe pas par la couche** : les tablettes (`/craftMasterTablet` envoyée en commande), l'écran de
  saisie, le classement, les saisons (UI locale du client de LordFinn : à laisser de côté pour les modos).
- **Replays (branche happy-gauss)** : la lecture tourne sur le client qui regarde (lecteur de Server Replay ;
  pendant la lecture le vrai serveur ne lui envoie plus rien, seules les commandes passent). Les ordres
  (`replay.control`) sont exécutés **sur ce client** (`ReplayControl.run`), qui envoie `/replay view cm …`. Le
  serveur peut quand même lui parler par l'envoi direct du lecteur (déjà utilisé pour l'état de lecture 5×/s).
- **Clips et télés (branche friendly-keller)** : pas encore codés.
- **lordfinn.fr** : front Vue + API Node sur le VPS, **connexion Twitch déjà en place** (Login, LinkTwitch).

## 2. Une seule couche d'actions pour quatre entrées

```
 voix (service local) ─┐
 Stream Deck (127.0.0.1)┤  client de LordFinn ── RunActions ──┐
 régie en jeu (écran)  ─┘  client du modo     ── RunActions ──┤
                                                             ├─▶ ActionExecutor.execute(Acteur, actions)
 site lordfinn.fr ── API Node ── WebSocket sortant du serveur ┘        │ permission par action
                                                                      │ journal « qui a fait quoi »
                                                                      ▼ effets (caméras, télés, replay…)
```

Changements dans la couche (petits, tout le reste en profite) :
1. **Acteur** au lieu de « admin » : `Acteur(nom, origine: voix|deck|jeu|site, droits)`. Construit depuis un
   `ServerPlayer` (permissions LuckPerms/op) ou depuis un modo du site (liste tenue par le serveur, §4).
2. **Une permission par famille d'actions**, vérifiée par l'exécuteur, plus par l'appelant :
   `camera.*`, `broadcast.*`, `fx` → `craftmaster:camera` · `tv.*` → `craftmaster:tv` (ou `image`) ·
   `replay.*` → `craftmaster:replay` · `score.*`, `manche.*`, `timer.*`, tablettes → `craftmaster:jeu` ·
   publication, révélation, saisons/parties/joueurs, suppression → `craftmaster:admin` seulement.
   Nouveau groupe pratique : `craftmaster:modo` = camera + tv + replay (+ jeu si LordFinn le veut, §5).
3. **Nouvelles actions** pour ce qui n'y est pas encore :
   - `tablet.start|pause|toggle|reset|restart {group?}` (remplace la commande envoyée par le Stream Deck) ;
   - `tv.show {tv, clip|url|twitch|live: pseudo|image}`, `tv.clear {tv}` — à écrire **directement en actions**
     dans la branche clips (je le signale à cette session), pas en commandes à part ;
   - `replay.control {do, …}` pour un modo : le joueur qui regarde ne peut envoyer que des commandes, mais un
     modo, lui, est sur le vrai serveur (ou passe par le site). Saut, vitesse, pause et changement de fichier
     sont **déjà exécutés côté serveur** dans la branche replays (`ReplayViewing` → `ServerReplayBridge.jumpTo /
     setSpeed / setPaused` sur l'objet du lecteur ; le client ne fait que déclencher `/replay view cm …`). L'action
     d'un modo appelle ces mêmes fonctions sur le lecteur du jeu de diffusion. Seule la réalisation du replay
     (Direct / Auto / Manuel, caméra 1–9, vue d'un joueur) vit sur le client qui regarde : le serveur la lui envoie
     par l'envoi direct du lecteur (le chemin qui porte déjà l'état de lecture 5×/s), et ce client exécute ensuite
     `ReplayControl.run` comme pour le Stream Deck. **La voix et le Stream Deck de LordFinn ne changent pas**
     (ils tournent sur le jeu qui regarde et passent déjà).
4. **Journal** : chaque liste exécutée est notée (heure, acteur, origine, lignes). LordFinn le voit dans la
   régie (onglet Journal) et peut annuler la dernière action d'un modo.
5. **Garde-fous** : pour un modo, `scores.publish` et `reveal.start` demandent deux appuis (comme le Stream
   Deck) ; limite de débit par modo (ex. 5 listes/s) ; `/cm modo pause` coupe d'un coup toutes les régies modo.

Rien de tout ça ne touche l'écran de saisie des scores ni la tablette du minuteur (la tablette garde son code ;
l'action `tablet.*` appelle ce qu'appelle déjà la commande).

## 3. Régie en jeu

- Ouverte par une touche (proposition : **`;`**, libre d'après la liste des touches prises) ou `/regie`, seulement
  pour qui a au moins une des permissions ci-dessus. **Jamais affichée sur un jeu en mode diffusion (F8)** : ce
  sont les écrans des modos, pas l'image des viewers. Aucun message dans le chat général ; le compte rendu va au
  modo seul (barre d'action), le journal à LordFinn.
- Un écran en grille façon Stream Deck, onglets visibles seulement si on a le droit :

```
┌ Caméras ┬ Manche ┬ Points ┬ Télés ┬ Replay ┬ Journal ┐
│ [1 Large] [2 Plateau] [3 Trône] [4 …]   ● direct : 2 │
│ [Auto ✓]  [Suivre ▾ Ensuri]  [Vue de ▾]  [Mosaïque]  │
│ Transition : [Coupe] [Fondu] [Enchaîné]              │
└──────────────────────────────────────────────────────┘
```
  - Caméras : boutons des caméras (noms et rôles du serveur), la caméra à l'antenne surlignée, auto on/off.
  - Manche : manche en cours + chrono, suivante / lancer telle manche / fin / stop chrono / tablettes.
  - Points : un bouton par candidat, +1 / −1 regroupés comme au Stream Deck (0,8 s → « +3 » annoncé).
  - Télés : choisir une télé (liste du serveur) puis clip (liste des clips enregistrés), lien Twitch collé,
    direct d'un candidat, image, vider.
  - Replay : liste des enregistrements, lecture/pause, ±10 s, vitesse, repère suivant/précédent, vue.
- État tenu à jour par le serveur (un paquet `regie_state` envoyé aux régies ouvertes quand il change).
- Code : un `Screen` client qui n'envoie que des `RunActions` ; aucune logique en double.

## 4. Site web — recommandation

**Héberger sur lordfinn.fr (page `/craftmaster/regie`), le serveur Minecraft se connecte au site, jamais
l'inverse.**

```
navigateur du modo ──HTTPS──▶ API lordfinn.fr ◀──WebSocket sortant (wss)── serveur CraftMaster
   (connexion Twitch)          relais seulement          (exécute, revérifie le modo, renvoie l'état)
```
- Le serveur Minecraft ouvre lui-même une connexion `wss://lordfinn.fr/…` (comme un client) : **aucun port à
  ouvrir**, rien de l'API locale du Stream Deck n'est touché (elle reste sur 127.0.0.1 dans le client de
  LordFinn, inchangée).
- Lien serveur ↔ site : un secret créé par `/cm site lier` (affiché une fois à LordFinn, à coller dans le `.env`
  de l'API). Sans lien, rien ne passe ; `/cm site off` coupe.
- **Authentification des modos : Twitch**, déjà en place sur le site. Qui est modo est décidé **par le serveur
  Minecraft**, pas par le site : liste dans la config du mod (`/cm modo ajouter <pseudo Twitch> [rôle]`), envoyée
  au site pour l'affichage, et **revérifiée par le serveur à chaque ordre**. Le site ne peut donc rien faire
  qu'un modo de la liste n'aurait pas le droit de faire, même s'il était compromis.
  (Variante possible : prendre automatiquement les modérateurs de la chaîne Twitch, mais un modo du chat n'est
  pas forcément un modo de l'émission ; je conseille la liste.)
- La page : mêmes onglets que la régie en jeu, état en direct (caméra à l'antenne, manche, chrono, scores du
  direct, télés, replay), journal. Faite pour téléphone et tablette.
- Côté site : une route WebSocket + quelques routes, copiées à la main sur le VPS selon la procédure habituelle
  (sauvegarde, `node --check`, `forever restart`).

**Écarté : un serveur web dans le mod côté serveur.** Plus simple à brancher, mais il faut ouvrir un port sur
la machine du serveur Minecraft, gérer HTTPS (sinon jetons en clair), et l'exposer sur Internet. Les jetons
émis par `/cm` (lien à usage unique donné au modo en jeu) restent possibles en secours si un modo n'a pas
Twitch, sur le même relais.

## 5. Questions pour LordFinn

1. Site : lordfinn.fr avec relais (recommandé) ou serveur web dans le mod ?
2. Connexion des modos : Twitch + liste tenue dans le mod (recommandé), modos Twitch de la chaîne, ou liens
   à jeton donnés par `/cm` ?
3. Droits des modos : tout ce que fait le Stream Deck (points et manches compris), ou seulement caméras, télés
   et replays (points et manches restent à LordFinn) ?
4. Où tourne le serveur Minecraft de l'émission pendant le direct (ton PC, un hébergeur) ? Ça ne change pas la
   recommandation (connexion sortante), mais ça décide où mettre le secret.

## 6. Étapes une fois validé

1. Couche : Acteur, permission par famille, journal, `tablet.*`, `replay.control` serveur ; tests unitaires
   (droits refusés / acceptés par famille) ; Stream Deck et voix inchangés pour LordFinn.
2. Régie en jeu (écran + `regie_state`), vérifiée sur 26.2 avec un joueur modo et un jeu de diffusion.
3. Relais : client WebSocket dans le mod (java.net.http, sans dépendance) + routes API + page du site.
4. Wiki : `docs/wiki/RegieModos.md`, permissions dans `Commandes.md`.
