package com.nickspeelman.localjournal.startup

import android.content.Context
import java.io.File
import java.util.Properties

/** Device-local install classification. The marker lives under noBackupFilesDir in Alpha 4. */
class InstallStateResolver(private val context: Context) {
    private val markerFile = File(context.noBackupFilesDir, "aside_startup_state.properties")
    private val legacyPrefs = context.getSharedPreferences("aside_startup_state", Context.MODE_PRIVATE)

    fun isFreshInstall(): Boolean {
        readMarker()?.let { return it }

        // Upgrade from Alpha 2/3: preserve the old classification without keeping a backup-eligible marker.
        if (legacyPrefs.getBoolean("alpha2_initialized", false)) {
            writeMarker(false, initialized = true)
            legacyPrefs.edit().clear().apply()
            return false
        }

        val settingsFile = File(context.filesDir, "datastore/settings.preferences_pb")
        val databaseExists = context.getDatabasePath("mood_database").exists()
        val alarmPrefs = File(context.applicationInfo.dataDir, "shared_prefs/random_prompt_alarm.xml")
        val hasLegacyAppData = settingsFile.exists() || databaseExists || alarmPrefs.exists()
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val looksLikePackageUpgrade = packageInfo.lastUpdateTime > packageInfo.firstInstallTime + 1_000L
        val fresh = !hasLegacyAppData && !looksLikePackageUpgrade
        writeMarker(fresh, initialized = false)
        return fresh
    }

    fun markInitialized() {
        val fresh = readMarker() ?: false
        writeMarker(fresh, initialized = true)
    }

    private fun readMarker(): Boolean? {
        if (!markerFile.exists()) return null
        val p = Properties().apply { markerFile.inputStream().use(::load) }
        return p.getProperty("fresh")?.toBooleanStrictOrNull()
    }

    private fun writeMarker(fresh: Boolean, initialized: Boolean) {
        markerFile.parentFile?.mkdirs()
        Properties().apply {
            setProperty("fresh", fresh.toString())
            setProperty("initialized", initialized.toString())
        }.also { props -> markerFile.outputStream().use { props.store(it, "Aside device-local startup state") } }
    }
}
