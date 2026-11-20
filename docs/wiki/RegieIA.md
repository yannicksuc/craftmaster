# Régie IA : faire évoluer la préparation en direct, à la voix

Pendant l'émission, LordFinn peut **changer ce qui est enregistré** — ambiances, timelines (séquences), conduites,
caméras, travellings, lancements automatiques — en le disant au micro. Sa demande part à un **agent Claude** qui
lit l'état de la régie, fait les changements avec les commandes du mod, et lui **répond dans son chat**.

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
| Intros de manche | « l'intro de La pomme » = `/cm tv intro la-pomme …` : il lit l'intro plan par plan (`lire intro <manche>`), change la caméra, l'effet (fisheye, macro, `mise_au_point`, `mise_au_point_inverse` = « focus sur la fleur puis la salle »…), la transition, la durée, l'ordre (« le plan final doit être la cam 1 »), ajoute des plans, `construire`, `auto`, `enchaine` — **sans la jouer** |
| Travellings | `/cm cam trajet <nom> duree / pause / courbe / zoom / suppr …` |
| Conduites, lancements | `/cm conduite ajouter / inserer / retirer`, `/cm conduite auto`, `/cm conduite manche` |

Il **ne lance rien à l'antenne** (ni ambiance, ni séquence, ni coupe) sauf si on le lui demande.

## Garde-fous
- **Qui** : seul un organisateur. Le serveur revérifie à chaque appel ; les commandes s'exécutent **au nom de
  LordFinn**, avec ses droits et pas plus.
- **Quoi** : seulement les commandes de la régie (`cam`, `tv`, `sequence`, `conduite`, `ambiance`, `cfsp`,
  `plateau`, `cmtele`, `cfpt`, `fx`). Une ligne ajoutée à une conduite ou à une timeline (qui s'exécutera plus tard)
  est vérifiée de même (plus `attendre`, `noir`, `playsound`, `title`, `particle`). Le reste est refusé.
- **Supprimer** une ambiance, une séquence, une conduite, une caméra ou un travelling attend une **confirmation** :
  le chat affiche « Régie IA veut : /cm ambiance supprimer pause **[Confirmer] [Refuser]** », l'agent pose la question ;
  on clique, ou on répond « oui » au micro (une demande suivante : l'agent ne peut pas se confirmer lui-même).
- **Annuler** : **[Annuler]** au bout de la réponse, ou « annule ce que tu as fait » : la scénographie (ambiances,
  conduites, séquences, lancements auto), les caméras et les travellings reviennent à l'état d'avant la demande.
- **Journal** : chaque demande et ses changements dans `/cm modo journal` (origine « ia »), le journal du serveur
  (« Régie IA (LordFinn) : /cm ambiance modifier … ») et `tools/voice-regie/logs/regie.log` ; **[détail]** (survol)
  liste ce qui a été fait.
- **Rien à l'image** : les messages ne vont que dans le chat de LordFinn, jamais dans un jeu en mode diffusion.
- **Jamais d'adresse** : toute adresse IP qui passerait dans une réponse est masquée (« ‹adresse masquée› »), côté
  serveur et côté service.
- Au plus 40 appels et 4 min par demande ; si le jeu se déconnecte, l'agent s'arrête.

## Comment ça marche
```
LordFinn (N)  → service local : Whisper → compréhension rapide → « change la préparation » = agent_request
                                                               ↓
                         agent Claude (dans le service, sur le PC)  ⇄  outils : lire, commande, ecrire_sequence,
                                                               ↑                 confirmer, annuler
jeu de LordFinn : interroge le service (5×/s), relaie chaque appel au serveur (paquet regie_agent_call),
                  renvoie la réponse (regie_agent_reply) ; à la fin, la réponse dans le chat
serveur : exécute en tant que LordFinn (sortie capturée), photo avant le 1er changement (annulation), journal
```
- Code : `tools/voice-regie/regie/agent.py` (relais, boucle API ou `claude -p`), `agent_prompt.py` (consignes),
  `agent_mcp.py` (serveur MCP pour Claude Code), `src/main/.../regie/AgentBridge.java` (serveur),
  `src/client/.../regie/RegieAgent.java` (relais du jeu, chat, `/ia`).
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
| « Refusé : … n'est pas une commande de la régie » | normal : l'agent ne touche qu'à la régie |
| Rien à annuler | la demande n'avait rien changé, ou elle est déjà annulée |
