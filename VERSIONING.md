# Aside versioning procedure

This file defines what Codex must do at the end of every completed task that changes repository files.

The user will review and commit the changes. Codex must prepare the repository so that the commit can correspond to a distinct released build.

## Files to update

Version information lives in:

`app/build.gradle.kts`

Every completed repository-changing task must also update:

`CHANGELOG.md`

## versionCode

At the end of the task, increment `versionCode` by exactly 1.

Example:

`25 -> 26 -> 27 -> 28`

Rules:

- Never reuse a `versionCode`.
- Never decrease it.
- Bump it only once per user-requested task, after the implementation is complete.
- Do not create extra bumps for intermediate compile errors, debugging attempts, or follow-up edits that are part of the same task.

## versionName

Every completed repository-changing task must receive a unique `versionName`.

Unless the user explicitly specifies a new milestone, treat the task as a follow-up release to the current version.

Examples:

Current:

`2.0.0-alpha8`

Next ordinary task:

`2.0.0-alpha8.1`

Next:

`2.0.0-alpha8.2`

If the current version already has a numeric follow-up suffix, increment that suffix.

Example:

`2.0.0-alpha9.3 -> 2.0.0-alpha9.4`

Only move to a new milestone such as `alpha9`, `beta1`, or `2.0.0` when the user explicitly asks to do so.

Examples of user-directed milestone changes:

`2.0.0-alpha8.4 -> 2.0.0-alpha9`

`2.0.0-alpha12.2 -> 2.0.0-beta1`

Do not independently decide that the project is ready for beta or stable release.

## CHANGELOG.md

Add a new entry matching the new `versionName`.

The changelog describes the released result, not every implementation step.

For a small fix, keep the entry short.

Example:

    ## 2.0.0-alpha8.1

    ### Fixes
    - Fixed the drawing area so input remains within the intended bounds.

For larger tasks, use whichever sections are useful, such as:

- Features
- User experience
- Privacy and security
- Reliability
- Fixes
- Distribution

Privacy- and data-safety-related changes should be called out explicitly rather than hidden inside generic maintenance notes.

## When to perform the bump

Do the version and changelog updates at the END of the task.

Correct sequence:

1. Implement the requested change.
2. Fix any errors discovered while implementing it.
3. Run relevant tests/checks.
4. When the work is ready for handoff, increment `versionCode` once.
5. Set the next `versionName` once.
6. Add one matching changelog entry.
7. Run any final verification affected by the metadata changes.
8. Tell the user the resulting version and version code.

Do not create a new version for every internal attempt.

## Actions Codex must not perform

Version preparation does not include source-control or Play Store actions.

Codex must not:

- commit
- push
- tag
- upload an AAB
- publish or promote a Google Play release

Those actions belong to the user.

## Final handoff

The completion summary should include a line in this form:

`Prepared Aside 2.0.0-alpha8.1 (versionCode 26).`

If relevant tests or checks could not be run, say so clearly.
