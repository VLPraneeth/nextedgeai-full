# NextEdge AI logo redesign

## Decision

Selected direction: **Cut N wordmark**. It is the only explored family that treats the name itself as the identity instead of adding another generic SaaS badge. The custom `N` becomes the favicon and collapsed-navigation mark, while the full lockup remains calm and institutional.

## Explored families

| Direction | Architecture | Assessment |
| --- | --- | --- |
| Cut N wordmark | Letterform-as-symbol / wordmark | Selected. Strongest category fit, lowest visual noise, and one coherent small-size fallback. |
| N/E monogram | Monogram + lockup | Legible but too mechanically busy; the separate `E` bars compete with the name. |
| Threshold edge | Abstract lockup | Clean, but the offset-plane motif is common in enterprise software and weak in one color. |
| Keystone | Geometric symbol + lockup | Reproducible, but reads as a bridge or ceremonial gate before it reads as NextEdge. |
| Gateway study | Abstract gesture | Rejected. Glow, tonal variation, and dark presentation fail the flat, timeless requirement. |

## Production specification

- **Typography:** Inter variable, optical size 32, weight 620; exported to SVG paths so rendering never depends on a local font.
- **Symbol:** custom geometric `N` on a 64-unit grid with one brass diagonal edge.
- **Colors:** ink `#1C1917`, brass `#A16207`, warm ivory `#FAFAF9`, white reverse `#FFFFFF`.
- **Hierarchy:** primary lockup, standalone mark, mono mark, reverse lockup, reverse mark.
- **Minimums:** 16 px standalone mark; 24 px full-lockup height; one-quarter mark-width clear space.

## Validation gates

- 16, 24, 32, 38, 48, and 62 px digital rendering.
- Light, dark, one-color, and maskable-app-icon contexts.
- Header, landing page, login, collapsed navigation, graph node, system field, About dialog, favicon, and PWA manifest.
- Independent trademark clearance is still required before legal registration or paid brand rollout.
