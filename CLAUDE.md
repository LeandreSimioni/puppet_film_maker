# Instructions pour Claude Code

## Contexte

App Android personnelle — Compagnie UEUEUE.
Génère des vidéos 1080×1080 d'une marionnette Muppet-style pour Instagram.
Tous les choix techniques ont été vérifiés. **Ne pas les remettre en question.**

## Ce qui est vérifié

- ✅ Three.js dans Android WebView : fonctionne avec `setJavaScriptEnabled(true)`
- ✅ `canvas.toDataURL()` → `JavascriptInterface` → Kotlin : méthode fiable pour exporter les frames
- ❌ `MediaRecorder` + `captureStream()` à 1080p sur Android : bug Chrome connu (bug 897727) — **NE PAS UTILISER**
- ✅ FFmpegKit : assemble séquence JPEG + audio → MP4 1080×1080
- ✅ Mistral Voxtral TTS : voix configurable via `actions-catalog.json`
- ✅ Mistral Voxtral STT : retourne timestamps mot par mot

## Fichiers de vérité — NE PAS DUPLIQUER AILLEURS

```
app/src/main/assets/puppet/
├── index.html          — moteur de rendu Three.js (UN SEUL, pas de copie)
├── puppet1.js          — géométrie 3D de la marionnette
├── three.min.js        — librairie Three.js (CDN fallback)
├── actions.js          — code JS de toutes les actions (applyAction)
└── actions-catalog.json — FICHIER DE VÉRITÉ UNIQUE :
                           voix TTS/STT, contraintes physiques,
                           préférences de mise en scène, règles orchestrateur,
                           catalogue des actions + recettes
```

**Règle absolue** : si une information existe dans `actions-catalog.json`, elle n'existe nulle part ailleurs.

## Ajouter une nouvelle action

1. Ajouter le `case` dans `actions.js`
2. Ajouter l'entrée dans `actions-catalog.json` → section `"actions"`
3. C'est tout — index.html charge actions.js automatiquement

## Flow complet (dans l'ordre)

1. **Démarrage** : `GitHubConfig.load()` lit `actions-catalog.json` depuis GitHub (fallback assets locaux)
2. **Rédacteur** : texte brut → `mistralClient.redact()` → script avec didascalies `[mouvement]`
3. **Valider** : `extractTextOnly()` retire les didascalies → `mistralClient.tts()` → audio
4. **STT** : `mistralClient.stt(audioPath)` → timestamps mot par mot
5. **Orchestrateur** : script + timestamps + catalog → `mistralClient.orchestrate()` → timeline JSON
6. **Rendu** : timeline injectée dans WebView → `startRenderWithTimeline()` → frames JPEG → FFmpegKit → MP4
7. **Correction** : `mistralClient.refineOrchestration()` modifie la timeline → re-rendu (audio réutilisé)

## Format de script

```
[Elle regarde ses papiers]
Alors... voilà ce qu'il m'a dit.
[Pause 1.5s]
Il ne reviendra pas.
```

- Texte nu = parole (envoyé au TTS)
- `[Didascalie]` = mouvement (interprété par l'Orchestrateur → actions du catalog)
- `[Pause Xs]` = silence (`insertSilence`)

## Exigences de qualité — NON NÉGOCIABLES

- **Lip sync mot par mot** obligatoire — les timestamps `word` du STT sont la base du rendu
- **Voix configurable** dans `actions-catalog.json` → section `voice`
- **1080×1080** — pas de downgrade sans raison technique prouvée
- Si une API ne supporte pas une fonctionnalité requise, chercher comment la débloquer

## Notes techniques

- Clé API dans `assets/config/config.json` — ne jamais committer (voir .gitignore)
- Résolution : 1080×1080 — si bug Android constaté en test, fallback 720×720 + scale FFmpegKit
- Temps de traitement : 2 à 4 minutes pour 1 minute de vidéo — acceptable
- `GitHubConfig` lit `actions-catalog.json` depuis GitHub raw API, expose `constraints`, `directorMemory`, `orchestratorRules`
