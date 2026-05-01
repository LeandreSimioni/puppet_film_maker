# Instructions pour Claude Code

## Contexte

App Android personnelle — Compagnie UEUEUE.
Génère des vidéos 1080×1080 d'une marionnette Muppet-style pour Instagram.
Tous les choix techniques ont été vérifiés et sourcés. **Ne pas les remettre en question.**

## Ce qui est vérifié

- ✅ Three.js dans Android WebView : fonctionne avec `setJavaScriptEnabled(true)`
- ✅ `canvas.toDataURL()` → `JavascriptInterface` → Kotlin : méthode fiable pour exporter les frames
- ❌ `MediaRecorder` + `captureStream()` à 1080p sur Android : bug Chrome connu (bug 897727) — **NE PAS UTILISER**
- ✅ FFmpegKit : assemble JPEG séquence + WAV + SRT → MP4 1080×1080
- ✅ Mistral Voxtral TTS : voix Marie, émotions disponibles (Neutral/Sad/Happy/Excited/Curious/Angry)
- ✅ Mistral Voxtral STT : retourne timestamps mot par mot
- ✅ GitHub raw API : lecture des fichiers de config sans authentification

## Flow complet (dans l'ordre)

1. **Démarrage** : `GitHubConfig.load()` lit `puppet_constraints.md` et `director_memory.md` depuis GitHub
2. **Rédacteur** : texte brut → `mistralClient.redact()` → script avec didascalies [éditable par l'utilisateur]
3. **Valider** : `extractTextOnly()` retire les didascalies → `mistralClient.tts()` → audio.wav
4. **STT** : `mistralClient.stt(audioPath)` → timestamps mot par mot
5. **Orchestrateur** : script + timestamps + constraints + directorMemory → `mistralClient.orchestrate()` → timeline JSON
6. **Rendu** : timeline injectée dans WebView → `startRenderWithTimeline()` → frames JPEG → FFmpegKit → MP4
7. **Correction** : `mistralClient.refineOrchestration()` modifie la timeline → re-rendu (audio réutilisé, pas de nouveau TTS/STT)

## Phase 1 — PRIORITÉ IMMÉDIATE

Valider le pipeline vidéo de bout en bout SANS audio :
1. WebView charge `assets/puppet/index.html`
2. Bouton "[DEV] Test export 5s" → `startRender(150, 30)` dans le JS
3. JS exporte 150 frames JPEG via `Android.onFrame(jpeg, index)`
4. Kotlin écrit les fichiers JPEG dans le cache
5. FFmpegKit assemble → MP4 dans le dossier Movies
6. Message de confirmation avec le chemin

**Critère de succès Phase 1 :** un fichier MP4 de 5 secondes avec la marionnette qui bouge la bouche, visible dans la galerie.

## Fichiers de configuration (lus depuis GitHub)

- `puppet_constraints.md` : contraintes physiques fixes de la marionnette
- `director_memory.md` : préférences de mise en scène évolutives

Ces fichiers sont injectés dans le prompt de l'Orchestrateur à chaque génération.
En cas d'absence de réseau, fallback sur `assets/config/`.

## Format de script

```
[Elle regarde ses papiers]
Alors... voilà ce qu'il m'a dit.
[Pause 1.5s]
[Angry]
Il ne reviendra pas.
```

- Texte nu = parole (envoyé au TTS)
- `[Didascalie]` = mouvement (interprété par l'Orchestrateur)
- `[Pause Xs]` = silence

## Notes

- Clé API dans `assets/config/config.json` — ne jamais committer (voir .gitignore)
- Mettre à jour `GITHUB_USER` dans `GitHubConfig.kt`
- Résolution : 1080×1080 — si bug Android constaté en test, fallback 720×720 + scale FFmpegKit
- Voix : Marie / Excited par défaut
- Temps de traitement : 2 à 4 minutes pour 1 minute de vidéo — acceptable
