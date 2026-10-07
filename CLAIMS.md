# Aside claims ledger

This file connects Aside's privacy/product claims to the code or process that supports them, and states the important limits. It is meant to make the project's promises inspectable rather than asking users to take them on faith.

| Claim | What supports it | Important limits |
| --- | --- | --- |
| **Aside itself has no ordinary Internet access.** | Release builds fail if the final merged Android manifest contains `android.permission.INTERNET`; see `VerifyNoInternetPermissionTask` in `app/build.gradle.kts`. | Android itself may perform cloud backup/device transfer when the user permits it. Apps the user deliberately opens for email, sharing, storage, or browsing can use their own network access. |
| **No account is required.** | There is no Aside account/login system or remote backend. Journal data is stored locally in Room. | Device/Google accounts may still be involved in Android-managed backup or Play Store installation; those are outside Aside. |
| **Aside contains no ads or analytics/tracking SDK.** | No advertising or analytics SDK is included in the app dependencies, and the app lacks `INTERNET`. | Local, on-device calculations live under the `analytics` package; that package name means journal statistics, not telemetry. |
| **Aside has no in-app subscription or advertising monetization code.** | There is no BillingClient/subscription implementation or ad SDK in the app. | Google Play controls the store listing and purchase price. The official distribution model is intended to be a one-time purchase, but that store-side fact is not enforced by the APK itself. |
| **Journal entries are stored locally by Aside.** | Entries are stored in the local Room database (`MoodDatabase`). | Android-managed backup/device transfer can copy journal data if enabled. Manual backups/exports can be saved to locations that sync elsewhere. |
| **Journal unlock prevents casual in-app viewing.** | `JournalContentGate` and the authentication flow require device authentication before protected journal screens are shown. | This is an app-level access gate, **not separate database encryption**. It is not designed to protect against a rooted/compromised device or forensic extraction. |
| **"Maximum privacy" is Aside's strongest available configuration.** | The preset enables the strictest combination of journal lock, lock-screen, Recents/screenshot, connected-device, and Android-backup controls exposed by Aside. | It does not create a separate encryption key/password and cannot override the operating system or malicious/privileged software. |
| **Privacy-lowering setting changes require fresh authentication when journal protection is active.** | `PrivacyChangePolicy` evaluates the complete privacy settings plus Android backup policy before changes are committed. | If journal protection is disabled, there is no fresh-auth gate because the journal is already available to someone holding the unlocked device. |
| **Notifications are quiet by default.** | Aside's notification channels disable sound and vibration by default in `NotificationHelper`. | Android notification settings and Do Not Disturb remain authoritative; users can change channel behavior. |
| **Lock-screen/connected-device privacy uses Android's available controls.** | Notification visibility, local-only flags, and lock-state checks are applied in `NotificationHelper` and notification receivers. | Android and third-party notification-access/bridge apps have final control; `localOnly` is a request to the system, not an absolute guarantee. |
| **Manual backups and exports are portable and user-controlled.** | Aside can create full-fidelity JSON backups and CSV exports using documented, human-readable formats. | These files are plaintext/readable. Once saved or shared outside Aside, copies are controlled by the chosen storage/share destination. |
| **Aside does not silently send beta feedback.** | The beta-feedback action opens the user's email/share app with a reviewable draft. | If the user chooses to send it, the selected external app/provider handles that message. |
| **Privacy-policy changes are publicly reviewable.** | `PRIVACY.md` is version-controlled with the application source and published through GitHub Pages. | Git history shows policy text changes from the point the public repository is available. |

## Verification notes

The strongest build-time invariant is the no-network check. Run:

```bash
./gradlew :app:verifyReleaseNoInternetPermission
```

A technically inclined user can also inspect the manifest/permissions of the APK installed on their device. Source-level checks and binary inspection are complementary: the repository shows what the project is designed to build, while inspecting an installed APK shows what that particular binary requests.
