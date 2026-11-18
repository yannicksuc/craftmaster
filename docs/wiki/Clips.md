# Clips et télés du studio

Enregistrer un clip de la vue du jeu, puis le passer sur une télé du studio. Les mêmes télés passent aussi des clips
Twitch (les tiens ou ceux postés par le chat) et le direct Twitch d'un candidat connecté.

Tout passe par **WaterMedia 3** (avec **WaterMedia Binaries**, qui apporte FFmpeg), livré par le modpack côté
client. Ce n'est **pas WaterFrames** : WaterFrames est un mod de télés construit sur WaterMedia, il s'arrête en
1.21.8 et n'est pas dans le pack. CraftMaster dessine ses propres écrans.

> **Minecraft 26.2 seulement.** WaterMedia n'existe pas en 26.3. En 26.3, les écrans gardent les images et les
> touches de clip disent pourquoi elles ne font rien.
>
> **Pas de Flashback dans le même jeu.** Le FFmpeg de Flashback remplace celui de WaterMedia, et ni les vidéos ni
> les clips ne marchent alors.

## Le clip de la vue actuelle (F9 / F10)
- **F9** démarre l'enregistrement, **F10** l'arrête. Les touches se changent dans Options > Commandes > CraftMaster.
  Le Stream Deck et la voix font la même chose (voir plus bas).
- **Ce qui est enregistré** : l'image du monde telle que ce jeu la montre, **sans l'interface** (ni HUD, ni chat,
  ni habillage de la diffusion), comme l'export vidéo de Flashback. En direct comme pendant un replay : le clip prend
  ce que montre le jeu à ce moment.
- **Le son n'est pas enregistré** pour l'instant.
- **À l'écran** : en mode diffusion, rien (les viewers ne voient aucun voyant). Hors diffusion, un petit point rouge
  et la durée dans le coin en haut à droite, et un message court au-dessus de la barre d'objets.
- **Fichier** : MP4 H.264 dans `craftmaster/clips/` du dossier du jeu. Encodé à part, sans ralentir l'image.
  Mesuré : 6 s en 854 × 480 à 30 i/s, 182 images, aucune sautée, 0,4 Mo.
- **Arrêt automatique** au bout de 3 minutes (`maxSeconds`).
- **Envoi au serveur** : à l'arrêt, le clip part tout seul vers le serveur, en morceaux, sans site web ni port à
  ouvrir. Le serveur le range dans `craftmaster/clips/`, avec un index (`index.json`).
  Le message « Clip « … » sur le serveur » dit quand c'est fait. Pendant un replay, le serveur ne laisse rien passer
  à part les commandes : l'envoi attend et réessaie toutes les 10 s.

Réglages dans `config/craftmaster-client.json`, section `clips` :

| Clé | Défaut | Rôle |
|---|---|---|
| `fps` | 30 | images par seconde (10 à 60) |
| `quality` | 40 | 10 (fichier léger) à 100 (meilleure image) ; 40 ≈ 9 Mbit/s en 1080p30 |
| `upload` | true | envoyer chaque clip au serveur |
| `maxSeconds` | 180 | arrêt automatique (0 = jamais) |

## Les télés
Une télé, c'est un écran du studio (`/cm media image`, [Plateau](Plateau.md)) auquel on donne un nom :
1. Pose l'écran : `/cm media image <adresse> [largeur] [hauteur]` en regardant un mur.
2. En le regardant, nomme-le : `/cm media tele nommer gauche`. Plusieurs écrans peuvent porter le même nom : ils montrent
   alors la même chose.
3. Ensuite, sans le regarder : `/cm media tele gauche …`.

Ce qu'une télé peut montrer :

