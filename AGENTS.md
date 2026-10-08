# Aside — Codex repository instructions

## Project purpose

Aside is a local-first Android mood journal designed to demand as little attention as possible.

The product should feel like a quiet tool that helps, then gets out of the way.

Treat `COMMITMENTS.md` and `DESIGN_PRINCIPLES.md` as authoritative project guidance, not optional background reading.

If a requested change would materially conflict with a documented commitment or design principle, do not silently work around it. Call out the conflict and ask whether the user intends to change the underlying commitment/principle.

## Non-negotiable project constraints

- Preserve the permanent Android application ID: `com.nickspeelman.localjournal`.
- Release builds must not request `android.permission.INTERNET`.
- Do not add advertising, sponsored content, analytics/tracking SDKs, subscriptions, microtransactions, or in-app feature purchases.
- Do not add streaks, feeds, engagement goals, attention-seeking notifications, or mechanics whose main purpose is to increase app usage.
- Keep journal data local to the device unless the user deliberately exports, backs up, restores, or shares it.
- Do not weaken privacy or security behavior casually. Be explicit about tradeoffs when a requested change affects privacy.
- Preserve user ownership and portability of journal data.
- Keep privacy claims technically accurate and conservative. Do not describe an app-level lock as database encryption.

## Project documents and when to read them

Read the documents relevant to the task rather than loading every document for every small change.

- `DESIGN_PRINCIPLES.md` — read before changing user-facing behavior, navigation, UI structure, onboarding, notifications, widgets, reports, analytics presentation, settings, or other UX/product decisions.
- `COMMITMENTS.md` — read before changes that could affect privacy, networking, data collection, business model, advertising, analytics/tracking, engagement mechanics, payments, open-code expectations, or the project's overall product philosophy.
- `PRIVACY.md` — read before changing data storage, backup, restore, export, sharing, notification privacy, authentication/privacy controls, screenshots/Recents behavior, or any user-facing privacy claim.
- `SECURITY.md` — read when changing security-reporting behavior or security-related repository guidance.
- `README.md` — repository overview, build information, and high-level privacy/build invariants.
- `CHANGELOG.md` — release history.
- `VERSIONING.md` — required end-of-task versioning procedure.

When a code change makes a statement in `COMMITMENTS.md`, `DESIGN_PRINCIPLES.md`, or `PRIVACY.md` inaccurate, update the relevant documentation in the same task.

Do not weaken or rewrite a public commitment merely to make an implementation easier. If the requested feature truly requires changing a commitment, surface that decision to the user.

## End-of-task release preparation

Assume that every completed task that leaves repository changes will become the next released build.

At the END of the task, after the requested work is complete and any implementation/debugging iterations are finished:

1. Follow `VERSIONING.md`.
2. Increment `versionCode` exactly once.
3. Update `versionName` exactly once.
4. Add the matching release entry to `CHANGELOG.md`.
5. Run the relevant verification/build checks.
6. In the final handoff, state the resulting `versionName` and `versionCode`.

Do not bump the version repeatedly while fixing errors or iterating on the same task. One user-requested task = one release-preparation bump.

If the task makes no repository changes, do not bump the version.

## Git and release boundary

Codex prepares the working tree but does not perform the user's release-control actions.

Do NOT:

- create Git commits
- push to GitHub
- create or push Git tags
- upload builds to Google Play
- change Play Console settings

The user reviews the work and handles those actions.

## Verification

Run tests/checks appropriate to the change.

For broad application changes or whenever practical, run:

`./gradlew :app:testDebugUnitTest :app:verifyReleaseNoInternetPermission --stacktrace`

A task is not complete merely because the code compiles. Check the behavior affected by the task and preserve existing privacy invariants.

When a task touches backup/restore, migrations, privacy controls, notification behavior, scheduling, or user-entered data, favor targeted regression checks in addition to compilation.

## Implementation priorities

When several implementations are possible, use `DESIGN_PRINCIPLES.md` as the decision framework.

In general, prefer the implementation that:

1. preserves privacy and local-first behavior,
2. demands less user attention,
3. keeps behavior understandable and inspectable,
4. avoids unnecessary dependencies and complexity,
5. preserves existing user data and backup/restore compatibility,
6. maintains accessibility,
7. minimizes the chance of silent data loss.

Do not introduce a network dependency, telemetry, external service, new persistent setting, or additional interruption merely for convenience.
