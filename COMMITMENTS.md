# Aside commitments

A lot of modern software is built around taking more: more of your attention, more of your data, and more money over time.

Aside is deliberately trying to do something smaller.

## We don't want your attention

Aside should be useful without becoming somewhere you spend time.

There are no streaks to maintain, feeds to scroll, engagement goals to hit, or notifications whose purpose is simply to bring you back. We don't measure success by daily active users, session length, notification opens, or how often you return.

A useful interaction with Aside should take seconds. A useful week might involve only a few minutes of total attention.

If Aside helps you and then disappears into the background, it's doing its job.

We will not add features whose primary purpose is to increase time spent in Aside or to manufacture engagement for its own sake.

## We don't want a subscription

Aside is a tool, not an ongoing service you need to rent.

The official app is intended to be something you buy once and continue using. We don't want to create artificial tiers, recurring payments, or features whose purpose is to keep proving that the app deserves another monthly charge.

You bought the tool. It should keep being your tool.

## We don't want to keep selling you pieces of the app

Aside does not use microtransactions or in-app purchases to unlock features, remove artificial limits, buy cosmetic upgrades, purchase additional reports, or regain access to parts of the journal you've already paid for.

We don't want to sell you a basic product and then spend the rest of the relationship looking for opportunities to charge you again.

**The price of Aside is the price of Aside.**

## We don't want to advertise to you

You paid for the software. That's enough.

Aside does not need to sell you anything else, show you somebody else's advertisements, or turn your attention into a product for advertisers.

There are no ads, sponsored messages, promoted content, or advertising trackers.

Purchasing Aside should be the end of the economic relationship unless you deliberately choose otherwise. We don't need you to keep generating revenue every time you use something you've already bought.

## We don't want possession of your data

Your journal exists so that it can be useful to you, not to us.

This is about more than protecting data from a breach. It's about respecting the boundary between a person using a tool and the person who made it.

If we don't collect your journal or build a behavioral profile about you, we can't sell it, rent it, mine it for advertising, use it to train models, combine it with other datasets, or quietly find a new business model for it later.

So Aside is designed around a simpler principle:

**If we don't need your data, we shouldn't have it.**

Aside does not require an account, does not contain third-party analytics or advertising trackers, and does not request ordinary Internet access. Your journal is stored for you, on your device, subject to the backup and export choices you make.

Your data should be something Aside works with on your behalf, not something Aside acquires from you.

## We don't want you to have to take our word for it

Aside is open source because trust should not depend on promises that can't be checked.

The source code for Aside is public under the GNU General Public License v3. The code that implements the app's behavior, its build configuration, its privacy protections, and its dependencies is available for anyone to inspect.

We will not ship a version of Aside whose meaningful application logic depends on private source code that users cannot review.

That means you don't have to simply trust us when we say Aside has no advertising, doesn't contain analytics, doesn't request Internet access, or stores your journal locally. Those claims can be compared against the code.

Official releases will be associated with public source versions so changes to both the software and these commitments remain visible over time.

Open source does not mean every component of Android itself or every third-party dependency is maintained in this repository, and it does not by itself prove that a distributed binary is byte-for-byte reproducible from the source. Where those limits matter, we'll say so plainly.

**There shouldn't be a secret version of Aside hidden behind the version you can inspect.**

## We want Aside to fit quietly into your life

Technology should serve people, then get out of the way.

Aside isn't meant to become a habit you have to maintain. There is no penalty for ignoring it, no reward for opening it more often, and no expectation that you organize your life around the app.

Check in when it's useful. Ignore it when it isn't. Come back after a week or a month and your journal will still be there.

The goal is not to make Aside an important part of your life.

The goal is to make a small tool that is useful when you need it and quiet when you don't.

---

These aren't only descriptions of the current version of Aside. They are commitments about the direction of the project.

This file lives in Aside's public source repository so those commitments can be examined alongside the code—and so any future change to them remains visible in the project's public history.

# How Aside currently keeps these commitments

The table below connects the project's commitments to the code or process that supports them and states important limits. The goal is to make these promises inspectable rather than asking users to take them on faith.

