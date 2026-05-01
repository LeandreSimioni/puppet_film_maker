# Déboguer l'app

## ⚠️ Contexte pour la prochaine session Claude

**Problème en cours (2026-05-01) :** le build CI GitHub Actions échoue à l'étape "Build Debug APK".

**Erreur exacte :**
```
Execution failed for task ':app:dataBindingMergeDependencyArtifactsDebug'.
> Could not resolve all files for configuration ':app:debugCompileClasspath'.
   > Could not find com.arthenica:ffmpeg-kit-full:6.0-2.
     Required by: project :app
```

**Ce qui a déjà été essayé (ne pas répéter) :**
- Ajouter `android.useAndroidX=true` dans `gradle.properties` ✅ (autre erreur corrigée)
- Ajouter Sonatype releases comme repo ❌ (même erreur)
- Changer `repositoriesMode` en `PREFER_SETTINGS` puis le supprimer ❌ (même erreur)
- `mavenCentral()` + `google()` dans `settings.gradle` ❌ (même erreur)

**Ce que tu dois faire en premier :**
```bash
curl -I "https://repo1.maven.org/maven2/com/arthenica/ffmpeg-kit-full/6.0-2/ffmpeg-kit-full-6.0-2.aar"
```
→ Si 200 : l'artifact existe, le problème est ailleurs (config Gradle, réseau CI)
→ Si 404 : l'artifact n'existe pas sous ce nom, il faut trouver le bon nom

**Piste la plus probable :** la version `6.0-2` n'existe pas sur Maven Central avec ce nom exact. Essaie de lister les versions disponibles :
```bash
curl -s "https://repo1.maven.org/maven2/com/arthenica/ffmpeg-kit-full/maven-metadata.xml"
```
Puis mettre à jour `app/build.gradle` avec la bonne version.

**Fichiers concernés :**
- `app/build.gradle` → ligne `implementation 'com.arthenica:ffmpeg-kit-full:6.0-2'`
- `settings.gradle` → configuration des repositories Maven
- `.github/workflows/build.yml` → pipeline CI

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
