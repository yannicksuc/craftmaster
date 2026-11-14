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

Comptez **10 à 20 s** par demande (lecture, quelques commandes, réponse). Pendant ce temps on peut continuer
l'émission, la régie vocale rapide reste disponible.

## Ce que l'agent sait faire
| Domaine | Comment (commandes qu'il emploie) |
|---|---|
| Ambiances | `/ambiance modifier <nom> <cible> <ordre>` : couleur, intensité, cône, poursuite, visée, fondu, rideau — **sans jouer l'ambiance** |
| Timelines | il lit la séquence (JSON), ajoute / décale / retire des clés (lumière, rideau, tableaux, écran, **caméra**, commande), la réécrit entière |
| Caméras | `/cam placer <n> <x y z> vers <x y z>` (nouvelle ou déplacée, d'après les points du plateau), `/cam name`, `zoom`, `role`, `motion` |
| Travellings | `/cam trajet <nom> duree / pause / courbe / zoom / suppr …` |
| Conduites, lancements | `/conduite ajouter / inserer / retirer`, `/conduite auto`, `/conduite manche` |

Il **ne lance rien à l'antenne** (ni ambiance, ni séquence, ni coupe) sauf si on le lui demande.

## Garde-fous
- **Qui** : seul un organisateur. Le serveur revérifie à chaque appel ; les commandes s'exécutent **au nom de
  LordFinn**, avec ses droits et pas plus.
- **Quoi** : seulement les commandes de la régie (`cam`, `tv`, `sequence`, `conduite`, `ambiance`, `cfsp`,
  `plateau`, `cmtele`, `cfpt`, `fx`). Une ligne ajoutée à une conduite ou à une timeline (qui s'exécutera plus tard)
  est vérifiée de même (plus `attendre`, `noir`, `playsound`, `title`, `particle`). Le reste est refusé.
- **Supprimer** une ambiance, une séquence, une conduite, une caméra ou un travelling attend une **confirmation** :
  le chat affiche « Régie IA veut : /ambiance supprimer pause **[Confirmer] [Refuser]** », l'agent pose la question ;
  on clique, ou on répond « oui » au micro (une demande suivante : l'agent ne peut pas se confirmer lui-même).
- **Annuler** : **[Annuler]** au bout de la réponse, ou « annule ce que tu as fait » : la scénographie (ambiances,
  conduites, séquences, lancements auto), les caméras et les travellings reviennent à l'état d'avant la demande.
- **Journal** : chaque demande et ses changements dans `/modo journal` (origine « ia »), le journal du serveur
  (« Régie IA (LordFinn) : /ambiance modifier … ») et `tools/voice-regie/logs/regie.log` ; **[détail]** (survol)
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
- Réglages : `REGIE_AGENT_MODEL` (défaut : celui de la régie, `claude-opus-5-5`), `REGIE_AGENT_EFFORT` (`medium`).

## Dépannage
| Message | Que faire |
|---|---|
| « Service de régie injoignable » | lancer `tools\voice-regie\start.ps1` |
| « Le jeu ne répond plus » | le jeu s'est déconnecté pendant la demande : redemander |
| « Refusé : … n'est pas une commande de la régie » | normal : l'agent ne touche qu'à la régie |
| Rien à annuler | la demande n'avait rien changé, ou elle est déjà annulée |
