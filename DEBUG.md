# Déboguer l'app

## ✅ Bug résolu (2026-05-01)

**Problème :** `com.arthenica:ffmpeg-kit-full:6.0-2` introuvable.

**Cause racine :** FFmpegKit a été retiré officiellement en janvier 2025. Les binaires Maven Central ont été supprimés le 1er avril 2025. Le search.maven.org montre encore les entrées mais les fichiers AAR n'existent plus (404).

**Solution appliquée :** remplacement par le fork communautaire drop-in :
```
com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1
```
- Disponible sur Maven Central ✅
- Classes identiques : `com.arthenica.ffmpegkit.*` — aucun changement de code Kotlin ✅
- Dépendance transitive `com.arthenica:smart-exception-java:0.2.1` aussi sur Maven Central ✅
- AAR ~83 Mo, variant "full" avec tous les codecs ✅

---

## Récupérer les logs depuis ton PC (Windows)

### 1. Télécharger Platform Tools (30 Mo, pas d'installation)
https://developer.android.com/tools/releases/platform-tools
→ Extraire le zip, par exemple dans `C:\platform-tools\`

### 2. Activer le débogage USB sur ton téléphone
Paramètres → À propos du téléphone → taper 7 fois sur "Numéro de build"
Puis : Paramètres → Options développeur → Débogage USB → Activé

### 3. Brancher le téléphone en USB
Accepter la demande d'autorisation sur le téléphone.

### 4. Ouvrir un terminal dans le dossier platform-tools
Shift + clic droit → "Ouvrir la fenêtre PowerShell ici"

### 5. Lancer les logs
```
.\adb logcat | findstr /I "muppet error exception ffmpeg mistral"
```

Tu verras les erreurs en temps réel. Copie les lignes pertinentes et donne-les à Claude Code.

## Obtenir le log de build CI

Si le build CI échoue, un artefact `build-log` est uploadé automatiquement :
1. GitHub → onglet "Actions" → dernier run "Build APK"
2. Descendre jusqu'à "Artifacts" → télécharger `build-log`
3. Ouvrir `build.log` → chercher `* What went wrong:`