| Commitment | What supports it | Important limits |
| --- | --- | --- |
| **Aside itself has no ordinary Internet access.** | Release builds fail if the final merged Android manifest contains `android.permission.INTERNET`; see `VerifyNoInternetPermissionTask` in `app/build.gradle.kts`. | Android itself may perform cloud backup/device transfer when the user permits it. Apps the user deliberately opens for email, sharing, storage, or browsing can use their own network access. |
| **Aside does not seek engagement for its own sake.** | No streaks, feeds, social features, engagement metrics, or attention-seeking notification mechanics. Notifications are silent by default and check-ins are designed to be brief. | Aside still offers reminders and reports because they serve the journal; users can configure or disable them. |
| **No account is required.** | There is no Aside account/login system or remote backend. Journal data is stored locally in Room. | Device/Google accounts may still be involved in Android-managed backup or Play Store installation; those are outside Aside. |
| **No ads, sponsored content, or advertising trackers.** | No advertising SDK is included in the app dependencies, the UI contains no ad placements, and the app lacks `INTERNET`. | This commitment applies to the official Aside app. Google Play itself is a separate service outside the app. |
| **No analytics/tracking SDK.** | No analytics SDK is included in app dependencies, and the app lacks `INTERNET`. | Local, on-device calculations live under the `analytics` package; that package name means journal statistics, not telemetry. |
| **No subscriptions, microtransactions, or in-app feature purchases.** | There is no BillingClient/subscription/in-app-purchase implementation in the app. | Google Play controls the initial store purchase and price. The official distribution model is a one-time purchase; that store-side fact is not enforced by the APK itself. |
| **Aside's meaningful application logic is open for inspection.** | The Aside source, build configuration, dependency declarations, privacy controls, and project documents are published under GPLv3 in the public repository; releases are intended to correspond to public source versions. | Android itself and third-party dependencies are separate projects. Public source enables inspection but does not, by itself, prove that a distributed binary is byte-for-byte reproducible; reproducible builds are a separate verification goal. |
| **Aside does not collect journal data for sale, advertising, profiling, or model training.** | Aside has no backend/account system, no trackers or analytics SDKs, and no ordinary Internet permission. Journal data is processed locally. | Data the user deliberately exports, backs up, or shares leaves Aside's local storage and is then governed by the chosen destination. |
| **Journal entries are stored locally by Aside.** | Entries are stored in the local Room database (`MoodDatabase`). | Android-managed backup/device transfer can copy journal data if enabled. Manual backups/exports can be saved to locations that sync elsewhere. |
| **Journal unlock prevents casual in-app viewing.** | `JournalContentGate` and the authentication flow require device authentication before protected journal screens are shown. | This is an app-level access gate, **not separate database encryption**. It is not designed to protect against a rooted/compromised device or forensic extraction. |
| **"Maximum privacy" is Aside's strongest available configuration.** | The preset enables the strictest combination of journal lock, lock-screen, Recents/screenshot, connected-device, and Android-backup controls exposed by Aside. | It does not create a separate encryption key/password and cannot override the operating system or malicious/privileged software. |
| **Privacy-lowering setting changes require fresh authentication when journal protection is active.** | `PrivacyChangePolicy` evaluates the complete privacy settings plus Android backup policy before changes are committed. | If journal protection is disabled, there is no fresh-auth gate because the journal is already available to someone holding the unlocked device. |
| **Notifications are quiet by default.** | Aside's notification channels disable sound and vibration by default in `NotificationHelper`. | Android notification settings and Do Not Disturb remain authoritative; users can change channel behavior. |
| **Lock-screen/connected-device privacy uses Android's available controls.** | Notification visibility, local-only flags, and lock-state checks are applied in `NotificationHelper` and notification receivers. | Android and third-party notification-access/bridge apps have final control; `localOnly` is a request to the system, not an absolute guarantee. |
| **Manual backups and exports are portable and user-controlled.** | Aside can create full-fidelity JSON backups and CSV exports using documented, human-readable formats. | These files are plaintext/readable. Once saved or shared outside Aside, copies are controlled by the chosen storage/share destination. |
| **Aside does not silently send beta feedback.** | The beta-feedback action opens the user's email/share app with a reviewable draft. | If the user chooses to send it, the selected external app/provider handles that message. |
| **Privacy-policy and commitment changes are publicly reviewable.** | `PRIVACY.md` and `COMMITMENTS.md` are version-controlled with the application source. The privacy policy is also published through GitHub Pages. | Git history shows changes from the point the public repository is available. |

## Verification notes

The strongest build-time invariant is the no-network check. Run:

```bash
./gradlew :app:verifyReleaseNoInternetPermission
```

A technically inclined user can also inspect the manifest/permissions of the APK installed on their device. Source-level checks and binary inspection are complementary: the repository shows what the project is designed to build, while inspecting an installed APK shows what that particular binary requests.
