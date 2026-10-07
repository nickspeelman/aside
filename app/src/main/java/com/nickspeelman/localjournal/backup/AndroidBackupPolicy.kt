package com.nickspeelman.localjournal.backup

data class AndroidBackupPolicy(
    val cloudJournalIncluded: Boolean,
    val deviceTransferJournalIncluded: Boolean
) {
    companion object {
        val LEGACY_ALPHA3 = AndroidBackupPolicy(true, true)
        val MAXIMUM_EASE = AndroidBackupPolicy(true, true)
        val BALANCED = AndroidBackupPolicy(false, true)
        val MAXIMUM_PRIVACY = AndroidBackupPolicy(false, false)
        val LEGACY_ANDROID_BALANCED = AndroidBackupPolicy(false, false)
    }
}
