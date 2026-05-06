// ============================================================
// ACTIONS.JS — Répertoire centralisé de tous les mouvements
// ============================================================
// Toute nouvelle action s'ajoute ICI et dans actions-catalog.json.
// Ne jamais écrire de logique d'action ailleurs.
// ============================================================

function applyAction(ev, currentT) {
  const dur = ev.duration ?? 0.25;
  switch (ev.action) {

    // ── TÊTE ──────────────────────────────────────────────────

    case 'setGaze':
      // Pivote la tête entière. gx=0 gy=0 = face caméra.
      addTween('gazeX', ev.gx ?? 0, currentT, dur);
      addTween('gazeY', ev.gy ?? 0, currentT, dur);
      break;

    case 'setRoll':
      // Incline la tête sur l'axe frontal (pencher de côté).
      addTween('roll', ev.rad ?? 0, currentT, dur);
      break;

    case 'setTilt':
      // Incline la tête sur l'axe latéral gauche/droite.
      addTween('tiltZ', ev.rad ?? 0, currentT, dur);
      break;

    case 'setSpin':
      // Rotation verticale absolue de la tête (valeur en radians).
      addTween('spinY', ev.rad ?? 0, currentT, dur);
      break;

    case 'spin360': {
      // Tour complet de la tête dans une direction (relatif à la position actuelle).
      const dir = ev.direction === 'left' ? 1 : -1;
      addTween('spinY', state.spinY + Math.PI * 2 * dir, currentT, dur);
      break;
    }

    // ── YEUX ──────────────────────────────────────────────────

    case 'setEye':
      // Déplace les pupilles indépendamment de la tête.
      addTween('eyeX', ev.ex ?? 0, currentT, dur);
      addTween('eyeY', ev.ey ?? 0, currentT, dur);
      break;

    case 'crossEyes':
      // Fait loucher les yeux. amount=0 pour revenir normal.
      addTween('crossEye', ev.amount ?? 0.6, currentT, dur);
      break;

    case 'rollEyesUp':
      // Lève uniquement les pupilles vers le haut.
      addTween('eyeY', -1.5, currentT, dur);
      break;

    // ── CORPS ─────────────────────────────────────────────────

    case 'moveX':
      // Déplace le personnage entier latéralement.
      addTween('posX', ev.x ?? 0, currentT, dur);
      break;

    case 'moveY':
      // Déplace le personnage entier verticalement.
      addTween('posY', ev.y ?? 0, currentT, dur);
      break;

    // ── BRAS ──────────────────────────────────────────────────

    case 'setArm': {
      // Contrôle un bras. side: "left" ou "right".
      // shoulder: 0=pendant, 1=levé ~60°. elbow: 0=tendu, 1=plié 90°.
      const prefix = ev.side === 'left' ? 'left' : 'right';
      if (ev.shoulder !== undefined) addTween(prefix + 'Shoulder', ev.shoulder, currentT, dur);
      if (ev.elbow   !== undefined)  addTween(prefix + 'Elbow',    ev.elbow,    currentT, dur);
      break;
    }

    case 'pointForward': {
      // Pointe vers l'avant (vers le spectateur). side: "left" ou "right".
      const prefix = ev.side === 'left' ? 'left' : 'right';
      addTween(prefix + 'Shoulder', 1.8, currentT, dur);
      addTween(prefix + 'Elbow', 0.05, currentT, dur);
      break;
    }

    // ── BOUCHE ────────────────────────────────────────────────

    case 'setMouth':
      // Contrôle direct de la bouche. Usage interne uniquement — le lip sync
      // est automatique via computeLipSync() et écrase cette valeur.
      state.mouthOpen = ev.amount ?? 0;
      break;

    default: break;
  }
}
