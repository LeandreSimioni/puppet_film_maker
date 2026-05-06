# Actions disponibles pour l'Orchestrateur

Ce fichier est la référence exhaustive de tout ce que le personnage peut faire.
Tu génères une timeline JSON triée par "t" croissant. Chaque événement déclenche une action au moment exact.

---

## TÊTE

### setGaze(gx, gy)
Pivote la **tête entière** dans une direction. Les yeux suivent la tête.
- `gx` : horizontal. -1 = regard droite, 0 = centre, 1 = regard gauche.
- `gy` : vertical. -1 = regard vers le bas, 0 = centre, 1 = regard vers le haut.
- Exemple : `{"t": 1.0, "action": "setGaze", "gx": 0.6, "gy": -0.2}`
- Usage : regarder quelque chose, fuir le regard caméra, observer.

### setEye(ex, ey)
Déplace les **pupilles** indépendamment de la tête. La tête ne bouge pas.
Crée des micro-expressions très efficaces : hésitation, furtivité, surprise intérieure.
- `ex` : horizontal. -1 = pupilles vers la droite, 1 = vers la gauche.
- `ey` : vertical. -1 = pupilles vers le bas, 1 = vers le haut.
- Exemple : `{"t": 2.5, "action": "setEye", "ex": -0.5, "ey": 0.3}`
- Usage : coup d'œil furtif, réflexion intérieure, malaise, surprise, évitement.
- Recommandé : alterner setGaze (mouvement ample) + setEye (micro-réaction).

### setRoll(rad)
Incline la tête sur son axe frontal (comme pencher la tête de côté).
- `rad` : en radians. Plage recommandée : -0.35 à 0.35.
- Positif = inclinaison vers la droite (du point de vue caméra).
- Exemple : `{"t": 3.0, "action": "setRoll", "rad": 0.25}`
- Usage : interrogation, attendrissement, incrédulité, écoute attentive.

### setTilt(rad)
Incline la tête sur l'axe latéral gauche/droite (différent de setRoll).
- `rad` : en radians. Plage recommandée : -0.4 à 0.4.
- Exemple : `{"t": 2.0, "action": "setTilt", "rad": 0.3}`
- Usage : curiosité, doute, écoute penchée.

### setSpin(rad)
Tourne la tête sur son axe vertical de façon additive au regard.
- `rad` : en radians. Plage recommandée : -0.5 à 0.5.
- Exemple : `{"t": 5.0, "action": "setSpin", "rad": -0.3}`
- Usage : profil partiel, regard oblique, évitement.

### moveX(x)
Déplace le **personnage entier** latéralement.
- `x` : -1.5 (gauche) à 1.5 (droite). 0 = centre.
- Exemple : `{"t": 4.0, "action": "moveX", "x": -0.4}`
- Usage : repositionnement, entrer/sortir du cadre (x: ±2.0).

### moveY(y)
Déplace le **personnage entier** verticalement.
- `y` : -1.5 (bas) à 1.5 (haut). 0 = centre.
- Exemple : `{"t": 6.0, "action": "moveY", "y": -0.3}`
- Usage : s'affaisser, se redresser, effet de recul.

---

## BRAS

### setArm(side, shoulder, elbow)
Contrôle un bras. Les bras sont au repos par défaut (pendant le long du corps).

- `side` : `"left"` ou `"right"`.
- `shoulder` : intensité du lever de bras vers l'avant/haut. 0 = pendant, 1 = bras levé à ~60°.
  Plage : 0.0 à 1.2 (au-delà = excessif).
- `elbow` : courbure du coude. 0 = bras tendu, 1 = avant-bras replié à 90°.
  Plage : 0.0 à 1.0.

**Exemples de gestes :**

Geste expressif (style "à l'italienne") :
```json
{"t": 5.0, "action": "setArm", "side": "left",  "shoulder": 0.7, "elbow": 0.5},
{"t": 5.0, "action": "setArm", "side": "right", "shoulder": 0.6, "elbow": 0.4}
```

