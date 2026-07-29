# CSS audit

- Status: Current
- Verified HEAD: `bea5d38f15209d488a95cc1d156c17dfead30e1e`
- Verified Date: 2026-07-24
- Owners: Frontend
- Related Source: `frontEnd/src/**/*.css`
- Supersedes: none
- Known Limitations: no unused-selector coverage tool was introduced

Runtime CSS is divided among global tokens, shared UI, layouts/pages and feature-local styles. The largest runtime stylesheet is `shared/ui/ui.css` (469 lines), followed by document styles (353). This is manageable but indicates growing shared-component breadth.

Legacy unrouted CSS dominates the largest files: `page/VirtualMarket.css` (1,070), `page/Admin.css` (676), root `App.css` (428), and `Head.css` (308). They are not imported by the runtime graph, but were kept because Phase 4 migration records mark their interaction patterns as design reference until Phase 11.

Findings:

- no new hardcoded localhost was found in runtime source;
- global tokens are the preferred source for brand/semantic color;
- feature CSS repeats card/grid/responsive patterns that could later move to shared primitives;
- inline styles are limited but should not become a parallel token system;
- reduced-motion/focus rules exist, while full contrast/device verification remains manual debt.

No selector was deleted: exact runtime, dynamic class and historical-reference safety could not all be proven.