| Commande | Ce qui passe |
|---|---|
| `/cm media tele gauche clip` | le dernier clip du serveur (`clip dernier`, `clip avant-dernier`, `clip 3` = le 3ᵉ de la liste) |
| `/cm media tele gauche lien <URL>` | un clip Twitch (`clips.twitch.tv/…` ou `twitch.tv/<chaîne>/clip/…`), une vidéo `.mp4`, une page Kick, Medal, Streamable… |
| `/cm media tele gauche direct <joueur>` | le direct Twitch de ce candidat (sa chaîne liée, il doit être connecté) ; ou directement un nom de chaîne |
| `/cm media tele gauche image <URL>` | une image |
| `/cm media tele gauche chat twitch` · `chat jeu` · `chat tout` | le mur de chat : les derniers messages du chat Twitch, du chat du jeu, ou des deux (`chat` seul = les deux) — voir [plus bas](#le-mur-de-chat) |
| `/cm media tele gauche off` | la mire CraftMaster |
| `/cm media tele tout …` | toutes les télés nommées |
| `/cm media tele liste` | les télés, leur nom et ce qu'elles montrent |

- **Synchronisation** : la vidéo démarre au même moment pour tout le monde. Un joueur qui arrive en cours de route la
  prend où elle en est (sauf un direct, toujours en temps réel). Les clips tournent en boucle.
- **Son** : il sort de la télé dans le monde, avec le volume « Jukebox ».
- **Charge réseau** : chaque jeu télécharge lui-même ce que montre la télé. Une télé qu'on ne voit plus depuis 4 s
  s'arrête (et son téléchargement aussi). L'image est limitée à 1280 × 720.
- **Clip du serveur** : chaque jeu le télécharge une fois depuis le serveur, puis le garde dans
  `craftmaster/clip-cache/`. Mesuré : un clip de 0,4 Mo arrive en moins d'une seconde chez un deuxième joueur.
- **Direct Twitch** : Twitch passe d'abord sa publicité aux spectateurs anonymes (« Commercial break in progress »),
  puis le direct. Une chaîne hors ligne affiche la mire avec « chaîne hors ligne ».
- **Licence** : la licence de WaterMedia est non commerciale (Defensive Public Source Non-Commercial). À vérifier
  pour un direct monétisé.

## Les clips sur le serveur
- `/cm media clip liste` : numéro, nom, durée, poids, auteur, direct ou replay. Clic sur une ligne : la commande
  `/cm media tele … clip <n>` est prête dans le chat.
- `/cm media clip renommer <n> <nom>`.
- `/cm media clip supprimer <n>`.

Le nom par défaut est « Clip 13/11 03h16 ».

## Les chaînes des candidats
- `/cm media chaine lier Ensuri ensuri_tv` (ou `twitch.tv/ensuri_tv`).
- `/cm media chaine delier Ensuri`.
- `/cm media chaine liste` : candidats connectés en vert.

Gardé dans `config/craftmaster/twitch.json`. Ensuite : `/cm media tele plateau direct Ensuri`. Un candidat connecté sans
chaîne liée est refusé, plutôt que de deviner une chaîne du même nom.

## Les liens de clips postés par le chat
Le jeu de diffusion lit déjà le chat Twitch (`twitch` dans `config/craftmaster-client.json`, voir
[Tâches](Taches.md)). Il retient aussi les **liens de clips Twitch** postés par les viewers : les 20 derniers, sans
doublon. **Rien ne passe tout seul** : c'est toi qui choisis.
- `/craftMasterTwitch clips` liste les liens (qui, il y a combien de temps). Un clic prépare
  `/cm media tele <tele> lien <lien>`.
- Le bouton Stream Deck `/tele?nom=gauche&chat` met le dernier lien du chat sur la télé « gauche ».
- `/craftMasterTwitch message <texte>` fait comme si un viewer avait posté ce texte, pour essayer sans Twitch.

## Le mur de chat
Une télé peut afficher les **derniers messages du chat**, lisibles de loin sur un grand écran du plateau :
- `/cm media tele <tele> chat twitch` : le chat Twitch du direct ;
- `/cm media tele <tele> chat jeu` : le chat des joueurs dans Minecraft ;
- `/cm media tele <tele> chat tout` (ou `chat` seul) : les deux, un petit trait violet (Twitch) ou vert (jeu) devant
  chaque message.

Sans nom de télé, sur l'écran regardé : `/cm media image chat:twitch` (ou `chat:jeu`, `chat:tout`). Un mur qui n'existe
pas encore se pose comme une image : `/cm media image chat:twitch 8 5` en regardant un mur. L'adresse `chat:…` marche
aussi dans les réglages de l'écran (clic droit).

À l'écran : le plus récent en bas, le pseudo dans sa couleur (la couleur Twitch du viewer, sinon une couleur fixe
tirée du pseudo), les messages coupés sur plusieurs lignes, 8 à 14 lignes selon la hauteur de l'écran ; les
anciens montent en douceur quand un message arrive.

