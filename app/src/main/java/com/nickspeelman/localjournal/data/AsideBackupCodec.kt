package com.nickspeelman.localjournal.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant

object AsideBackupCodec {
    const val FORMAT = "aside-backup"
    const val FORMAT_VERSION = 1

    data class BackupMetadata(
        val createdAtEpochMillis: Long,
        val appVersionName: String,
        val appVersionCode: Long
    )

    data class DecodedBackup(
        val metadata: BackupMetadata,
        val entries: List<MoodEntry>
    )

    class BackupValidationException(message: String) : IllegalArgumentException(message)

    fun encode(
        entries: List<MoodEntry>,
        appVersionName: String,
        appVersionCode: Long,
        createdAtEpochMillis: Long = System.currentTimeMillis()
    ): String {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("formatVersion", FORMAT_VERSION)
            .put("createdAt", Instant.ofEpochMilli(createdAtEpochMillis).toString())
            .put("createdAtEpochMillis", createdAtEpochMillis)
            .put("backupScope", "journal")
            .put(
                "appVersion",
                JSONObject()
                    .put("name", appVersionName)
                    .put("code", appVersionCode)
            )
            .put("entryCount", entries.size)

        val jsonEntries = JSONArray()
        entries.sortedBy { it.id }.forEach { entry ->
            val jsonEntry = JSONObject()
                .put("id", entry.id)
                .put("timestampEpochMillis", entry.timestamp)
                .put("note", entry.note)
                .put("hashtags", entry.hashtags)
            if (entry.rating == null) {
                jsonEntry.put("rating", JSONObject.NULL)
            } else {
                jsonEntry.put("rating", entry.rating)
            }
            jsonEntries.put(jsonEntry)
        }
        root.put("entries", jsonEntries)

        return root.toString(2)
    }

    fun decode(rawJson: String): DecodedBackup {
        val root = try {
            JSONObject(rawJson)
        } catch (_: JSONException) {
            throw BackupValidationException("This backup appears to be damaged or invalid.")
        }

        val format = if (root.has("format") && !root.isNull("format")) {
            try { root.get("format") as? String } catch (_: JSONException) { null }
        } else null
        if (format != FORMAT) {
            throw BackupValidationException("This isn't an Aside backup.")
        }

        val version = try {
            requireInt(root, "formatVersion", "format version")
        } catch (_: BackupValidationException) {
            throw BackupValidationException("This backup has an invalid format version.")
        }
        if (version > FORMAT_VERSION) {
            throw BackupValidationException("This backup was created by a newer version of Aside.")
        }
        if (version < 1) {
            throw BackupValidationException("This backup uses an unsupported format version.")
        }

        val backupScope = try {
            requireString(root, "backupScope", "backup scope")
        } catch (_: BackupValidationException) {
            throw BackupValidationException("This Aside backup doesn't contain a journal backup that this version can restore.")
        }
        if (backupScope != "journal") {
            throw BackupValidationException("This Aside backup doesn't contain a journal backup that this version can restore.")
        }

        val createdAt = requireString(root, "createdAt", "backup creation time")
        val parsedCreatedAt = try {
            Instant.parse(createdAt).toEpochMilli()
        } catch (_: Exception) {
            throw BackupValidationException("This backup has an invalid creation timestamp.")
        }
        val createdAtEpochMillis = requireLong(root, "createdAtEpochMillis", "backup creation time")
        if (createdAtEpochMillis < 0 || parsedCreatedAt != createdAtEpochMillis) {
            throw BackupValidationException("This backup has inconsistent creation timestamp information.")
        }
        val appVersionObject = try {
            root.getJSONObject("appVersion")
        } catch (_: JSONException) {
            throw BackupValidationException("This backup is missing app-version information.")
        }
        val appVersionName = try {
            requireString(appVersionObject, "name", "app version name")
        } catch (_: BackupValidationException) {
            throw BackupValidationException("This backup has invalid app-version information.")
        }
        val appVersionCode = requireLong(appVersionObject, "code", "app version code")

        val entriesArray = try {
            root.getJSONArray("entries")
        } catch (_: JSONException) {
            throw BackupValidationException("This backup is missing its journal entries.")
        }

        if (root.has("entryCount")) {
            val declaredCount = try {
                requireInt(root, "entryCount", "entry count")
            } catch (_: BackupValidationException) {
                throw BackupValidationException("This backup has an invalid entry count.")
            }
            if (declaredCount != entriesArray.length()) {
                throw BackupValidationException("This backup's entry count doesn't match its contents.")
            }
        }

        val seenIds = mutableSetOf<Int>()
        val entries = ArrayList<MoodEntry>(entriesArray.length())
        for (index in 0 until entriesArray.length()) {
            val item = try {
                entriesArray.getJSONObject(index)
            } catch (_: JSONException) {
                throw BackupValidationException("Journal entry ${index + 1} is malformed.")
            }

            val id = requireInt(item, "id", "entry ID", index)
            if (id <= 0) {
                throw BackupValidationException("Journal entry ${index + 1} has an invalid ID.")
            }
            if (!seenIds.add(id)) {
                throw BackupValidationException("This backup contains duplicate journal entry IDs.")
            }

            if (!item.has("rating")) {
                throw BackupValidationException("Journal entry ${index + 1} is missing its mood rating field.")
            }
            val rating = if (item.isNull("rating")) {
                null
            } else {
                val parsed = requireInt(item, "rating", "mood rating", index)
                if (parsed !in 1..5) {
                    throw BackupValidationException("Journal entry ${index + 1} has a mood rating outside 1-5.")
                }
                parsed
            }

            val timestamp = requireLong(item, "timestampEpochMillis", "timestamp", index)
            if (timestamp < 0) {
                throw BackupValidationException("Journal entry ${index + 1} has an invalid timestamp.")
            }

            val note = requireString(item, "note", "note", index)
            val hashtags = requireString(item, "hashtags", "hashtags", index)

            entries += MoodEntry(
                id = id,
                rating = rating,
                note = note,
                hashtags = hashtags,
                timestamp = timestamp
            )
        }

        return DecodedBackup(
            metadata = BackupMetadata(
                createdAtEpochMillis = createdAtEpochMillis,
                appVersionName = appVersionName,
                appVersionCode = appVersionCode
            ),
            entries = entries
        )
    }

