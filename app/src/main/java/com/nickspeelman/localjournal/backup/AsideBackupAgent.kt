package com.nickspeelman.localjournal.backup

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.Build
import android.os.ParcelFileDescriptor
import com.nickspeelman.localjournal.data.AsideBackupCodec
import com.nickspeelman.localjournal.data.MoodDatabase
import com.nickspeelman.localjournal.notifications.BackupReminderScheduler
import com.nickspeelman.localjournal.notifications.MonthlyReportScheduler
import com.nickspeelman.localjournal.notifications.RandomPromptAlarmScheduler
import com.nickspeelman.localjournal.notifications.WeeklyReportScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Full-data backup agent with an allow-list. Ordinary settings remain portable, while journal
 * contents are emitted only when the device-local policy permits the current transport.
 */
class AsideBackupAgent : BackupAgent() {
    // Aside uses full-data backup (android:fullBackupOnly="true"). BackupAgent still requires
    // implementations of the legacy key/value callbacks, so keep them as deliberate no-ops.
    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?
    ) = Unit

    override fun onRestore(
        data: BackupDataInput?,
        appVersionCode: Int,
        newState: ParcelFileDescriptor?
    ) = Unit

    override fun onFullBackup(data: FullBackupDataOutput) {
        // Portable, non-journal configuration. Never call super: this is an intentional allow-list.
        File(filesDir, "datastore/settings.preferences_pb")
            .takeIf { file: File -> file.exists() }
            ?.let { file: File -> fullBackupFile(file, data) }

        val snapshotFile = File(filesDir, JOURNAL_SNAPSHOT_FILE)
        snapshotFile.delete() // clean up a stale file left by an interrupted earlier backup

        val policy = BackupPolicyStore(this).read()
        val isD2d = Build.VERSION.SDK_INT >= 28 &&
            (data.transportFlags and BackupAgent.FLAG_DEVICE_TO_DEVICE_TRANSFER) != 0
        val includeJournal = if (Build.VERSION.SDK_INT < 28) {
            policy.cloudJournalIncluded && policy.deviceTransferJournalIncluded
        } else if (isD2d) {
            policy.deviceTransferJournalIncluded
        } else {
            policy.cloudJournalIncluded
        }
        if (!includeJournal) return

        // Do not copy a live Room/WAL file family. Serialize one transactionally read journal
        // snapshot using Aside's existing backup format, hand that single file to Android, then
        // remove the temporary plaintext copy from the device immediately.
        try {
            runBlocking(Dispatchers.IO) {
                val entries = MoodDatabase.getDatabase(this@AsideBackupAgent)
                    .moodDao()
                    .getAllEntriesSnapshot()
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                @Suppress("DEPRECATION")
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    packageInfo.versionCode.toLong()
                }
                val json = AsideBackupCodec.encode(
                    entries = entries,
                    appVersionName = packageInfo.versionName ?: "unknown",
                    appVersionCode = versionCode
                )
                snapshotFile.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
            }
            fullBackupFile(snapshotFile, data)
        } finally {
            snapshotFile.delete()
        }
    }

    override fun onRestoreFinished() {
        super.onRestoreFinished()
        // Runtime scheduler databases/alarm timestamps are intentionally not portable. Rebuild
        // from restored configuration using idempotent unique work / a fresh random prompt.
        getSharedPreferences("random_prompt_alarm", MODE_PRIVATE).edit().clear().commit()
        runBlocking(Dispatchers.IO) {
            restoreJournalSnapshotIfPresent()
            RandomPromptAlarmScheduler.reschedule(this@AsideBackupAgent)
            WeeklyReportScheduler.ensureScheduled(this@AsideBackupAgent)
            MonthlyReportScheduler.ensureScheduled(this@AsideBackupAgent)
            BackupReminderScheduler.ensureScheduled(this@AsideBackupAgent)
        }
    }

    private suspend fun restoreJournalSnapshotIfPresent() {
        val snapshotFile = File(filesDir, JOURNAL_SNAPSHOT_FILE)
        if (!snapshotFile.exists()) return

        try {
            val decoded = AsideBackupCodec.decode(snapshotFile.readText(Charsets.UTF_8))
            MoodDatabase.getDatabase(this).moodDao().replaceAll(decoded.entries)
        } catch (_: Exception) {
            // A damaged transport snapshot must not make the app unusable. replaceAll() is a Room
            // transaction, so a failed import leaves the existing journal untouched.
        } finally {
            snapshotFile.delete()
        }
    }

    companion object {
        private const val JOURNAL_SNAPSHOT_FILE = ".aside_android_backup_journal.json"
    }
}
