package com.nickspeelman.localjournal.backup

import android.content.Context
import android.os.Build
import android.util.AtomicFile
import java.io.File
import java.util.Properties

class BackupPolicyStore(context: Context) {
    private val file = File(context.noBackupFilesDir, "aside_backup_policy.properties")
    private val atomicFile = AtomicFile(file)

    fun exists(): Boolean = atomicFile.baseFile.exists()

    fun read(): AndroidBackupPolicy {
        if (!exists()) return AndroidBackupPolicy.MAXIMUM_PRIVACY
        return runCatching {
            val p = Properties().apply { atomicFile.openRead().use(::load) }
            AndroidBackupPolicy(
                cloudJournalIncluded = p.getProperty("cloudJournalIncluded")?.toBooleanStrictOrNull() ?: false,
                deviceTransferJournalIncluded = p.getProperty("deviceTransferJournalIncluded")?.toBooleanStrictOrNull() ?: false
            )
        }.getOrElse {
            // Corrupt or partially recovered policy files fail closed: journal data stays excluded.
            AndroidBackupPolicy.MAXIMUM_PRIVACY
        }
    }

    @Synchronized
    fun write(policy: AndroidBackupPolicy) {
        file.parentFile?.mkdirs()
        val effective = if (Build.VERSION.SDK_INT < 28) {
            val included = policy.cloudJournalIncluded && policy.deviceTransferJournalIncluded
            AndroidBackupPolicy(included, included)
        } else policy
        Properties().apply {
            setProperty("version", "1")
            setProperty("cloudJournalIncluded", effective.cloudJournalIncluded.toString())
            setProperty("deviceTransferJournalIncluded", effective.deviceTransferJournalIncluded.toString())
        }.also { props ->
            val output = atomicFile.startWrite()
            try {
                props.store(output, "Aside device-local Android backup policy")
                atomicFile.finishWrite(output)
            } catch (error: Throwable) {
                atomicFile.failWrite(output)
                throw error
            }
        }
    }
}