    private fun requireString(
        objectValue: JSONObject,
        key: String,
        label: String,
        entryIndex: Int? = null
    ): String {
        if (!objectValue.has(key) || objectValue.isNull(key)) {
            throw fieldError(label, entryIndex)
        }
        val value = try {
            objectValue.get(key)
        } catch (_: JSONException) {
            throw fieldError(label, entryIndex)
        }
        return value as? String ?: throw fieldError(label, entryIndex)
    }

    private fun requireInt(
        objectValue: JSONObject,
        key: String,
        label: String,
        entryIndex: Int? = null
    ): Int {
        val number = requireNumber(objectValue, key, label, entryIndex)
        val asDouble = number.toDouble()
        val asLong = number.toLong()
        if (!asDouble.isFinite() || asDouble != asLong.toDouble() || asLong !in Int.MIN_VALUE..Int.MAX_VALUE) {
            throw fieldError(label, entryIndex)
        }
        return asLong.toInt()
    }

    private fun requireLong(
        objectValue: JSONObject,
        key: String,
        label: String,
        entryIndex: Int? = null
    ): Long {
        val number = requireNumber(objectValue, key, label, entryIndex)
        val asDouble = number.toDouble()
        val asLong = number.toLong()
        if (!asDouble.isFinite() || asDouble != asLong.toDouble()) {
            throw fieldError(label, entryIndex)
        }
        return asLong
    }

    private fun requireNumber(
        objectValue: JSONObject,
        key: String,
        label: String,
        entryIndex: Int?
    ): Number {
        if (!objectValue.has(key) || objectValue.isNull(key)) {
            throw fieldError(label, entryIndex)
        }
        val value = try {
            objectValue.get(key)
        } catch (_: JSONException) {
            throw fieldError(label, entryIndex)
        }
        return value as? Number ?: throw fieldError(label, entryIndex)
    }

    private fun fieldError(label: String, entryIndex: Int?): BackupValidationException {
        return if (entryIndex == null) {
            BackupValidationException("This backup has invalid $label information.")
        } else {
            BackupValidationException("Journal entry ${entryIndex + 1} has an invalid $label.")
        }
    }
}
