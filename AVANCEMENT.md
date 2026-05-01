# Avancement — Puppet Film Maker

## Phases

### ✅ Phase 1 — Pipeline vidéo sans audio
**Critère :** MP4 5s avec la marionnette qui bouge la bouche, visible dans la galerie.

**Ce qui a été fait :**
- WebView charge `assets/puppet/index.html` (Three.js, marionnette Muppet-style)
- Bouton `[DEV] Test export 5s` → `startRender(150, 30)` dans le JS
- `PuppetBridge.onFrame()` écrit les JPEG dans le cache
- `VideoExporter.assembleVideo()` appelle FFmpegKit → MP4 dans Movies
- `MediaScannerConnection.scanFile()` → visible dans la galerie
- `statusText` affiche le chemin complet du MP4

**Mergé dans `main` le 2026-05-01.**

---

### ⏳ Phase 2 — Pipeline complet avec audio
**Critère :** MP4 avec voix Marie (TTS), lip sync automatique (STT), sous-titres.

**Ce qui a été fait :**
- `GITHUB_USER` → `leandresimioni`, `GITHUB_REPO` → `puppet_film_maker` dans `GitHubConfig.kt`
- `computeLipSync(t, words)` ajouté dans `index.html` : bouche ouverte sur chaque mot (arc sinusoïdal), fermée entre les mots, décroissance 80ms après fin de mot
- `startRenderWithTimeline` accepte maintenant `words` comme 3e paramètre
- `launchRender` dans `MainActivity` passe les timestamps STT au JS

**À faire (tests sur appareil) :**
- [ ] Renseigner `mistral_api_key` dans `assets/config/config.json` (non commité)
- [ ] Tester `btnValidate` : TTS → STT → Orchestrateur → timeline JSON
- [ ] Tester `btnRender` : rendu avec timeline + audio + SRT → MP4
- [ ] Vérifier la génération des sous-titres (`generateSrt`)

---

### ⏳ Phase 3 — Correction itérative
**Critère :** modifier la timeline via chat sans refaire le TTS/STT.

**À faire :**
- [ ] Tester `btnRefine` : envoyer une demande de correction → nouvelle timeline → re-rendu
- [ ] Valider que l'audio est bien réutilisé (pas de nouveau TTS)

---

### ⏳ Phase 4 — Polissage
- [ ] Bundler Three.js r128 localement (`assets/puppet/three.min.js`) pour fonctionner hors-ligne
- [ ] Fallback 720×720 si bug WebView à 1080p
- [ ] Tester le fallback assets quand GitHub est inaccessible (`GitHubConfig`)
- [ ] Gérer les erreurs API (affichage utilisateur propre)

---

## Notes techniques
- Clé API Mistral : `assets/config/config.json` — **ne jamais committer**
- Résolution : 1080×1080 (fallback 720×720 + scale FFmpegKit si bug)
- Voix : Marie / Excited par défaut
- Three.js : chargé depuis CDN (nécessite internet sur l'appareil)
- `MediaRecorder` + `captureStream()` à 1080p : **bug Android connu, NE PAS UTILISER**