Pointer / désigner :
```json
{"t": 6.0, "action": "setArm", "side": "right", "shoulder": 1.0, "elbow": 0.1}
```

Haussement d'épaules (lever bref puis retour) :
```json
{"t": 7.0, "action": "setArm", "side": "left",  "shoulder": 0.5, "elbow": 0.0},
{"t": 7.0, "action": "setArm", "side": "right", "shoulder": 0.5, "elbow": 0.0},
{"t": 7.8, "action": "setArm", "side": "left",  "shoulder": 0.0, "elbow": 0.0},
{"t": 7.8, "action": "setArm", "side": "right", "shoulder": 0.0, "elbow": 0.0}
```

Retour au repos :
```json
{"t": 9.0, "action": "setArm", "side": "left",  "shoulder": 0.0, "elbow": 0.0},
{"t": 9.0, "action": "setArm", "side": "right", "shoulder": 0.0, "elbow": 0.0}
```

**Règle** : ne jamais laisser les bras immobiles pendant toute la vidéo. Même un léger geste (shoulder 0.15, elbow 0.1) à mi-discours suffit à donner vie au personnage.

---

## TEMPS

### insertSilence(duration)
Coupe l'audio et insère `duration` secondes de silence.
Tous les timestamps **après ce point** sont décalés automatiquement.
- `duration` : durée en secondes.
- Exemple : `{"t": 3.5, "action": "insertSilence", "duration": 0.6}`

> **Règles pour `insertSilence`** :
>
> Le TTS génère déjà des pauses naturelles aux fins de phrases. Les timestamps STT
> les reflètent. Si tu places un `insertSilence` juste à la fin d'un mot ou au
> début du mot suivant, tu **doubles** la pause existante — ne fais jamais ça.
>
> **Règle :** ne place `insertSilence` que si le gap naturel entre deux mots est
> insuffisant pour l'effet voulu. Dans ce cas :
>
> 1. Calcule le gap naturel : `G = mot_suivant.start − mot_precedent.end`
> 2. Calcule la durée à insérer : `D = pause_souhaitée − G` (si D ≤ 0, n'insère rien)
> 3. Place le `t` **au centre** du gap existant : `t = mot_precedent.end + G / 2`
>
> **Exemple** : gap naturel de 0.6s entre "dit." (fin 3.2s) et "Il" (début 3.8s),
> tu veux 1.2s de pause totale :
> - D = 1.2 − 0.6 = 0.6s à insérer
> - t = 3.2 + 0.6/2 = 3.5s
> - → `{"t": 3.5, "action": "insertSilence", "duration": 0.6}`
>
> Placer le `t` au centre du gap (et non au bord d'un mot) présente deux avantages :
> jamais de risque de couper un mot, et les artefacts de découpe audio tombent
> dans du silence où ils sont inaudibles.

---

## BOUCHE (AUTOMATIQUE — NE PAS CONTRÔLER)

Le lip sync est entièrement calculé à partir des timestamps STT mot par mot.
**Ne jamais générer `setMouth`** dans la timeline — il sera ignoré ou créera des conflits avec le lip sync automatique.

---

## RÈGLES DE MISE EN SCÈNE

1. **Varier le regard toutes les 2–4 secondes.** Un regard fixe = personnage mort.
   Alterner `setGaze` (mouvement de tête) et `setEye` (micro-mouvement de pupilles).

2. **Les bras parlent.** Utiliser `setArm` au moins 2–3 fois par vidéo.
   Style naturel : un bras à la fois, coude légèrement plié, shoulder entre 0.3 et 0.8.

3. **Les silences sont des outils.** `insertSilence` crée la tension, la respiration, le drame.

4. **Revenir au centre après un geste fort.**
   Après `setGaze` extrême ou `setArm` levé : prévoir un retour à 0 environ 1s après.

5. **"Ne rien faire" est valide.** Un personnage figé dans son dernier état pendant 2s
   peut être plus expressif qu'une agitation permanente.

6. **Synchroniser les gestes avec les mots clés.**
   Un bras levé au mot "jamais", un setEye fuyant au mot "mentir" — c'est ce niveau de détail qui compte.
