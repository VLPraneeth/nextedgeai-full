# NextEdge AI logo system

The primary identity is a wordmark-first system. Its custom cut `N` is both the first letter of the lockup and the standalone small-size mark. The single brass edge represents the controlled transition where connected data becomes action.

## Assets

- `nextedge-lockup.svg`: primary light-background lockup.
- `nextedge-lockup-reverse.svg`: reverse lockup for dark backgrounds.
- `nextedge-mark.svg`: standalone `N` for favicons, graphs, and collapsed navigation.
- `nextedge-mark-mono.svg`: one-color reproduction.
- `nextedge-mark-reverse.svg`: reverse standalone mark.
- PNG, Apple touch, maskable PWA, and multi-size ICO exports are generated from the same construction grid.

## Rules

- Ink: `#1C1917`; brass: `#A16207`; warm ivory: `#FAFAF9`.
- Minimum mark size: 16 px digital. Minimum full lockup height: 24 px.
- Clear space: at least one quarter of the mark width.
- Never add a container, gradient, shadow, glow, outline, or additional accent color.
- Use the mono asset when color reproduction is unreliable.

## Regeneration

Run `py -3 tools/generate-nextedge-logo.py --font <path-to-Inter-variable.ttf>` from the repository root. The wordmark is exported as paths, so production rendering does not depend on an installed font.
