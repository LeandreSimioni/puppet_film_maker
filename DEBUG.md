# Déboguer l'app

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

## Obtenir l'APK depuis GitHub Actions

1. Aller sur ton repo GitHub → onglet "Actions"
2. Cliquer sur le dernier workflow "Build APK"
3. Descendre jusqu'à "Artifacts"
4. Télécharger "muppet-debug"
5. Extraire le zip → `app-debug.apk`
6. Envoyer le fichier sur ton téléphone (email, Drive, câble USB)
7. Ouvrir le fichier sur le téléphone → Installer
   (autoriser les sources inconnues si demandé)
