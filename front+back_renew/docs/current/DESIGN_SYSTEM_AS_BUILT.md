# Design system as built

- Status: Current
- Verified HEAD: `bea5d38f15209d488a95cc1d156c17dfead30e1e`
- Verified Date: 2026-07-24
- Owners: Frontend/Design
- Related Source: `frontEnd/src/shared/styles`, `frontEnd/src/shared/ui`
- Supersedes: Phase 4 implementation snapshot as current guidance
- Known Limitations: no Storybook and no automated visual regression

The current runtime uses global design tokens for color, spacing, radius, type, elevation and responsive behavior. Brand mint is visually distinct from semantic success. Shared primitives cover button, input, card, badge, table/state and analysis/job feedback patterns.

AppShell supplies global navigation and responsive sidebar behavior; ProjectLayout supplies project context and stage navigation. Pages consistently render loading, empty, error, queued/running and success states instead of fake result data. Evidence/provenance/disclaimer patterns are domain components.

Accessibility controls include semantic controls, visible focus, labeled inputs, keyboard navigation, reasonable touch targets and reduced-motion rules. Contrast and 40/44 px target compliance were inspected in CSS but not certified with a full device/screen-reader lab.

New work must use shared tokens/primitives first, preserve server-backed state semantics, include all states, support direct entry, and avoid one-off colors or inline layout systems.