D'où viennent les messages :
- **Twitch** : le jeu de diffusion lit déjà le chat Twitch (`twitch` dans `config/craftmaster-client.json`, voir
  [Tâches](Taches.md)) ; il envoie chaque message au serveur. Le serveur ne les garde **que d'un organisateur**
  (permission `craftmaster:image` ou admin) ; si plusieurs organisateurs lisent le chat, chaque message n'est
  gardé qu'une fois. Un message supprimé par un modérateur Twitch, ou les messages d'un viewer banni ou mis en
  sourdine, disparaissent aussi du mur.
- **Jeu** : les messages du chat Minecraft des joueurs.
- Ignorés : les commandes (`!tache`, `!so`…) et les messages vides. Les caractères de contrôle et les codes
  couleur `§` sont retirés, un message est coupé à 200 caractères.

Le serveur garde les **50 derniers messages** de chaque source (rien sur disque, vidés au redémarrage) : un
joueur qui arrive devant le mur voit l'historique récent. Seuls les joueurs qui voient un mur de chat reçoivent les
messages ; leur jeu ne fait que les dessiner.

## Stream Deck et voix
Stream Deck ([Saisie](Saisie.md#api-streamdeck)), sur le jeu de LordFinn :

| Adresse | Effet |
|---|---|
| `/clip/start` · `/clip/stop` · `/clip/toggle` | clip de la vue de ce jeu |
| `/clip/status` | `{recording, seconds, message}` |
| `/tele?nom=gauche&clip=dernier` | le dernier clip du serveur sur « gauche » (`clip=2`…) |
| `/tele?nom=gauche&lien=<URL>` · `&image=<URL>` | un lien, une image |
| `/tele?nom=gauche&direct=Ensuri` | le direct d'Ensuri |
| `/tele?nom=gauche&chat` | le dernier clip Twitch posté dans le chat |
| `/tele?nom=gauche&chat=twitch` · `chat=jeu` · `chat=tout` | le mur de chat |
| `/tele?nom=gauche&off` | la mire |

Voix ([Régie vocale](RegieVocale.md)) :
- « lance un clip », « coupe le clip » ;
- « mets le clip sur la télé de gauche » ;
- « le direct d'Ensuri sur la télé plateau » ;
- « éteins la télé de gauche ».

Le clip tourne sur le jeu qui l'a demandé : la régie web ne peut pas le lancer, sauf par le Stream Deck local.
Les ordres des télés du Stream Deck et de la voix sont des actions `tele.show`, comme ceux de la régie des
modérateurs. `/cm media tele` tapée à la main passe aussi par cette action : commande, voix, Stream Deck, régie
en jeu et site suivent un seul chemin, avec le même effet (droits et journal de la régie des modos).

Action `tele.show` : `{tele, clip | link | live | image | chat | off}`. Un seul champ parmi les six ; sans aucun,
c'est le dernier clip. `chat` vaut `twitch`, `jeu` ou `tout`. `off: true` remet la mire.

La réponse du Stream Deck redonne l'action envoyée. Une télé inconnue est signalée dans le chat de l'organisateur
(« ⚠ Aucune télé nommée … »).

## Pourquoi un enregistrement dans le jeu (et pas OBS)
LordFinn veut un clip « comme l'outil de Flashback », de la vue actuelle :
- **Flashback** lui-même est exclu : son FFmpeg casse WaterMedia.
- **OBS** (obs-websocket) aurait demandé d'activer son serveur websocket, enregistré la scène entière (caméras,
  habillage) et laissé le fichier sur le PC d'OBS.

L'enregistrement maison lit l'image du monde sur la carte graphique, dans trois tampons à tour de rôle, sans attendre
la carte. Il l'encode sur un fil à part avec le FFmpeg de WaterMedia (OpenH264), puis le serveur la distribue aux
jeux.

## Pas encore fait / pas vérifié
- **Son du clip** : pas enregistré.
- **Clip pendant un replay** : pas essayé en jeu (le replay est sur une autre branche). L'envoi au serveur est fait
  pour attendre la fin du replay.
- **Touches F9 / F10 au clavier** : pas essayées en vrai. Elles appellent les mêmes fonctions que le Stream Deck,
  essayé en jeu.
- **Voix** : ordres essayés dans les tests du service (mode hors ligne). Pas essayés au micro dans le jeu.
- **Son des télés** : non écouté (jeux de test en muet).
