# Aside design principles

This document describes how Aside should make product, UX, and implementation decisions.

It is not marketing copy. These principles are decision criteria for choosing between otherwise reasonable designs.

Aside is a local-first Android mood journal. It should be useful, private, human, and deliberately undemanding.

## 1. Technology should serve the person

Aside should fit into the user's life rather than asking the user to organize their life around the app.

A technically impressive feature is not automatically a good Aside feature. If it creates more obligation, maintenance, configuration, interruption, or attention than the value it provides, simplify it or leave it out.

The app should help, then get out of the way.

## 2. Attention is a cost

Treat the user's attention as something to conserve.

Prefer:

- interactions that take seconds
- fewer taps and fewer screens
- sensible defaults
- direct paths to the user's likely goal
- automatic cleanup when safe
- returning the user to what they were doing
- passive availability over active interruption
- concise explanations at the point where they matter

Avoid:

- unnecessary confirmations
- repeated prompts
- artificial urgency
- extra navigation for its own sake
- dashboards that exist mainly to encourage checking
- features whose main benefit is another reason to reopen the app

A useful experience should not require frequent use. Aside should remain useful even if the user spends only a few minutes with it in a typical week.

## 3. Utility over engagement

Do not optimize for time spent, daily active use, retention loops, notification opens, streaks, or habitual checking.

Do not add:

- streaks
- feeds
- gamification
- engagement scores
- badges for frequency of use
- rewards for opening the app
- loss aversion for missing entries
- notifications whose purpose is simply to bring the user back

Reminders and reports are acceptable when they directly serve the journal and remain configurable or disableable.

Success means the tool was useful, not that the user spent more time in it.

## 4. Prefer the smaller feature

When two solutions provide similar value, prefer the one with:

- fewer concepts
- fewer settings
- fewer dependencies
- fewer screens
- fewer persistent states
- less maintenance
- less user attention

Do not add complexity merely because it is technically possible.

Before adding a new setting, ask whether a good default can eliminate the need for the setting.

Before adding a new screen, ask whether the task can be completed in an existing flow.

Before adding a new feature, ask whether the same outcome can be achieved by making an existing feature clearer or more flexible.

## 5. Quiet, warm, and human

Aside should feel:

- quiet
- spacious
- understated
- warm
- human
- calm without being sentimental

It should not feel:

- flashy
- gamified
- clinical
- corporate
- productivity-obsessed
- surveillance-oriented
- crowded with metrics

Use visual emphasis sparingly. Not every piece of information needs to compete for prominence.

The established visual direction uses Forum for display/serif typography, Lato for sans-serif typography, and a restrained sea-glass aqua-green palette. Preserve that direction unless the user explicitly changes it.

## 6. Privacy by architecture

Prefer designs in which Aside does not possess data it does not need.

The strongest privacy protection is often not collecting or transmitting information in the first place.

Release builds must continue to omit ordinary Internet access.

Do not introduce networking, remote accounts, telemetry, advertising SDKs, analytics SDKs, or external services merely for convenience.

If a feature can work locally, prefer the local implementation.

If data leaves Aside, it should normally be because the user deliberately exported, backed up, restored, or shared it.

## 7. Make privacy understandable, not theatrical

Privacy controls should communicate their real effect and their limits.

Do not use reassuring language that overstates what a control can guarantee.

Examples:

- An in-app journal lock is an access gate, not separate database encryption.
- Android backup/device transfer is different from an Aside manual backup.
- Notification privacy depends partly on Android and other software outside Aside's control.

Prefer plain explanations over security theater.

When a privacy-reducing change has meaningful consequences, make that consequence understandable before or when the change is made.

## 8. User ownership and reversibility

The journal belongs to the user.

Design features so the user can retain meaningful control over their own data.

Preserve:

- edit and delete capability
- human-readable export where appropriate
- full-fidelity backup and restore
- portability
- recoverability
- the ability to leave the app without losing access to their own information

Avoid lock-in.

Changes to storage, schemas, backup formats, or restore behavior should prioritize preservation of existing user data.

## 9. Good defaults, optional depth

A new user should not need to understand every setting before Aside becomes useful.

