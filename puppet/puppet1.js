// puppet1.js — Marionnette design avancé (cheveux, lèvres canvas, yeux calibrés)
// Expose: buildPuppet(scene, root) → { headGroup, upperJaw, lowerJaw,
//           leftArmGroup, rightArmGroup, leftElbowGroup, rightElbowGroup,
//           leftEyeSocket, rightEyeSocket }

function buildPuppet(scene, root) {
  const skinMat  = new THREE.MeshStandardMaterial({color:0xf2c0a8, roughness:0.85});
  const shirtMat = new THREE.MeshStandardMaterial({color:0xd98b4f, roughness:0.9});
  const intMat   = new THREE.MeshStandardMaterial({color:0x0a0203, roughness:1.0});

  // Corps
  const neck = new THREE.Mesh(
    new THREE.CylinderGeometry(0.15,0.15,0.4,24),
    new THREE.MeshStandardMaterial({color:0xd49060, roughness:0.8}));
  neck.position.set(0,-1.18,0); root.add(neck);

  const torsoGroup = new THREE.Group();
  torsoGroup.position.set(0,-1.7,0); root.add(torsoGroup);
  torsoGroup.add(new THREE.Mesh(new THREE.CylinderGeometry(0.5,0.5,1.0,32), shirtMat));
  const leftSh = new THREE.Mesh(new THREE.SphereGeometry(0.22,24,24), shirtMat);
  leftSh.position.set(-0.65,0.35,0); torsoGroup.add(leftSh);
  const rightSh = new THREE.Mesh(new THREE.SphereGeometry(0.22,24,24), shirtMat);
  rightSh.position.set(0.65,0.35,0); torsoGroup.add(rightSh);

  // Bras
  let leftArmGroup, rightArmGroup, leftElbowGroup, rightElbowGroup;
  function makeArm(sx) {
    const g = new THREE.Group();
    g.position.set(sx*0.65,-1.35,0.0);
    const sleeve = new THREE.Mesh(new THREE.CylinderGeometry(0.13,0.11,0.25,12), shirtMat);
    sleeve.position.y = -0.125; g.add(sleeve);
    const ua = new THREE.Mesh(new THREE.CylinderGeometry(0.09,0.09,0.55,12), skinMat);
    ua.position.y = -0.275; g.add(ua);
    const elbow = new THREE.Group(); elbow.position.y = -0.55; g.add(elbow);
    const fa = new THREE.Mesh(new THREE.CylinderGeometry(0.08,0.07,0.5,12), skinMat);
    fa.position.y = -0.25; elbow.add(fa);
    const hand = new THREE.Mesh(new THREE.SphereGeometry(0.12,12,10), skinMat);
    hand.position.y = -0.52; elbow.add(hand);
    root.add(g);
    if (sx < 0) { leftArmGroup = g; leftElbowGroup = elbow; }
    else         { rightArmGroup = g; rightElbowGroup = elbow; }
  }
  makeArm(-1); makeArm(1);

  // Tête
  const headGroup = new THREE.Group(); root.add(headGroup);
  headGroup.add(new THREE.Mesh(new THREE.SphereGeometry(0.98,32,24), intMat));

  // Mâchoire haute avec lèvre peinte
  const uCanvas = document.createElement('canvas'); uCanvas.width=1024; uCanvas.height=512;
  const uCtx = uCanvas.getContext('2d');
  uCtx.fillStyle='#f2c0a8'; uCtx.fillRect(0,0,1024,512);
  uCtx.fillStyle='#cc1111'; uCtx.beginPath();
  uCtx.moveTo(472,512); uCtx.quadraticCurveTo(492,477,512,500);
  uCtx.quadraticCurveTo(532,477,552,512); uCtx.fill();
  const upperJaw = new THREE.Group(); headGroup.add(upperJaw);
  upperJaw.add(new THREE.Mesh(
    new THREE.SphereGeometry(1,48,32,-Math.PI/2,Math.PI*2,0,Math.PI/2),
    new THREE.MeshStandardMaterial({map:new THREE.CanvasTexture(uCanvas), color:0xffffff, roughness:0.85})));

  // Mâchoire basse avec lèvre peinte
  const lCanvas = document.createElement('canvas'); lCanvas.width=1024; lCanvas.height=512;
  const lCtx = lCanvas.getContext('2d');
  lCtx.fillStyle='#f2c0a8'; lCtx.fillRect(0,0,1024,512);
  lCtx.fillStyle='#cc1111'; lCtx.beginPath();
  lCtx.moveTo(472,0); lCtx.quadraticCurveTo(512,60,552,0); lCtx.fill();
  const lowerJaw = new THREE.Group(); headGroup.add(lowerJaw);
  lowerJaw.add(new THREE.Mesh(
    new THREE.SphereGeometry(1,48,32,-Math.PI/2,Math.PI*2,Math.PI/2,Math.PI/2),
    new THREE.MeshStandardMaterial({map:new THREE.CanvasTexture(lCanvas), color:0xffffff, roughness:0.85})));

  // Cheveux
  const hairMat = new THREE.MeshStandardMaterial({
    color:0x261230, emissive:0x261230, emissiveIntensity:0.35, roughness:0.7, side:THREE.DoubleSide});
  const hairGroup = new THREE.Group(); upperJaw.add(hairGroup);
  hairGroup.add(new THREE.Mesh(
    new THREE.SphereGeometry(1.06,48,16,0,Math.PI*2,0,Math.PI*0.15), hairMat));
  const backGeo = new THREE.SphereGeometry(1.06,48,32,Math.PI*0.75,Math.PI*1.5,Math.PI*0.15,Math.PI*0.65);
  const hPos = backGeo.attributes.position;
  for (let i=0; i<hPos.count; i++) {
    const x=hPos.getX(i), y=hPos.getY(i), z=hPos.getZ(i);
    if (y < 0.2) {
      const depth=0.2-y, flare=Math.pow(depth,1.5)*0.35;
      const r=Math.sqrt(x*x+z*z), scale=(r+flare)/r;
      hPos.setX(i,x*scale); hPos.setZ(i,z*scale);
    }
  }
  backGeo.computeVertexNormals();
  hairGroup.add(new THREE.Mesh(backGeo, hairMat));
  hairGroup.add(new THREE.Mesh(
    new THREE.SphereGeometry(1.06,24,12,Math.PI*0.25,Math.PI*0.5,Math.PI*0.15,Math.PI*0.13), hairMat));

  // Yeux calibrés
  let leftEyeSocket, rightEyeSocket;
  const CALIB = { LX:-0.286, LY:-1.382, RX:3.159, RY:1.210 };
  function makeEye(sx) {
    const R=0.85, rx=-0.45, ry=sx*0.28;
    const px=R*Math.sin(ry)*Math.cos(rx), py=R*-Math.sin(rx), pz=R*Math.cos(ry)*Math.cos(rx);
    const socket = new THREE.Group(); socket.position.set(px,py,pz); upperJaw.add(socket);
    const calibPivot = new THREE.Group();
    calibPivot.rotation.x = sx<0 ? CALIB.LX : CALIB.RX;
    calibPivot.rotation.y = sx<0 ? CALIB.LY : CALIB.RY;
    socket.add(calibPivot);
    const eyeCanvas = document.createElement('canvas'); eyeCanvas.width=256; eyeCanvas.height=128;
    const eyeCtx = eyeCanvas.getContext('2d');
    eyeCtx.fillStyle='#f5f1e8'; eyeCtx.fillRect(0,0,256,128);
    eyeCtx.fillStyle='#1a1410'; eyeCtx.beginPath(); eyeCtx.arc(128,70,14,0,Math.PI*2); eyeCtx.fill();
    const globe = new THREE.Mesh(
      new THREE.SphereGeometry(0.22,24,24),
      new THREE.MeshStandardMaterial({map:new THREE.CanvasTexture(eyeCanvas), roughness:0.4}));
    globe.rotation.y=ry; globe.rotation.x=rx;
    calibPivot.add(globe);
    if (sx<0) leftEyeSocket=socket; else rightEyeSocket=socket;
  }
  makeEye(-1); makeEye(1);

  // Nez
  const nose = new THREE.Mesh(new THREE.SphereGeometry(0.1,16,12), skinMat);
  nose.scale.set(1,1.1,1.2); nose.position.set(0,0.20,1.0); upperJaw.add(nose);

  return { headGroup, upperJaw, lowerJaw,
           leftArmGroup, rightArmGroup, leftElbowGroup, rightElbowGroup,
           leftEyeSocket, rightEyeSocket };
}
