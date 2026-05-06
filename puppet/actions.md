Pointer le spectateur :
[
  {"t": 0.0, "action": "setGaze", "gx": 0, "gy": 0},
  {"t": 0.5, "action": "pointForward", "side": "right", "duration": 0.4},
  {"t": 2.5, "action": "setArm", "side": "right", "shoulder": 0, "elbow": 0, "duration": 0.6}
]
if (ev.action === 'pointForward') {
    const prefix = ev.side === 'left' ? 'left' : 'right';
    // Lève le bras vers l'avant et garde le coude presque tendu
    addTween(prefix + 'Shoulder', 1.8, currentT, dur);
    addTween(prefix + 'Elbow', 0.05, currentT, dur);
}

TOur complet de la tête :
[
  {"t": 0.0, "action": "setGaze", "gx": 0, "gy": 0},
  {"t": 0.5, "action": "spin360", "direction": "right", "duration": 0.5}
]
if (ev.action === 'spin360') {
    // Fait un tour complet (2 * Pi radians) dans la direction voulue
    const dir = ev.direction === 'left' ? 1 : -1;
    const targetSpin = state.spinY + (Math.PI * 2 * dir);
    addTween('spinY', targetSpin, currentT, dur);
}

Loucher :
rajouter ce petit correctif dans la fonction updatePuppet() de ton code principal :

Ajouter crossEye: 0 dans l'objet let state = { ... }

Remplacer les deux blocs de rotation rotation.y des yeux par :
leftEyeSocket.rotation.y = (-state.eyeX * 0.5) + (state.crossEye || 0);
rightEyeSocket.rotation.y = (-state.eyeX * 0.5) - (state.crossEye || 0);
[
  {"t": 0.0, "action": "setGaze", "gx": 0, "gy": 0},
  {"t": 0.5, "action": "crossEyes", "amount": 0.6, "duration": 0.4},
  {"t": 2.5, "action": "crossEyes", "amount": 0, "duration": 0.3}
]

if (ev.action === 'crossEyes') {
    // Fait loucher les yeux en ajoutant une valeur "crossEye" convergente
    addTween('crossEye', ev.amount !== undefined ? ev.amount : 0.6, currentT, dur);
}

Lever les yeux au ciel

[
  {"t": 0.0, "action": "setGaze", "gx": 0, "gy": 0},
  {"t": 0.0, "action": "setEye", "ex": 0, "ey": 0},
  {"t": 0.5, "action": "rollEyesUp", "duration": 0.8},
  {"t": 2.5, "action": "setEye", "ex": 0, "ey": 0, "duration": 0.4}
]

if (ev.action === 'rollEyesUp') {
    // Lève uniquement les pupilles vers le haut
    addTween('eyeY', -1.5, currentT, dur);
}
