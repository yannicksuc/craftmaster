# Régie IA : faire évoluer la préparation en direct, à la voix

Pendant l'émission, LordFinn peut **tout faire faire** à un **agent Claude**, au micro ou par `/ia …` : il a la
main sur le show comme lui (toutes les commandes `/cm`, et celles du serveur avec son accord), il **voit tout**
(manches, arènes, caméras, intros, joueurs, journal, l'arbre des commandes, le wiki) et surtout il **voit ce que
filment les caméras** : il regarde son propre travail (cadrage, focus, effets, intro entière) et le corrige avant
de répondre dans le chat, images à l'appui.

> « Rends l'ambiance tension plus rouge et ajoute un plan serré sur la galerie dans la révélation. »
>
> **[Régie IA]** L'ambiance tension est plus rouge : contre en rouge franc, rampe en rouge framboise, face en
> rose-rouge… La révélation a déjà le plan serré sur la galerie (caméra 13) à 7 s, en flash. Tu veux que j'ajoute
> plutôt le travelling galerie juste après, vers 10 s ? **[détail] [Annuler]**

Réservé aux organisateurs (`craftmaster:admin` ou opérateur), comme la [régie vocale](RegieVocale.md).

## S'en servir
- **À la voix** : la touche `N` de la régie vocale, comme d'habitude. Ce qui *joue* (« ambiance tension », « caméra
  2 », « lance la conduite ouverture ») reste immédiat ; ce qui *change la préparation* (« rends… plus rouge »,
  « ajoute un plan… dans… », « crée une caméra… », « la conduite d'ouverture attend 3 s de plus », « le travelling
  galerie plus lent ») part à l'agent. Le panneau de la régie dit « Transmis à l'agent de régie ».
- **Au clavier** : `/ia <demande>` (commande du jeu).
- **Suite de conversation** : pendant 20 min l'agent se souvient de l'échange. Répondre à sa question (« oui »,
  « la deuxième », « vers 10 secondes ») lui revient directement.
- Il faut le **service de la régie vocale** lancé sur le PC (`tools\voice-regie\start.ps1`) : l'agent y tourne,
  avec l'abonnement Claude (Claude Code) ou une clé API.

- **L'icône** : à côté de celle de Simple Voice Chat (même taille, même place, réglable par ses options
  « position / taille de l'icône »), une étincelle : avec des barres de son tant qu'on parle (touche N),
  clignotante tant que l'agent travaille. Cachée avec les icônes de Voice Chat, avec F1, jamais à l'image de la
  diffusion.