Choose thoughtful defaults.

Use progressive disclosure: make the common path simple, while allowing users who care about a specific behavior to customize it.

Privacy presets are an example of this approach: provide understandable starting points while still allowing individual controls to be changed.

Do not force advanced configuration into the primary flow.

## 10. Preserve the user's words and context

Aside may extract structure from journal entries, but it should not casually rewrite or flatten the user's original expression.

For example, a hashtag may be indexed for exploration, but `#work` should remain `#work` in the journal text.

Derived data should supplement the original entry, not replace its meaning.

Editing, display, export, backup, restore, reports, and sharing should preserve user-entered text faithfully unless a transformation is explicit and necessary.

## 11. Insights, not judgment

Reports and analytics should help users notice patterns.

They should not tell users how they ought to feel, shame them for low ratings, reward them for high ratings, or turn mood into a performance metric.

Avoid language that implies:

- a "good" user has a higher mood score
- every downward trend requires correction
- frequent logging is morally or psychologically better
- the user should optimize themselves for the app

Present observations clearly and let the user decide what they mean.

## 12. Notifications must earn the interruption

Every notification should have a clear user-serving reason to exist.

Prefer:

- silent notification channels by default
- user-controlled scheduling
- privacy-conscious lock-screen behavior
- concise actions
- the ability to disable optional notifications

Do not use notifications as engagement prompts.

If an interaction begins from a notification or widget, complete the task with as little disruption as possible. Do not open the full app afterward unless doing so is necessary or the user asked for it.

## 13. Respect context switching

Quick-entry surfaces should be genuinely quick.

A user who records a mood from a home-screen widget, notification, or other lightweight entry point should normally return to what they were doing after saving.

Avoid turning a small interaction into an unsolicited app session.

This principle applies beyond quick entry: finishing the user's requested action should usually end the interaction.

## 14. Safety and reliability beat cleverness

Journal data is difficult or impossible to recreate.

Prefer boring, dependable implementations over clever ones when data integrity is involved.

Be conservative around:

- database migrations
- backup and restore
- deletion
- import/export
- scheduling
- authentication
- privacy settings

Failures should be visible enough to recover from and should not silently discard user-entered information.

## 15. Open and inspectable behavior

Aside's important behavior should remain understandable from the public source.

Avoid opaque dependencies or hidden services for core application behavior.

A user or reviewer should be able to trace important claims—especially privacy claims—to code or documented platform behavior.

If implementation changes make a statement in `COMMITMENTS.md` or `PRIVACY.md` inaccurate, update the document rather than leaving the claim behind.

## 16. Accessibility is part of quiet design

Reducing friction includes making the app usable by people with different visual, motor, and cognitive needs.

Prefer:

- clear labels
- adequate touch targets
- readable contrast
- support for system text sizing where practical
- controls that do not rely only on color
- predictable navigation
- concise language

Do not trade basic accessibility for visual minimalism.

## Decision checklist

When proposing or implementing a user-facing feature, ask:

1. What concrete problem does this solve for the user?
2. Can the same value be delivered with fewer taps, screens, settings, or interruptions?
3. Does it ask for attention that is not necessary?
4. Does it create a reason to use Aside more often without creating equivalent user value?
5. Can it remain entirely local?
6. Does it collect, expose, transform, or retain more data than necessary?
7. Does it preserve the user's words and data ownership?
8. Are the privacy and security implications described accurately?
9. Does it fit the quiet, warm, understated visual and interaction style?
10. Does it preserve accessibility?
11. Could failure cause data loss or silently discard user input?
12. Would the feature still make sense if success were measured by usefulness rather than engagement?

If a design performs poorly against these questions, revise it before implementation.

## Relationship to other project documents

`DESIGN_PRINCIPLES.md` explains how Aside should make product and UX decisions.

`COMMITMENTS.md` contains the promises Aside makes to users and the philosophy behind them.

`PRIVACY.md` describes specific data and privacy behavior.

`AGENTS.md` tells coding agents how to work in the repository and when to consult these documents.

These documents should reinforce one another. If a proposed change conflicts with a commitment or principle, the conflict should be made explicit rather than silently implemented.
