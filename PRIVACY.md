---
layout: default
title: Aside Privacy Policy
permalink: /privacy/
---

# Aside Privacy Policy

**Effective date: October 7, 2026**  
**Last updated: October 7, 2026**

Aside is developed and maintained by Nick Speelman. Aside is designed so that the developer does not need to receive your journal data in order for the app to work. This privacy policy is maintained in the same public source repository as Aside so changes to it can be reviewed through the repository's commit history.

## The short version

- Aside has no account system, advertising, analytics, tracking, or crash-reporting service.
- Aside's release build does not request Android's `INTERNET` permission, so the app itself cannot make ordinary network connections.
- Journal entries and settings are stored on your device.
- Depending on the privacy options you choose, Android may copy journal data through Android-managed cloud backup or device-to-device transfer.
- Manual backups and exports are readable files. If you save or share them outside Aside, the destination you choose controls those copies.
- Aside does not automatically send journal entries or usage information to the developer.

## Information Aside stores

Aside stores the information needed to provide the journal on your device. This can include mood ratings, journal notes, hashtags, timestamps, app preferences, privacy settings, report settings, and local backup-status information.

Aside does not require an Aside account and does not maintain a server-side profile for you.

## Network access, analytics, advertising, and tracking

Aside's release build does **not** request `android.permission.INTERNET`. The release build is configured to fail if that permission appears in Android's final merged manifest, including if a dependency were to add it.

Aside does not include advertising SDKs, analytics/tracking SDKs, or an automatic crash-reporting service.

Some actions intentionally hand information to another app that you choose. For example, opening this privacy policy launches a web browser, sending beta feedback launches an email/share app, and sharing an export launches Android's share interface. Those other apps and services have their own privacy practices and network permissions.

## Journal data and device storage

Aside stores journal entries in a local database on your device. The optional in-app journal lock controls whether Aside will display protected journal content without fresh device authentication.

**The journal database is not encrypted with a separate Aside password or separate Aside encryption key.** The journal lock is intended to prevent casual access through the app by someone holding an already-unlocked phone; it is not designed to protect data from a rooted or compromised device, privileged forensic access, or compromise of Android itself.

## Android backup and device transfer

Aside lets you control whether journal contents are eligible for Android-managed cloud backup and device-to-device transfer where Android provides those controls. Ordinary app settings may still be eligible for Android-managed restoration.

Android, the device manufacturer, and the user's account/backup configuration ultimately control whether a system backup or transfer occurs, where it is stored, how long it is retained, and when it is deleted. Aside's developer does not receive those Android-managed backups.

On Android versions where cloud backup and device transfer cannot be reliably separated, Aside treats the journal setting as applying to both together and explains that limitation in the app.

## Manual backups, CSV exports, reports, and sharing

Aside can create manual full-fidelity JSON backups, CSV journal exports, and report/chart files. These outputs are intentionally portable and readable rather than locked to Aside.

That also means they are **not encrypted by Aside**. When you choose a save location or share target, the file leaves Aside's control. A cloud-synced folder, email provider, messaging app, file-sharing service, or other destination may upload, retain, scan, or otherwise process the copy according to its own policies.

Aside warns about this before relevant storage/export actions.

## Notifications and connected devices

Aside provides privacy controls for lock-screen notification content and whether Android should bridge notifications to connected devices. Aside uses Android's notification-visibility and local-only controls, and it checks device lock state for sensitive notification actions.

Android's notification settings, lock-screen behavior, connected-device services, and third-party notification-access apps remain outside Aside's full control. Aside cannot guarantee that every system component or third-party bridge will honor every request identically.

Aside's notification channels are configured to be silent by default. Users can change notification behavior in Android settings.

## Beta feedback and support

If you choose **Send beta feedback**, Aside prepares a message containing limited technical information such as the Aside version/build type, Android version/API level, device manufacturer/model, and whether notifications are permitted. The message is opened in an external email/share app so you can review and edit it before deciding whether to send it.

Aside does not automatically include journal entries, notes, mood ratings/history, hashtags, backups, exported reports, or private logs in beta feedback.

If you contact support yourself, information you include in that message is handled by the email/provider services involved in sending and receiving it.

## Data retention and deletion

Journal data remains on the device until you delete entries, delete all journal data, clear Aside's app data, uninstall the app, or replace the journal through a restore operation.

Manual backup/export files remain wherever you chose to save or share them until they are deleted there. Android-managed backups or transferred copies are retained according to Android, account-provider, or device-manufacturer policies rather than Aside's.

Aside does not maintain a remote copy that the developer can delete on your behalf because Aside does not receive your journal database in the first place.

## Changes to this policy

Changes to this policy are committed alongside the Aside source code. You can review the current file and its full revision history in the public repository. Material changes will also update the **Last updated** date above.

Policy source: [PRIVACY.md](https://github.com/nickspeelman/aside/blob/main/PRIVACY.md)  
Revision history: [PRIVACY.md history](https://github.com/nickspeelman/aside/commits/main/PRIVACY.md)

## Contact

For privacy, security, or support questions, contact **asidehelp@nickspeelman.com**.
