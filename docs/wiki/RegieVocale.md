# Régie vocale

Piloter l'émission à la voix : **maintenir `N`**, dire l'ordre en français, relâcher. En 1 à 3 secondes le jeu est à
jour et un panneau en haut à gauche montre ce qui a été compris et fait. Réservé aux organisateurs
(`craftmaster:admin` ou opérateur) ; les autres reçoivent « La régie vocale est réservée aux organisateurs ».

## Démarrer (avant l'émission)
1. Clé Claude dans l'environnement Windows (une fois) :
   `[Environment]::SetEnvironmentVariable('ANTHROPIC_API_KEY', '<clé>', 'User')`, puis ouvrir un nouveau terminal.
2. Lancer le service sur le PC de LordFinn : `powershell -File tools\voice-regie\start.ps1` (laisser la fenêtre
   ouverte ; 10 à 20 s de chargement la première fois). `start.ps1 -Check` vérifie GPU et clé.
3. En jeu, la touche se change dans Options → Commandes → CraftMaster → « Régie vocale ».

Le service n'est pas lancé par le jeu : c'est un programme à part, sur le PC de celui qui parle (le serveur n'a
besoin de rien).

## Ce qu'on peut dire (exemples)
| Ordre | Effet |
|---|---|
| « trois points pour Juste pour le pont » | +3 à JustEdible dans la manche « Le Pont » |
| « deux points à Cycy et un pour Ensuri » | +2 Cyllu, +1 Ensuri (manche en cours) |
| « mets Arreaubaz à cinq sur la tour » / « efface la note d'Ensuri » | fixer / effacer un score |
| « manche suivante » / « lance le labyrinthe » / « fin de manche » / « stop le chrono » | déroulé |
| « ouvrez les rideaux » / « fermez le rideau du fond » | rideaux (scène par défaut) |
| « mettez à jour les scores » | publier (busts, tableaux, écrans des joueurs) |
| « lance la révélation » | révélation mise en scène |
| « annule » / « annule les deux dernières » | annuler |
| « renomme l'épreuve 2 en Tour infernale » | renommer une manche |
| « ajoute Cyllu comme joueur, surnom Cycy » | nouveau joueur avec surnom |
| « crée une manche Parkour sur scène avec un chrono de deux minutes » | nouvelle manche |
| « archive la partie 1 », « crée la saison 5 », « cache le classement » | saisons / parties |

## Ce qu'elle comprend
- **Pseudos approximatifs** : surnoms (définis par joueur), fautes de transcription (« l'or fine » → LordFinn,
  « arrobaz » → Arreaubaz, « silu » → Cyllu). Deux joueurs aussi proches l'un que l'autre : elle ne devine pas.
- **Manche** : par son nom, ses surnoms ou son numéro (« l'épreuve 2 »). Pas de manche dite → manche en cours.
- **Règle d'inactivité** : si aucune note n'a été donnée depuis 4 min (réglable) dans la manche en cours et qu'une
  note arrive sans manche, c'est la manche suivante : elle est **lancée d'abord** (titre, TP…), puis notée.
- **Doute réel** (joueur inconnu, ordre ambigu, hors sujet) : **rien n'est fait**, le panneau dit pourquoi
  (bordure dorée). Rien de destructif (supprimer, archiver) sans demande claire.

## Le panneau
- « ● Régie à l'écoute » (point rouge clignotant, durée, niveau du micro) tant que la touche est tenue ;
- « ⏳ Compréhension… » puis « ⏳ Exécution… » ;
- pendant 9 s : « « ce qui a été entendu » », la réponse, chaque action faite (vert) ou refusée (✗ rouge) et
  « Dis « annule » pour annuler ». Bordure verte = fait, dorée = rien à faire, rouge = erreur.
Seul celui qui parle le voit (mais il apparaît dans son stream). Le détail est aussi dans `logs/latest.log`
du jeu, dans le journal du serveur (« Voice control: LordFinn said … ») et dans `tools/voice-regie/logs/regie.log`.

## Annuler
« annule » défait la dernière commande vocale entière (toutes ses actions d'un coup), « annule les trois
dernières » remonte plus loin (journal de 50 actions, partagé avec les commandes).

## Dépannage
| Message | Que faire |
|---|---|
| Service de régie injoignable | lancer `tools\voice-regie\start.ps1` (port 25790 libre ?) |
| Le service de régie n'a pas de clé ANTHROPIC_API_KEY | définir la variable utilisateur, **rouvrir** le terminal, relancer `start.ps1` |
| Micro indisponible | micro branché / autorisé pour les applications de bureau (Paramètres Windows → Confidentialité → Microphone) ; Java prend le micro par défaut de Windows |
| Trop court | maintenir la touche pendant toute la phrase (minimum 0,3 s) |
| Mauvais nom compris | ajouter un surnom au joueur / à la manche (« ajoute le surnom Juste à JustEdible ») |
| Lent (> 3 s) | `start.ps1 -Check` : sans GPU Whisper passe sur le processeur (2 à 6 s) ; `REGIE_CLAUDE_FAST=1` accélère Claude |
| Sans internet | `REGIE_UNDERSTANDING=offline` : seulement « N points pour X [sur manche] », « X prend N points », rideaux, manche suivante, publier, révélation, annule |

Détails techniques : [tools/voice-regie/README.md](../../tools/voice-regie/README.md).
