# Changelog

Aside is currently alpha software. Privacy- and data-safety-related changes are called out explicitly rather than hidden inside generic maintenance notes.

## 2.0.0-alpha8.6

### Features
- Added an optional, user-initiated email for sharing aggregate usage statistics with the developer.
- Feedback and optional statistics now open as addressed email drafts rather than through the general Android share interface.

### Privacy and security
- Usage statistics are calculated locally and remain reviewable before sending; journal text, hashtag values, mood values, entry dates/times, stable identifiers, and private logs are excluded.
- Preparing journal-derived statistics requires the protected journal to be unlocked and does not add Internet permission, telemetry, or an analytics SDK.

## 2.0.0-alpha8.5

### User experience
- Added local suggestions from previously used hashtags while writing or editing a journal entry, helping keep tag names consistent.

## 2.0.0-alpha8.4

### Reliability
- Fixed the period-summary hashtag test so it validates note-only statistics without depending on presentation order.
- Updated the Android CI actions to supported Node.js 24–based releases while retaining GitHub-hosted Gradle caching.

## 2.0.0-alpha8.3

### Fixes
- Made in-app and exported period summaries count hashtags from note-only check-ins consistently, with an explicit label when a tag has no mood average.

## 2.0.0-alpha8.2

### Fixes
- Kept an in-progress check-in open through screen rotation, including its selected rating and draft note.

## 2.0.0-alpha8 — pre-alpha hardening

### Privacy and security
- Made privacy-reducing setting changes, including Android backup inclusion, go through one fresh-authentication decision when journal protection is active.
- Reworked Android-managed journal backup to serialize a consistent full-fidelity snapshot instead of copying a live SQLite/WAL file family.
- Tightened journal-lock wording to make clear that it is an in-app access gate rather than separate database encryption.
- Added an always-visible **Important privacy limits** section to the Privacy screen.
- Hardened notification reply paths so failed writes do not silently discard typed notes.
- Made legacy notification reply handling respect current lock/privacy settings.
- Neutralized spreadsheet-formula interpretation in exported CSV cells.
- Added a restore file-size safety limit.
- Added a release-build invariant that fails if the final merged manifest requests `android.permission.INTERNET`.
- Added GPLv3 licensing, a security-reporting policy, a public commitments document and verification ledger, and a version-controlled privacy policy.

### Reliability and efficiency
- Fixed Room singleton initialization so concurrent callers cannot create duplicate instances.
- Re-check pause/waking-hour rules when a delayed check-in alarm actually fires.
- Preserved backup-reminder settings when unrelated settings change.
- Only reschedule check-ins/reports whose scheduling inputs actually changed.
- Avoid redundant settings reads and obsolete scheduler cleanup on every check-in.
- Do not keep backup-reminder work scheduled while reminders are disabled.
- Use a database count query for backup-reminder checks instead of loading every journal entry.
- Reuse the shared Room entry stream for Home summaries.
- Move device-local backup-policy file access off the main thread and use atomic file writes.
- Use lifecycle-owned coroutines for Activity work.
- Added safe Compose caching for repeated filtering/formatting work.
- Added Room migration coverage and expanded privacy/scheduler/export tests.

### User experience
- Notifications are silent and non-vibrating by default while remaining visually available; Android/user channel settings remain authoritative.
- Added visible **1 · worst** / **5 · best** guidance to rating controls and equivalent guidance in notifications.
- Added the in-app link to the public, version-controlled privacy policy.

### Distribution
- Changed the permanent Play application ID to `com.nickspeelman.localjournal` before the first Play alpha.
- Added keystore/crash/local-build artifacts to `.gitignore` and removed local build artifacts from the distributable project.

## 2.0.0-alpha8.1

### Fixes
- Fixed the drawing area so input remains within the intended bounds.

### Codex support
- Added AGENTS.md, DESIGN_PRINCIPLES.md, VERSIONING.md
