# Dashboard usability release

This release is based on the current published main branch, not the divergent experimental worktree. It changes presentation only: run action and workload count above configuration, collapsible advanced settings, compact empty states, first-run guidance, keyboard focus and responsive layout.

The CPU/wall-time selector, measured samples, warmup settings, workload sizes, API contracts, plotting and worker behavior are preserved. The UI does not introduce the experimental worktree's different measurement model. An 87-container quick preset still represents 87 invocations; this release does not silently reduce samples for apparent speed.

Release through the existing CI/CD workflow: push the tested main revision, then manually dispatch CI/CD on main. Verification and image builds must pass before deployment. No direct VPS code copying or database migration is required by this UI change.

Run `npm ci`, `npm test`, `npm run build` and `npm run test:ui` in `benchlab-web`. The optional UI suite requires installed Chrome; it starts its own local server and mocks every API call. It checks desktop/mobile layout, metric switching, unchanged iteration defaults and an actionable admission error without submitting production work. Screenshots remain ignored under `test-results`.
