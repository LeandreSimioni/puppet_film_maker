import { useState } from "react";

const DECIDED = [
  {
    id: "finalite",
    label: "Finalité",
    items: [
      "Vidéo carrée 1080×1080 — format Instagram natif",
      "Durée cible : ~1 minute par séquence",
      "Sous-titres incrustés — pour la lecture sans son",
      "Application Android personnelle — APK installé sur le téléphone de Léandre uniquement",
    ],
  },
  {
    id: "pipeline",
    label: "Pipeline général",
    items: [
      "Script texte (avec didascalies) → saisi ou généré par le Rédacteur LLM",
      "Texte seul → Voxtral TTS → audio WAV 24kHz",
      "Audio WAV → Voxtral STT → timestamps précis mot par mot (OBLIGATOIRE)",
      "Script complet → Orchestrateur LLM → timeline JSON de mouvements",
      "Timeline + timestamps + audio → rendu 3D marionnette frame par frame",
      "Frames + audio + sous-titres → ffmpeg → MP4 1080×1080",
    ],
  },
  {
    id: "lipsync",
    label: "Lip sync — double passe Mistral (non négociable)",
    items: [
      "Passe 1 : Voxtral TTS génère l'audio",
      "Passe 2 : Voxtral STT retranscrit cet audio → timestamps mot par mot",
      "Bouche ouverte exactement quand la parole commence, fermée quand elle s'arrête",
      "Amplitude audio (librosa) module l'ouverture à l'intérieur de chaque mot",
    ],
  },
  {
    id: "script-format",
    label: "Format de script",
    items: [
      "Texte nu = parole prononcée",
      "[Didascalie] = mouvement de marionnette entre crochets",
      "[Pause Xs] = silence explicite de X secondes",
      "Exemple : [Elle regarde ses papiers] / Alors... voilà. / [Pause 1.5s] / Il ne reviendra pas.",
    ],
  },
  {
    id: "prompt-redacteur",
    label: "Prompt — Rédacteur de scripts",
    items: [
      `Prompt système : "Tu es auteur de théâtre de marionnettes. Écris un script en alternant texte parlé (texte nu) et didascalies entre crochets []. Les didascalies décrivent uniquement des mouvements physiques ou des pauses : regard, inclinaison de tête, émotion, geste, pause chrono. Pas de descriptions de décor. Chaque segment de parole doit durer moins de 2 minutes."`,
    ],
  },
  {
    id: "prompt-orchestrateur",
    label: "Prompt — Orchestrateur de mouvements",
    items: [
      `Prompt système : "Tu es régisseur de marionnettes. On te donne un script avec didascalies et les timestamps de chaque mot parlé. Génère une timeline JSON d'actions : [{\"t\": 0.0, \"action\": \"setGaze\", \"gx\": 0, \"gy\": -0.8}, ...]. Les t sont en secondes depuis le début de l'audio. Actions disponibles : setGaze(gx,gy), setRoll(rad), setEmotion(e), playGesture(g), pause(s). Réponds uniquement avec le JSON."`,
    ],
  },
  {
    id: "movements",
    label: "Vocabulaire de mouvement",
    items: [
      "setGaze(gx, gy) — regard : gx gauche/droite [-1..1], gy bas/haut [-1..1]",
      "setRoll(rad) — inclinaison latérale [-0.4..0.4 rad]",
      "setEmotion(e) — neutre / joyeux / triste / surpris / interrogatif",
      "setVoiceEmotion(e) — émotion vocale TTS : Neutral / Sad / Happy / Excited / Curious / Angry",
      "playGesture(g) — hochement, sursaut, acquiescement, doute",
      "pause(s) — silence audio via ffmpeg, marionnette immobile",
    ],
  },
  {
    id: "puppet",
    label: "Marionnette",
    items: [
      "Technique : deux demi-sphères Three.js (upperJaw / lowerJaw) — technique Muppet authentique",
      "Thème de base : warm (skin #f2c0a8 / shirt #d98b4f / hair #4a3a2e)",
      "Chapeau haut-de-forme sombre — comme le prototype",
      "Yeux, nez, sourcils attachés à upperJaw (bougent avec la mâchoire haute)",
      "Thèmes modifiables : objet JSON { skin, shirt, hair } — une ligne de code par couleur",
      "Plusieurs marionnettes possibles — chacune avec son thème JSON et sa voix",
    ],
  },
  {
    id: "voice",
    label: "Voix",
    items: [
      "Voix : Marie (français) — émotion par défaut : Excited (Énergique, Enjoué, Enthousiaste)",
      "Émotions disponibles : Neutral, Sad, Happy, Excited, Curious, Angry",
      "Chaque segment de texte est envoyé séparément au TTS avec son émotion propre",
      "L'orchestrateur choisit l'émotion vocale segment par segment selon les didascalies",
      "Les segments audio sont ensuite concaténés par ffmpeg",
      "Plus tard : clonage zero-shot — 10–20 s de voix de référence en environnement calme → ref_audio dans l'API",
    ],
  },
  {
    id: "background",
    label: "Fond de scène",
    items: [
      "Choix de l'utilisateur à chaque génération : image depuis la galerie, fond uni, ou vidéo en boucle",
      "Implémentation : texture Three.js en arrière-plan ou fond renderer",
    ],
  },
  {
    id: "android",
    label: "Architecture Android — vérifiée",
    items: [
      "✅ Three.js dans Android WebView : fonctionne avec setJavaScriptEnabled(true) — confirmé",
      "❌ MediaRecorder + captureStream() : bug Chrome Android connu à partir de 720p — EXCLU",
      "✅ Méthode retenue : canvas.toDataURL('image/jpeg') → JavascriptInterface → Kotlin → fichiers JPEG temp",
      "✅ FFmpegKit assemble : ffmpeg -framerate 30 -i frame_%04d.jpg -i audio.wav -vf subtitles=subs.srt -c:v libx264 output.mp4",
      "Résolution canvas : 1080×1080 — si bug Android constaté en test, fallback 720×720 + scale FFmpegKit",
      "Sous-titres : fichier SRT généré par l'orchestrateur, brûlé dans la vidéo par FFmpegKit",
      "Temps de traitement : 2 à 4 minutes pour 1 minute de vidéo — parfait pour usage personnel",
      "Distribution : APK signé, installé manuellement — pas de Play Store",
      "Stack : Kotlin (UI + appels API Mistral) + WebView (Three.js) + FFmpegKit",
    ],
  },
];

