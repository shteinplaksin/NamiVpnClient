package io.github.hhwkart.nami.data.backup

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import io.github.hhwkart.nami.database.ParcelizeBridge
import io.github.hhwkart.nami.database.ProxyEntity
import io.github.hhwkart.nami.database.ProxyGroup
import io.github.hhwkart.nami.database.SagerDatabase
import io.github.hhwkart.nami.database.preference.KeyValuePair
import io.github.hhwkart.nami.database.preference.PublicDatabase
import io.github.hhwkart.nami.core.utils.Util
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Recovers a prepared legacy restore journal after process death. The
 * pre-image is logical row data, so recovery never copies an open SQLite db
 * file and does not depend on WAL/SHM snapshot timing.
 */
object LegacyRestoreRecovery {
    private const val JOURNAL_FILE_NAME = "restore-journal.json"
    private val recoveryMutex = Mutex()

    fun journal(context: Context): LogicalRestoreJournal = LogicalRestoreJournal(
        AtomicFileRestoreJournalStorage(
            File(context.applicationContext.filesDir, JOURNAL_FILE_NAME),
        ),
    )

    suspend fun recoverPending(context: Context) = recoveryMutex.withLock {
        val journal = journal(context)
        val pending = journal.pending() ?: return@withLock
        restorePreImage(pending.preImage)
        verifyPreImage(pending.preImage)
        journal.clearPreparedAfterRollback(pending.operationId)
    }

    fun restorePreImage(preImage: LogicalRestorePreImage) {
        if (preImage.profiles != null || preImage.groups != null || preImage.rules != null) {
            SagerDatabase.instance.runInTransaction {
                preImage.profiles?.let { encoded ->
                    val profiles = encoded.map {
                        parcelize(it) { parcel -> ProxyEntity.CREATOR.createFromParcel(parcel) }
                    }
                    SagerDatabase.proxyDao.reset()
                    SagerDatabase.proxyDao.insert(profiles)
                }
                preImage.groups?.let { encoded ->
                    val groups = encoded.map {
                        parcelize(it) { parcel -> ProxyGroup.CREATOR.createFromParcel(parcel) }
                    }
                    SagerDatabase.groupDao.reset()
                    SagerDatabase.groupDao.insert(groups)
                }
                preImage.rules?.let { encoded ->
                    val rules = encoded.map {
                        parcelize(it) { parcel -> ParcelizeBridge.createRule(parcel) }
                    }
                    SagerDatabase.rulesDao.reset()
                    SagerDatabase.rulesDao.insert(rules)
                }
            }
        }
        preImage.settings?.let { encoded ->
            val settings = encoded.map {
                parcelize(it) { parcel -> KeyValuePair.CREATOR.createFromParcel(parcel) }
            }
            PublicDatabase.instance.runInTransaction {
                PublicDatabase.kvPairDao.reset()
                PublicDatabase.kvPairDao.insert(settings)
            }
        }
    }

    fun verifyPreImage(preImage: LogicalRestorePreImage) {
        fun verifySection(name: String, expected: List<String>?, actual: List<String>) {
            if (expected == null) return
            check(expected.groupingBy { it }.eachCount() == actual.groupingBy { it }.eachCount()) {
                "Rollback $name rows do not match the pre-image journal"
            }
        }

        verifySection(
            "profiles",
            preImage.profiles,
            SagerDatabase.proxyDao.getAll().map { it.toBase64Str() },
        )
        verifySection(
            "groups",
            preImage.groups,
            SagerDatabase.groupDao.allGroups().map { it.toBase64Str() },
        )
        verifySection(
            "rules",
            preImage.rules,
            SagerDatabase.rulesDao.allRules().map { it.toBase64Str() },
        )
        verifySection(
            "settings",
            preImage.settings,
            PublicDatabase.kvPairDao.all().map { it.toBase64Str() },
        )
    }

    private fun Parcelable.toBase64Str(): String {
        val parcel = Parcel.obtain()
        return try {
            writeToParcel(parcel, 0)
            Util.b64EncodeUrlSafe(parcel.marshall())
        } finally {
            parcel.recycle()
        }
    }

    private fun <T> parcelize(encoded: String, creator: (Parcel) -> T): T {
        val data = Util.b64Decode(encoded)
        val parcel = Parcel.obtain()
        return try {
            parcel.unmarshall(data, 0, data.size)
            parcel.setDataPosition(0)
            creator(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
