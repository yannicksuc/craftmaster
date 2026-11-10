# Régie vocale — service local

Service HTTP local (127.0.0.1:25790) utilisé par le mod CraftMaster : il reçoit l'ordre parlé (WAV 16 kHz mono) et
le contexte de la partie, le transcrit avec **faster-whisper**, le comprend avec **Claude** (un outil par action du
schéma de `docs/design/emission.md` §4) et renvoie `{transcript, actions, reply}`. Guide organisateur :
[docs/wiki/RegieVocale.md](../../docs/wiki/RegieVocale.md).

## Démarrer
```powershell
powershell -File tools\voice-regie\start.ps1          # Ctrl+C pour arrêter
powershell -File tools\voice-regie\start.ps1 -Check   # GPU, bibliothèques CUDA, présence de la clé
```
La clé Claude est lue **uniquement** dans la variable d'environnement `ANTHROPIC_API_KEY` (par le SDK `anthropic`) ;
le service ne la lit, ne l'affiche ni ne l'enregistre jamais. Variable utilisateur Windows :
`[Environment]::SetEnvironmentVariable('ANTHROPIC_API_KEY', '<clé>', 'User')` puis **nouveau** terminal.
**Sans clé, rien à faire** : le service passe par Claude Code (`claude -p`) et ton abonnement Claude, comme finnTools
(`regie/subscription.py`). Il suffit que Claude Code soit installé et connecté (sous Windows ou dans WSL : le service
le trouve tout seul). Un peu plus lent : 4 à 6 s par ordre au lieu de 1 à 2 s avec la clé.

Le premier lancement charge Whisper (~10 à 20 s). `GET /health` dit si le modèle est prêt.

## Réglages (variables d'environnement, toutes facultatives)
| Variable | Défaut | Rôle |
|---|---|---|
| `REGIE_PORT` | 25790 | port (toujours sur 127.0.0.1 ; le mod lit `-Dcraftmaster.regie.url` s'il change) |
| `REGIE_UNDERSTANDING` | `claude` | `claude` = clé API si présente, sinon abonnement ; `abonnement` = toujours Claude Code ; `offline` = quelques phrases fixes sans Claude (tests, secours sans internet) |
| `REGIE_WSL_DISTRO` | `Ubuntu` | distribution WSL où Claude Code est installé |
| `REGIE_CLAUDE_CMD` | auto | commande Claude Code du mode abonnement (ex. `wsl.exe -e /home/moi/.local/bin/claude`) |
| `REGIE_CLAUDE_MODEL` | `claude-opus-5-5` | modèle Claude |
| `REGIE_CLAUDE_EFFORT` | `low` | effort (`low` = réponse rapide, suffisant pour ces ordres courts) |
| `REGIE_CLAUDE_FAST` | 0 | 1 = mode rapide (bêta, plus cher, ~2x plus rapide en sortie) |
| `REGIE_CLAUDE_FALLBACKS` | 1 | repli serveur automatique si Claude refuse une demande |
| `REGIE_CLAUDE_TIMEOUT` | 12 | délai max d'un appel Claude (s) |
| `REGIE_WHISPER_MODEL` / `_DEVICE` / `_COMPUTE` | auto | auto = `large-v3-turbo` float16 sur GPU, sinon `small` int8 sur CPU |
| `REGIE_IDLE_MINUTES` | 4 | seuil « pas de note depuis longtemps → manche suivante » (le contexte du serveur prime) |

## API
- `GET /health` → `{ok, whisper, whisperReady, apiKey, model, understanding}`
- `POST /command` ← `{"audio": "<WAV base64>", "context": {...}}` (ou `"text"` au lieu de `"audio"`)
  → `{transcript, actions[], reply, warnings[], error, timings{stt, claude, total}}`.
  `error` : `null`, `no_api_key`, `claude_error`, `refused`, `stt_error`, `empty_transcript`, `bad_request`.

## Fonctionnement
1. **Whisper** (`regie/stt.py`) : français, avec en *prompt* les pseudos, surnoms et manches du contexte
   (« Cycy », « JustEdible » sont alors bien écrits). GPU : cuBLAS vient de la roue pip `nvidia-cublas-cu12`, cuDNN 9
   est livré avec ctranslate2 ; les dossiers DLL sont ajoutés au démarrage.
2. **Claude** (`regie/understand.py`) : une seule requête, sans boucle. Les outils (`regie/schema.py`, `strict`)
   et le prompt système (`regie/prompt.py`, règles + exemples) sont fixes → **mis en cache** (préfixe identique à
   chaque requête) ; le contexte et la transcription vont dans le message. Claude répond par des appels d'outils =
   les actions, dans l'ordre ; `say` pour répondre sans rien faire (doute réel).
3. **Validation** (`regie/validate.py`) : action inconnue, champ manquant, joueur ou manche introuvable → action
   écartée avec une raison (mieux vaut ne rien faire) ; nombres et oui/non convertis ; joueurs et manches ramenés
   au pseudo / id exact (approché + phonétique, ambiguïté = rien) ; règle d'inactivité appliquée (lancer la manche
   suivante avant la note) ; « next » / « current » figés en id réel quand une manche est lancée dans le même ordre.

## Tests
```powershell
python -m pytest tests                    # logique pure, faux client Claude, aucun réseau
python tests\make_tts_wavs.py Julie       # phrases de tests\tts_phrases.txt -> tests\audio\*.wav (voix Windows)
python tests\whisper_check.py             # transcription + latence de ces WAV (avec / sans vocabulaire)
```
`tests/audio/*.wav` servent aussi au test en jeu (dossier déclencheur, voir le wiki Tests).
