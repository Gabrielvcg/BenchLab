# Dashboard usability release

Version 0.0.19 adds a dedicated `workbench.css` presentation layer: neutral white/slate surfaces, restrained blue interactive accents, readable selected states, and monospaced table values. It removes the decorative green/red background without changing chart series colours, benchmark parameters or data. Desktop and mobile browser tests cover the same workflow.

Version 0.0.18 replaces the sidebar with a wide setup card followed by results. Secondary metric/history controls and advanced execution settings are collapsed. Protected API 401 responses clear the local session and request sign-in; this handles rejected tokens without assuming the underlying reason (expiry, signing-key change or another authentication failure). Login failures do not trigger the protected-session handler.

This release is based on the current published main branch, not the divergent experimental worktree. It changes presentation only: run action and workload count above configuration, collapsible advanced settings, compact empty states, first-run guidance, keyboard focus and responsive layout.

The CPU/wall-time selector, measured samples, warmup settings, workload sizes, API contracts, plotting and worker behavior are preserved. The UI does not introduce the experimental worktree's different measurement model. An 87-container quick preset still represents 87 invocations; this release does not silently reduce samples for apparent speed.

Release through the existing CI/CD workflow: push the tested main revision, then manually dispatch CI/CD on main. Verification and image builds must pass before deployment. No direct VPS code copying or database migration is required by this UI change.

Run `npm ci`, `npm test`, `npm run build` and `npm run test:ui` in `benchlab-web`. The optional UI suite requires installed Chrome; it starts its own local server and mocks every API call. It checks desktop/mobile layout, metric switching, unchanged iteration defaults and an actionable admission error without submitting production work. Screenshots remain ignored under `test-results`.
