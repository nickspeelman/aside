# Implementation Plan - Offline Mood Journal

Build a privacy-focused, offline-only mood journal that allows users to record their mood directly from notifications using a specific text format.

## User Review Required

> [!IMPORTANT]
> **Privacy & Offline Policy**: The app will not include any networking permissions or libraries. All data remains on the device.
> **Notification Permissions**: On Android 13+, the app will require explicit notification permission to function as intended.

## Proposed Changes

### Dependencies & Setup

#### [MODIFY] [libs.versions.toml](file:///C:/Users/nspeelman.SEIU/AndroidStudioProjects/LocalJournal/gradle/libs.versions.toml)
* Add Room, WorkManager, and Navigation Compose.

#### [MODIFY] [build.gradle.kts (app)](file:///C:/Users/nspeelman.SEIU/AndroidStudioProjects/LocalJournal/app/build.gradle.kts)
* Apply Room and KSP plugins.
* Add implementation dependencies.

---

### Data Layer (Local Storage)

#### [NEW] [MoodEntry.kt](file:///C:/Users/nspeelman.SEIU/AndroidStudioProjects/LocalJournal/app/src/main/java/com/nickspeelman/localjournal/data/MoodEntry.kt)
* Data class representing a journal entry: `id`, `rating` (Int), `note` (String), `hashtags` (List<String>), `timestamp` (Long).

#### [NEW] [MoodDao.kt](file:///C:/Users/nspeelman.SEIU/AndroidStudioProjects/LocalJournal/app/src/main/java/com/nickspeelman/localjournal/data/MoodDao.kt)
* Room DAO for CRUD operations on `MoodEntry`.

#### [NEW] [MoodDatabase.kt](file:///C:/Users/nspeelman.SEIU/AndroidStudioProjects/LocalJournal/app/src/main/java/com/nickspeelman/localjournal/data/MoodDatabase.kt)
* Room database configuration.

#### [NEW] [MoodRepository.kt](file:///C:/Users/nspeelman.SEIU/AndroidStudioProjects/LocalJournal/app/src/main/java/com/nickspeelman/localjournal/data/MoodRepository.kt)
* Abstract the data source for the ViewModel and Workers.

---

### Notification & Background Logic

#### [NEW] [NotificationHelper.kt](file:///C:/Users/nspeelman.SEIU/AndroidStudioProjects/LocalJournal/app/src/main/java/com/nickspeelman/localjournal/notifications/NotificationHelper.kt)
* Utility to create notification channels and build notifications with `RemoteInput` for Direct Reply.

#### [NEW] [MoodReplyReceiver.kt](file:///C:/Users/nspeelman.SEIU/AndroidStudioProjects/LocalJournal/app/src/main/java/com/nickspeelman/localjournal/notifications/MoodReplyReceiver.kt)
* BroadcastReceiver that triggers when a user replies from a notification.
* Parses the text input: `{Rating} {Optional Note} {Optional Hashtags}`.
* Saves the entry to the Room database.

#### [NEW] [RandomPromptWorker.kt](file:///C:/Users/nspeelman.SEIU/AndroidStudioProjects/LocalJournal/app/src/main/java/com/nickspeelman/localjournal/workers/RandomPromptWorker.kt)
* WorkManager worker that schedules itself at random intervals (e.g., 2-4 times a day) and triggers a notification.

#### [NEW] [WeeklySummaryWorker.kt](file:///C:/Users/nspeelman.SEIU/AndroidStudioProjects/LocalJournal/app/src/main/java/com/nickspeelman/localjournal/workers/WeeklySummaryWorker.kt)
* Periodic worker that runs once a week to calculate mood statistics and show a summary notification.

---

### UI Layer

#### [NEW] [MoodViewModel.kt](file:///C:/Users/nspeelman.SEIU/AndroidStudioProjects/LocalJournal/app/src/main/java/com/nickspeelman/localjournal/ui/MoodViewModel.kt)
* Manages the UI state, exposing the list of entries and summary data.

#### [NEW] [HistoryScreen.kt](file:///C:/Users/nspeelman.SEIU/AndroidStudioProjects/LocalJournal/app/src/main/java/com/nickspeelman/localjournal/ui/HistoryScreen.kt)
* A list of previous mood entries with date headers.

#### [NEW] [SummaryScreen.kt](file:///C:/Users/nspeelman.SEIU/AndroidStudioProjects/LocalJournal/app/src/main/java/com/nickspeelman/localjournal/ui/SummaryScreen.kt)
* Visual summaries (average mood, most used hashtags, entry frequency).

#### [MODIFY] [MainActivity.kt](file:///C:/Users/nspeelman.SEIU/AndroidStudioProjects/LocalJournal/app/src/main/java/com/nickspeelman/localjournal/MainActivity.kt)
* Set up Navigation between History and Summary.
* Request notification permissions.
* Initialize WorkManager schedules.

## Verification Plan

### Automated Tests
* Unit tests for the response parser logic.
* Room database integration tests.

### Manual Verification
* Deploy to an emulator/device.
* Manually trigger the prompt notification via a debug button or by shortening the random interval.
* Reply to the notification and verify the entry appears in the app history.
* Verify no network activity occurs (via Profiler).