const OPEN = [];

const STATUS_COLORS = {
  decided: { bg: "#1a2e1a", border: "#2d5a2d", accent: "#4caf50", label: "DÉCIDÉ" },
  open:    { bg: "#2a1f0e", border: "#5a3d1a", accent: "#ff9800", label: "À DÉCIDER" },
};

function Section({ data, type }) {
  const [open, setOpen] = useState(true);
  const colors = STATUS_COLORS[type];
  return (
    <div style={{ marginBottom: "1rem", border: `1px solid ${colors.border}`, borderRadius: "6px", overflow: "hidden", background: colors.bg }}>
      <button onClick={() => setOpen(o => !o)} style={{ width: "100%", display: "flex", alignItems: "center", gap: "0.75rem", padding: "0.75rem 1rem", background: "transparent", border: "none", cursor: "pointer", textAlign: "left" }}>
        <span style={{ fontSize: "0.6rem", fontFamily: "monospace", fontWeight: 700, letterSpacing: "0.12em", color: colors.accent, background: `${colors.accent}22`, border: `1px solid ${colors.accent}55`, padding: "2px 6px", borderRadius: "3px", flexShrink: 0 }}>
          {colors.label}
        </span>
        <span style={{ color: "#e8e0d0", fontWeight: 600, fontSize: "0.88rem", flex: 1 }}>{data.label}</span>
        <span style={{ color: "#666", fontSize: "0.8rem" }}>{open ? "▲" : "▼"}</span>
      </button>
      {open && (
        <div style={{ padding: "0.25rem 1rem 0.75rem 1rem" }}>
          {(data.items || data.questions || []).map((item, i) => (
            <div key={i} style={{ display: "flex", gap: "0.6rem", padding: "0.3rem 0", borderBottom: "1px solid #ffffff08", alignItems: "flex-start" }}>
              <span style={{ color: colors.accent, flexShrink: 0, marginTop: "2px", fontSize: "0.75rem" }}>{type === "open" ? "?" : "·"}</span>
              <span style={{ color: "#c8c0b0", fontSize: "0.8rem", lineHeight: 1.55, fontFamily: item.startsWith('"') ? "monospace" : "inherit" }}>{item}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default function App() {
  return (
    <div style={{ minHeight: "100vh", background: "#0f0f0f", color: "#e8e0d0", fontFamily: "'Courier New', Courier, monospace", padding: "2rem 1rem" }}>
      <div style={{ maxWidth: "700px", margin: "0 auto" }}>

        <div style={{ marginBottom: "2rem" }}>
          <div style={{ fontSize: "0.65rem", color: "#555", letterSpacing: "0.15em", marginBottom: "0.5rem" }}>COMPAGNIE UEUEUE — PROJET</div>
          <h1 style={{ fontSize: "1.4rem", fontWeight: 700, margin: 0, color: "#fff" }}>Muppet Video Generator</h1>
          <div style={{ fontSize: "0.75rem", color: "#555", marginTop: "0.4rem" }}>App Android personnelle · Instagram 1080×1080 · ~1 min</div>
        </div>

        <div style={{ background: "#161616", border: "1px solid #2a2a2a", borderRadius: "6px", padding: "1rem", marginBottom: "1.5rem" }}>
          <div style={{ color: "#555", fontSize: "0.6rem", letterSpacing: "0.12em", marginBottom: "0.6rem" }}>PIPELINE</div>
          <div style={{ display: "flex", flexWrap: "wrap", gap: "0.3rem", alignItems: "center" }}>
            {[
              ["Script", "#4fc3f7"], "→",
              ["Rédacteur LLM", "#ce93d8"], "→",
              ["Voxtral TTS", "#4caf50"], "→",
              ["Voxtral STT ×2", "#4caf50"], "→",
              ["Orchestrateur LLM", "#ce93d8"], "→",
              ["Rendu 3D", "#ce93d8"], "→",
              ["FFmpegKit", "#888"], "→",
              ["MP4 1080×1080", "#4fc3f7"],
            ].map((s, i) =>
              s === "→" ? <span key={i} style={{ color: "#333" }}>→</span> :
              <span key={i} style={{ color: s[1], background: `${s[1]}18`, border: `1px solid ${s[1]}33`, padding: "2px 8px", borderRadius: "3px", fontSize: "0.68rem" }}>{s[0]}</span>
            )}
          </div>
        </div>

        <div style={{ marginBottom: "0.5rem", fontSize: "0.6rem", color: "#444", letterSpacing: "0.12em" }}>POINTS RÉSOLUS</div>
        {DECIDED.map(d => <Section key={d.id} data={d} type="decided" />)}

        {OPEN.length > 0 && <>
          <div style={{ marginBottom: "0.5rem", marginTop: "1.5rem", fontSize: "0.6rem", color: "#444", letterSpacing: "0.12em" }}>À DÉCIDER</div>
          {OPEN.map(d => <Section key={d.id} data={d} type="open" />)}
        </>}

        <div style={{ marginTop: "2rem", fontSize: "0.7rem", color: "#4caf50", textAlign: "center" }}>✓ Toutes les décisions sont prises — prêt à coder</div>
      </div>
    </div>
  );
}