- **Activer / couper** : bouton étincelle dans le menu de Voice Chat (touche V), en bas, après « volumes »
  (barré de rouge = coupé ; organisateurs seulement). Coupé : les demandes de préparation ne partent plus (le
  panneau dit « L'agent de régie est coupé »), `/ia` refuse, la régie vocale rapide continue. Retenu dans
  `config/craftmaster-client.json` (`regieAgent`).

Comptez **10 à 20 s** par demande (lecture, quelques commandes, réponse). Pendant ce temps on peut continuer
l'émission, la régie vocale rapide reste disponible.

## Ce que l'agent sait faire
| Domaine | Comment (commandes qu'il emploie) |
|---|---|
| Ambiances | `/cm ambiance modifier <nom> <cible> <ordre>` : couleur, intensité, cône, poursuite, visée, fondu, rideau — **sans jouer l'ambiance** |
| Timelines | il lit la séquence (JSON), ajoute / décale / retire des clés (lumière, rideau, tableaux, écran, **caméra**, commande), la réécrit entière |
| Caméras | `/cm cam placer <n> <x y z> vers <x y z>` (nouvelle ou déplacée, d'après les points du plateau), `/cm cam nom`, `zoom`, `role`, `mouvement` (fixe, respiration, travelling = « zoom en avant », panoramique) |
| Intros de manche | « l'intro de La pomme » = `/cm tv intro la-pomme …` : il lit l'intro plan par plan (`lire intro <manche>`), change la caméra, l'effet (fisheye, macro, `mise_au_point`, `mise_au_point_inverse` = « focus sur la fleur puis la salle »…), la mise au point réglable (`plan N focus de centre vers fond duree 2 flou 150`, voir [Effets](Effets.md)), la transition, la durée, l'ordre (« le plan final doit être la cam 1 »), ajoute des plans, `construire`, `auto`, `enchaine` — **sans la jouer** |
| Travellings | `/cm cam trajet <nom> duree / pause / courbe / zoom / suppr …` |
| Conduites, lancements | `/cm conduite ajouter / inserer / retirer`, `/cm conduite auto`, `/cm conduite manche` |
| Tout le reste | manches, épreuves, arènes, chronos, replays, saison, plateau, médias… et les commandes du serveur : ce qui se voit attend ton clic (voir Garde-fous) |

Il **ne lance rien à l'antenne** (ni ambiance, ni séquence, ni coupe) sauf si on le lui demande, et même alors tu
confirmes d'un clic.

## Ce qu'il voit
| Outil | Ce qu'il apprend |
|---|---|
| `lire` | `resume`, `manches`, `intro <manche>` (plans, effets, focus, transitions, musique), `effets`, `ambiance`, `sequence`, `conduite`, `cameras` (position, visée, champ, mouvement), `trajet`, `projecteurs`, `arenes` (référence, copies), `zone <salle>` (tout ce que contient la salle d'une épreuve, voir plus bas), `mecaniques <pack>` (le pack de données de la salle : règles des objets, butins, fonctions commentées), `musiques` (les musiques d'intro du thème : ambiance, univers, tempo, durée, moment de l'« appel » ; elle choisit celle qui colle à l'épreuve, maladresse par défaut), `joueurs` (monde, position, regard, mode), `blocs <mot>` (les blocs autour de toi : « fleur », « pot »…), `commandes [groupe]` (l'arbre complet de `/cm` avec ses arguments), `statut`, `journal`, `log [mot]` |
| `completer` | ce que Tab propose après un début de commande (arguments, infobulles) : il découvre une commande au lieu de la deviner |
| `wiki` | ces pages (liste, une page, ou les lignes qui contiennent un mot) |
| **`voir`** | **ce que filme la caméra**, voir ci-dessous |

## Comprendre une épreuve
« Comment on finit la pomme ? », « qu'est-ce qui manque pour que ce soit faisable ? » : l'agent lit la salle
(`lire zone pomme`) et son pack (`lire mecaniques pomme`), puis en déduit le **chemin de la solution** et les
**blocages**.

| `lire zone <salle>` donne | |
|---|---|
| panneaux | recto et verso, et s'ils sont cliquables |
| conteneurs | coffres, tonneaux, shulkers, étagères, pots décorés, feux de camp (allumés ou non), pupitres, cadres, supports d'armure, objets par terre ; chaque objet avec son nom, son lore et ses règles d'aventure (« peut casser », « se pose sur ») |
| mécanismes | boutons, leviers, cibles, plaques, portes et trappes (ouvertes ou fermées), pots de fleurs, établis, fourneaux, chaudrons, ruches… |
| personnages | villageois et leurs échanges (« 16 × Farine → 1 émeraude, 13 fois »), autres créatures comptées |
| la salle | la tâche, l'arrivée, le kit, et chaque sorte de bloc avec son nombre et sa boîte (feuilles, bûches, minerais) |

Les coordonnées sont relatives au coin de la salle (les `~x ~y ~z` du pack), regroupées par secteur de 12 blocs.

L'agent répond en étapes numérotées :
- ce qu'il faut avoir ou faire ;
- ce que ça débloque ;
- le compte des émeraudes.

Puis il donne les blocages (objet introuvable, échange impossible, risque de blocage), avec leurs coordonnées. Il ne
change rien à la salle sans qu'on le lui demande.

## Ses yeux : `voir`
Ton jeu dessine ce que filme une caméra **exactement comme le flux de l'émission** (F7) : effets, look de l'intro,
focus, et tes shaders s'ils sont allumés. L'image est prise hors écran, une image à la fois : ton écran ne bouge pas.
L'agent reçoit les images ; toi aussi, dans ton chat : **[Régie IA] 👁 Intro de « La pomme » (6 images) [aperçu]
[dossier] [▶ vidéo]**. **[aperçu]** les ouvre en jeu (◀ ▶, flèches ou molette), **[dossier]** ouvre le dossier
(`craftmaster-regie/vues/` dans l'instance), **[▶ vidéo]** la vidéo quand il y en a une (MP4, s'il y a ffmpeg sur
le PC). `/iavue` rouvre la dernière vue, `/iavue <n>` une autre.

| Il regarde | Exemple |
|---|---|
| une caméra (image fixe, ou à 0, 2, 4 s de son mouvement) ; il peut **essayer** un effet ou un focus sans rien changer | « cadre-moi la cam 4 de la pomme sur la fleur et montre-moi » |
| un travelling (6 images le long du trajet) | « montre-moi le travelling galerie » |
| une intro entière : une image par plan (planche contact numérotée), 3 par plan pour voir les focus bouger, ou une vidéo à 8 images/s | « refais l'intro de la pomme plus dynamique et vérifie » |
| un plan d'intro (3 images) | « le plan 3 de la pomme est net au bon endroit ? » |

Il juge en réalisateur (sujet dans le cadre et net, règle des tiers, rien de coupé au bord, pas de premier plan
qui bouche, rythme de l'intro), recadre (`cam placer … vers …`, `cam zoom`), regarde à nouveau, puis te dit ce qu'il
a vu. **Limite** : ton jeu ne dessine que ce qu'il a chargé autour de toi ; une caméra trop loin revient « hors de
portée » et l'agent te propose de t'approcher (par exemple `/cm arene tp pomme reference`, que tu confirmes).

## Garde-fous
- **Qui** : seul un organisateur. Le serveur revérifie à chaque appel ; les commandes s'exécutent **au nom de
  LordFinn**, avec ses droits et pas plus.
- **Quoi** :
  - **tout de suite** : le réglage du show, que personne ne voit avant qu'on le joue (caméras, travellings, éditeur
    d'intro, effets et focus d'un plan, ambiances, conduites, séquences, points du plateau, télés) et tout ce qui ne
    fait que lire ;
  - **après ton clic** : ce qui supprime, ce que les joueurs ou le stream verraient (jouer à l'antenne, lancer une
    manche ou une épreuve, TP, effets sur les joueurs ou la diffusion, projecteurs en direct…) et toute commande du
    serveur (`gamemode`, `tp`…). Le chat affiche « Régie IA veut faire : /… **[Confirmer] [Refuser]** », l'agent pose
    la question ; on clique, ou on répond « oui » au micro (une demande suivante : il ne peut pas se confirmer
    lui-même) ;
  - **jamais** : arrêter le serveur, opérateurs, bannissements, liste blanche, permissions, ses propres boutons.

  Une ligne ajoutée à une conduite ou à une timeline (qui s'exécutera plus tard) est vérifiée comme avant (régie,
  `attendre`, `noir`, `playsound`, `title`, `particle`).
- **Annuler** : **[Annuler]** au bout de la réponse, ou « annule ce que tu as fait » : la scénographie (ambiances,
  conduites, séquences, lancements auto), les caméras, les travellings et les intros des manches reviennent à
  l'état d'avant la demande.
- **Journal** : chaque demande et ses changements dans `/cm modo journal` (origine « ia »), le journal du serveur
  (« Régie IA (LordFinn) : /cm ambiance modifier … ») et `tools/voice-regie/logs/regie.log` ; **[détail]** (survol)
  liste ce qui a été fait.
- **Rien à l'image** : les messages ne vont que dans le chat de LordFinn, jamais dans un jeu en mode diffusion.
- **Jamais d'adresse** : toute adresse IP qui passerait dans une réponse est masquée (« ‹adresse masquée› »), côté
  serveur et côté service.
- Au plus 60 appels et 7 min par demande ; si le jeu se déconnecte, l'agent s'arrête.

## Comment ça marche
```
LordFinn (N)  → service local : Whisper → compréhension rapide → « change la préparation » = agent_request
                                                               ↓
                         agent Claude (dans le service, sur le PC)  ⇄  outils : lire, commande, completer, voir,
                                                               ↑                 wiki (local), ecrire_sequence, confirmer, annuler
jeu de LordFinn : interroge le service (5×/s), relaie chaque appel au serveur (paquet regie_agent_call),
                  renvoie la réponse (regie_agent_reply) ; à la fin, la réponse dans le chat
serveur : exécute en tant que LordFinn (sortie capturée), photo avant le 1er changement (annulation), journal
voir : serveur → liste des images à prendre (_rendu) → jeu de LordFinn : chaque image dessinée une image sur deux,
       hors écran (ThumbCapture + AgentVision), PNG + planche + MP4 → service : les images au modèle
```
- Code : `tools/voice-regie/regie/agent.py` (relais, boucle API ou `claude -p`), `agent_prompt.py` (consignes),
  `agent_mcp.py` (serveur MCP pour Claude Code), `src/main/.../regie/AgentBridge.java` (serveur),
  `src/client/.../regie/RegieAgent.java` (relais du jeu, chat, `/ia`), `AgentVision.java` + `AgentViewScreen.java`
  (les images, `/iavue`), `tools/voice-regie/regie/vision.py` (images vers le modèle, wiki).
- Essai en dev : un service à part (`REGIE_PORT=25791`) et `scripts\dev-client.ps1 -Second -RegieUrl http://127.0.0.1:25791`.
- Sans clé API, l'agent passe par Claude Code (`claude -p`, abonnement) avec un serveur MCP `craftmaster` lancé par
  le Python Windows (même dans WSL), qui rappelle le service ; la conversation suit par `--resume`.
- Icône et bouton : `client/regie/AgentHudIcon.java` (place lue dans les réglages de Voice Chat par réflexion :
  il n'a pas d'API pour son HUD), `AgentToggleButton` + `mixin/VoiceChatScreenMixin` (ajouté à son menu, appliqué
  seulement s'il est installé : `VoiceChatMixinPlugin`), sprites `textures/gui/sprites/icons/regie_agent_*.png`
  (`scripts/gen_regie_agent_icons.py`, palette et barre rouge de Voice Chat).
- Réglages : `REGIE_AGENT_MODEL` (défaut : celui de la régie, `claude-opus-5-5`), `REGIE_AGENT_EFFORT` (`medium`).

## Dépannage
| Message | Que faire |
|---|---|
| « Service de régie injoignable » | lancer `tools\voice-regie\start.ps1` |
| « Le jeu ne répond plus » | le jeu s'est déconnecté pendant la demande : redemander |
| « Refusé : … n'est jamais fait par la régie IA » | normal : arrêt du serveur, opérateurs, bannissements… restent à toi |
| Images « hors de portée » | la caméra est trop loin de toi : approche-toi (ou laisse l'agent te TP, tu confirmes) |
| Rien à annuler | la demande n'avait rien changé, ou elle est déjà annulée |
