/**
 * VeloceEngine — Official Mascot Logo Generator (Precision Feline Anatomy v9 - Flawless)
 * Mascot: The Racing Cheetah (Acinonyx jubatus) — Speed, Zero-Allocation, Nanosecond Agility
 *
 * Perception & Aesthetics Polish:
 *  - Refined muzzle & whisker pads: broad lateral feline geometry (completely eliminates the "buck teeth" optical illusion)
 *  - Luxurious silver-slate and warm titanium tones instead of stark flat white
 *  - Classic inverted feline nose prism with sleek metallic bevel
 *  - 100% Watertight Solid Base Silhouette (Zero gaps, zero background bleed)
 *  - Piercing predatory almond eyes in glowing amber-gold with cyan speed glints
 *  - Razor-sharp aerodynamic ears flared at 30° predator angle
 *  - Coherent directional studio lighting (top-right key light, deep left obsidian shadows)
 */

'use strict';

const fs = require('fs');
const path = require('path');
const { Resvg } = require('c:/Users/alexander/.gemini/antigravity-ide/scratch/node_modules/@resvg/resvg-js');

function buildCheetahLogoSvg() {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" width="1024" height="1024">
  <defs>
    <!-- Squircle container clipping -->
    <clipPath id="squircle-clip">
      <rect x="24" y="24" width="976" height="976" rx="220" />
    </clipPath>

    <!-- Volumetric Obsidian & Slate Lighting Gradients -->
    <!-- Deep Shadow (Far Left) -->
    <linearGradient id="g-shadow-dark" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#070a13"/>
      <stop offset="100%" stop-color="#020408"/>
    </linearGradient>

    <!-- Mid Shadow (Left) -->
    <linearGradient id="g-shadow-mid" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#182335"/>
      <stop offset="100%" stop-color="#0b111d"/>
    </linearGradient>

    <!-- Neutral Midtone (Center Plane) -->
    <linearGradient id="g-mid" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#2c3b52"/>
      <stop offset="100%" stop-color="#1a2536"/>
    </linearGradient>

    <!-- Lit Slate (Right) -->
    <linearGradient id="g-lit-soft" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#475569"/>
      <stop offset="100%" stop-color="#2d3c50"/>
    </linearGradient>

    <!-- Bright Lit Slate (Far Right / Upper Crests) -->
    <linearGradient id="g-lit-bright" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#64748b"/>
      <stop offset="100%" stop-color="#475569"/>
    </linearGradient>

    <!-- Highlight Crest -->
    <linearGradient id="g-highlight" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#94a3b8"/>
      <stop offset="100%" stop-color="#64748b"/>
    </linearGradient>

    <!-- Sophisticated Silver-Slate Muzzle / Fur Shading (Eliminates optical illusions) -->
    <linearGradient id="g-muzzle-lit" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#cbd5e1"/>
      <stop offset="100%" stop-color="#94a3b8"/>
    </linearGradient>

    <linearGradient id="g-muzzle-shadow" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#64748b"/>
      <stop offset="100%" stop-color="#334155"/>
    </linearGradient>

    <linearGradient id="g-chin-lit" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#cbd5e1"/>
      <stop offset="100%" stop-color="#64748b"/>
    </linearGradient>

    <!-- Cheetah Amber Predator Eyes -->
    <linearGradient id="g-eye-l" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#f59e0b"/>
      <stop offset="60%" stop-color="#d97706"/>
      <stop offset="100%" stop-color="#78350f"/>
    </linearGradient>

    <linearGradient id="g-eye-r" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#fef08a"/>
      <stop offset="35%" stop-color="#f59e0b"/>
      <stop offset="100%" stop-color="#b45309"/>
    </linearGradient>

    <!-- Electric Cyan Telemetry & Speed Highlights -->
    <linearGradient id="g-cyan-pulse" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#38bdf8"/>
      <stop offset="50%" stop-color="#00f5ff"/>
      <stop offset="100%" stop-color="#0284c7"/>
    </linearGradient>

    <linearGradient id="g-ear-cyan-l" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#0284c7" stop-opacity="0.8"/>
      <stop offset="100%" stop-color="#0f172a" stop-opacity="0.2"/>
    </linearGradient>

    <linearGradient id="g-ear-cyan-r" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#00f5ff" stop-opacity="0.9"/>
      <stop offset="100%" stop-color="#0f172a" stop-opacity="0.2"/>
    </linearGradient>

    <!-- Subtle Elevation Shadow -->
    <filter id="hex-elevation" x="-10%" y="-10%" width="120%" height="120%">
      <feDropShadow dx="0" dy="16" stdDeviation="22" flood-color="#020617" flood-opacity="0.25" />
    </filter>
  </defs>

  <!-- Luxury White Squircle Container -->
  <rect x="24" y="24" width="976" height="976" rx="220" fill="#ffffff" stroke="#e2e8f0" stroke-width="6" />

  <g clip-path="url(#squircle-clip)">
    <g transform="translate(512, 512)" filter="url(#hex-elevation)">

      <!-- Hexagonal Architectural Gateway Frame -->
      <polygon points="
        0,-412
        356,-206
        356,206
        0,412
        -356,206
        -356,-206
      " fill="none" stroke="#0f172a" stroke-width="36" stroke-linejoin="round" />

      <!-- Inner High-Tech Telemetry Ring (Dashed Cyan) -->
      <polygon points="
        0,-374
        324,-187
        324,187
        0,374
        -324,187
        -324,-187
      " fill="none" stroke="#00f5ff" stroke-width="4" opacity="0.4" stroke-dasharray="16, 12" />

      <!-- ============================================================== -->
      <!-- 1. 100% WATERTIGHT SOLID BASE SILHOUETTE                       -->
      <!-- ============================================================== -->
      <path d="
        M 0,-220
        L 65,-190
        L 175,-285
        L 160,-150
        L 185,-95
        L 215,0
        L 195,75
        L 150,145
        L 130,200
        L 235,360
        L 0,405
        L -235,360
        L -130,200
        L -150,145
        L -195,75
        L -215,0
        L -185,-95
        L -160,-150
        L -175,-285
        L -65,-190
        Z
      " fill="#070a13" />

      <!-- ============================================================== -->
      <!-- 2. NECK & CHEST FOUNDATION                                     -->
      <!-- ============================================================== -->

      <!-- Right Neck Major Illuminated Plane -->
      <polygon points="
        0,188
        0,405
        235,360
        130,200
        150,145
      " fill="url(#g-shadow-mid)" />

      <!-- Left Neck Major Shadow Plane -->
      <polygon points="
        0,188
        0,405
        -235,360
        -130,200
        -150,145
      " fill="url(#g-shadow-dark)" />

      <!-- Outer Neck Sweeps -->
      <polygon points="-150,145 -130,200 -235,360 -195,75" fill="#05080f" />
      <polygon points="150,145 130,200 235,360 195,75" fill="#334155" />

      <!-- Chest Keel (Aerodynamic V-Armor) -->
      <polygon points="
        0,190
        -75,260
        0,385
        75,260
      " fill="#0b111d" />

      <polygon points="
        0,190
        75,260
        0,385
      " fill="#1e293b" />

      <!-- ============================================================== -->
      <!-- 3. CHEETAH EARS (Razor-sharp, aerodynamic 30° predator angle)  -->
      <!-- ============================================================== -->

      <!-- LEFT EAR (Shadow Side) -->
      <polygon points="-65,-190 -160,-150 -175,-285" fill="#070a13" />
      <polygon points="-75,-192 -145,-158 -160,-268 -100,-205" fill="#182335" />
      <polygon points="-85,-195 -132,-165 -148,-245" fill="url(#g-ear-cyan-l)" />
      <polygon points="-160,-150 -175,-285 -145,-158" fill="#020408" />

      <!-- RIGHT EAR (Lit Side) -->
      <polygon points="65,-190 160,-150 175,-285" fill="#1e293b" />
      <polygon points="75,-192 145,-158 160,-268 100,-205" fill="#475569" />
      <polygon points="85,-195 132,-165 148,-245" fill="url(#g-ear-cyan-r)" />
      <polygon points="160,-150 175,-285 145,-158" fill="#020408" />

      <!-- ============================================================== -->
      <!-- 4. SKULL & FOREHEAD MESH (Aerodynamic Feline Crown)            -->
      <!-- ============================================================== -->

      <!-- Crown Central Apex -->
      <polygon points="0,-220 -65,-190 -30,-150 0,-150" fill="#0f172a" />
      <polygon points="0,-220 65,-190 30,-150 0,-150" fill="#334155" />

      <!-- Forehead Upper Struts -->
      <polygon points="-65,-190 -30,-150 -90,-145" fill="#090d16" />
      <polygon points="65,-190 30,-150 90,-145" fill="#475569" />

      <!-- Forehead Central Diamond Plate -->
      <polygon points="0,-150 -30,-150 0,-95" fill="#182335" />
      <polygon points="0,-150 30,-150 0,-95" fill="#475569" />

      <!-- Forehead Lateral Wing Plates -->
      <polygon points="-30,-150 -90,-145 -115,-100 -35,-90 0,-95" fill="#0f172a" />
      <polygon points="30,-150 90,-145 115,-100 35,-90 0,-95" fill="#64748b" />

      <!-- Brow Ridge Facets -->
      <polygon points="-35,-90 -115,-100 -135,-55 -35,-52" fill="#182335" />
      <polygon points="35,-90 115,-100 135,-55 35,-52" fill="#64748b" />

      <!-- Cheetah Forehead Speed Spots (Warm Amber-Gold Diamond Badges) -->
      <polygon points="0,-135 -14,-125 0,-115 14,-125" fill="#f59e0b" opacity="0.95" />
      <polygon points="-30,-120 -42,-112 -30,-104 -18,-112" fill="#d97706" opacity="0.9" />
      <polygon points="30,-120 42,-112 30,-104 18,-112" fill="#fbbf24" opacity="0.95" />

      <!-- ============================================================== -->
      <!-- 5. BROAD ZYGOMATIC CHEEKBONES (Authentic Feline Head Width)     -->
      <!-- ============================================================== -->

      <!-- Left Cheek Outer Flange (Flares out to x = -215) -->
      <polygon points="-160,-150 -90,-145 -115,-100 -185,-95" fill="#070a13" />
      <polygon points="-185,-95 -115,-100 -135,-55 -215,0" fill="#0b111d" />
      <polygon points="-215,0 -135,-55 -125,25 -195,75" fill="#070a13" />
      <polygon points="-195,75 -125,25 -115,105 -150,145" fill="#0b111d" />

      <!-- Right Cheek Outer Flange (Flares out to x = +215) -->
      <polygon points="160,-150 90,-145 115,-100 185,-95" fill="#334155" />
      <polygon points="185,-95 115,-100 135,-55 215,0" fill="#475569" />
      <polygon points="215,0 135,-55 125,25 195,75" fill="#64748b" />
      <polygon points="195,75 125,25 115,105 150,145" fill="#475569" />

      <!-- Sub-Eye Cheek Facets -->
      <polygon points="-35,-52 -135,-55 -125,25 -42,15" fill="#0f172a" />
      <polygon points="35,-52 135,-55 125,25 42,15" fill="#334155" />

      <!-- Mid Cheek Lower Facets -->
      <polygon points="-42,15 -125,25 -115,105 -50,105" fill="#182335" />
      <polygon points="42,15 125,25 115,105 50,105" fill="#475569" />

      <!-- ============================================================== -->
      <!-- 6. NOSE BRIDGE (Sleek Tapered Feline Snout)                    -->
      <!-- ============================================================== -->

      <!-- Central Nose Bridge -->
      <polygon points="0,-95 -35,-52 -28,25 0,35" fill="#182335" />
      <polygon points="0,-95 35,-52 28,25 0,35" fill="#475569" />

      <!-- Center Ridge Specular Highlight -->
      <polygon points="0,-50 16,-10 0,30" fill="#94a3b8" />

      <!-- ============================================================== -->
      <!-- 7. PREDATOR ALMOND EYES (Amber Gold with Fierce Predator Cant) -->
      <!-- ============================================================== -->

      <!-- LEFT EYE -->
      <polygon points="-35,-52 -135,-55 -125,-25 -32,-32" fill="#020408" />
      <polygon points="-40,-48 -120,-52 -112,-28 -36,-34" fill="url(#g-eye-l)" />
      <polygon points="-76,-50 -82,-40 -76,-30 -70,-40" fill="#000000" />
      <polygon points="-62,-47 -70,-47 -66,-41" fill="#00f5ff" />
      <polygon points="-35,-52 -135,-55 -118,-50 -40,-46" fill="#000000" opacity="0.65" />

      <!-- RIGHT EYE (Catching Full Light) -->
      <polygon points="35,-52 135,-55 125,-25 32,-32" fill="#020408" />
      <polygon points="40,-48 120,-52 112,-28 36,-34" fill="url(#g-eye-r)" />
      <polygon points="76,-50 70,-40 76,-30 82,-40" fill="#000000" />
      <polygon points="88,-48 80,-48 84,-42" fill="#00f5ff" />
      <circle cx="90" cy="-44" r="2.8" fill="#ffffff" />
      <polygon points="35,-52 135,-55 118,-50 40,-46" fill="#0f172a" opacity="0.4" />

      <!-- ============================================================== -->
      <!-- 8. CHEETAH MALAR TEAR STRIPES                                  -->
      <!-- Natural Curving Obsidian Facets                               -->
      <!-- ============================================================== -->

      <!-- LEFT MALAR STRIPE (Jet Black Obsidian) -->
      <polygon points="
        -32,-32
        -44,-30
        -42,15
        -50,105
        -36,108
        -30,25
      " fill="#020408" stroke="#000000" stroke-width="1.5" />

      <polygon points="-30,25 -36,108 -26,105 -24,35" fill="#090d16" opacity="0.8" />

      <!-- RIGHT MALAR STRIPE (Jet Black Obsidian) -->
      <polygon points="
        32,-32
        44,-30
        42,15
        50,105
        36,108
        30,25
      " fill="#020408" stroke="#000000" stroke-width="1.5" />

      <polygon points="30,25 36,108 26,105 24,35" fill="#182335" opacity="0.5" />

      <!-- ============================================================== -->
      <!-- 9. REFINED FELINE NOSE PAD & LATERAL WHISKER PADS             -->
      <!-- (Broad triangular feline anatomy — completely eliminates buck teeth) -->
      <!-- ============================================================== -->

      <!-- FELINE NOSE PAD (Inverted Geometric Prism) -->
      <polygon points="-24,35 24,35 28,52 0,68 -28,52" fill="#070a13" />
      <polygon points="0,35 24,35 28,52 0,68" fill="#1e293b" />
      <polygon points="0,38 14,38 16,46 0,54" fill="#64748b" opacity="0.75" />
      <polygon points="-16,50 -23,52 -18,57" fill="#000000" />
      <polygon points="16,50 23,52 18,57" fill="#000000" />

      <!-- Left Whisker Pad (Broad Lateral Plane in Soft Shadow) -->
      <polygon points="
        -24,35
        -30,25
        -36,108
        0,118
        0,68
      " fill="url(#g-muzzle-shadow)" />

      <!-- Right Whisker Pad (Broad Lateral Plane Catching Studio Light) -->
      <polygon points="
        24,35
        30,25
        36,108
        0,118
        0,68
      " fill="url(#g-muzzle-lit)" />

      <!-- Philtrum Center Seam -->
      <line x1="0" y1="68" x2="0" y2="118" stroke="#182335" stroke-width="3" />

      <!-- Whisker Pad Lateral Outer Blends -->
      <polygon points="-36,108 -50,105 -30,25" fill="#182335" opacity="0.6" />
      <polygon points="36,108 50,105 30,25" fill="#475569" opacity="0.6" />

      <!-- ============================================================== -->
      <!-- 10. CHIN & JAWLINE (Sleek Predator Chin Wedge)                -->
      <!-- ============================================================== -->

      <!-- Center Chin Facet (Clean Feline Tapered Chin) -->
      <polygon points="0,118 -26,114 -18,152 0,160" fill="#475569" />
      <polygon points="0,118 26,114 18,152 0,160" fill="url(#g-chin-lit)" />

      <!-- Chin Lateral Shading -->
      <polygon points="-26,114 -36,108 -50,140 -18,152" fill="#182335" />
      <polygon points="26,114 36,108 50,140 18,152" fill="#334155" />

      <!-- Lower Jaw Transition to Throat -->
      <polygon points="-115,105 -150,145 -50,140" fill="#090d16" />
      <polygon points="115,105 150,145 50,140" fill="#334155" />

      <!-- Sub-Chin Throat Shadow -->
      <polygon points="0,160 -18,152 -50,140 0,188" fill="#090d16" />
      <polygon points="0,160 18,152 50,140 0,188" fill="#182335" />

      <!-- Throat Lateral Struts -->
      <polygon points="-50,140 -150,145 0,188" fill="#05080f" />
      <polygon points="50,140 150,145 0,188" fill="#2c3b52" />

      <!-- ============================================================== -->
      <!-- 11. HIGH-TECH TELEMETRY NODE (Zero-Latency Matching Core)      -->
      <!-- ============================================================== -->

      <!-- Laser Circuit Traces to Telemetry Core -->
      <line x1="-120" y1="285" x2="-45" y2="285" stroke="#00f5ff" stroke-width="3" opacity="0.65" stroke-dasharray="6,6" />
      <line x1="45" y1="285" x2="120" y2="285" stroke="#00f5ff" stroke-width="3" opacity="0.65" stroke-dasharray="6,6" />

      <!-- Telemetry Hex Core Outer -->
      <polygon points="
        0,250
        35,270
        35,310
        0,330
        -35,310
        -35,270
      " fill="#0b1329" stroke="#00f5ff" stroke-width="4" stroke-linejoin="round" />

      <!-- Inner Glowing Core -->
      <polygon points="
        0,260
        24,274
        24,302
        0,316
        -24,302
        -24,274
      " fill="url(#g-cyan-pulse)" />

      <!-- Nanosecond Center Glint -->
      <circle cx="0" cy="288" r="5" fill="#ffffff" />
      <circle cx="0" cy="288" r="12" fill="none" stroke="#ffffff" stroke-width="1.5" opacity="0.8" />

    </g>
  </g>
</svg>`;
}

function main() {
  console.log('Generating VeloceEngine Cheetah Logo (v9 Flawless)...');
  const svg = buildCheetahLogoSvg();

  const svgPath = path.resolve(__dirname, '..', 'docs', 'images', 'logo.svg');
  const pngPath = path.resolve(__dirname, '..', 'docs', 'images', 'logo.png');

  fs.writeFileSync(svgPath, svg, 'utf8');
  console.log('Saved SVG to:', svgPath);

  const resvg = new Resvg(svg, {
    fitTo: {
      mode: 'width',
      value: 1024,
    },
    shapeRendering: 2,
    textRendering: 1,
    imageRendering: 0,
  });

  const pngData = resvg.render();
  const pngBuffer = pngData.asPng();

  fs.writeFileSync(pngPath, pngBuffer);
  console.log('Saved PNG (1024x1024) to:', pngPath);
}

main();
