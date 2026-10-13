package com.example.crucibleintelligencesmsapp.data.repository

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import com.example.crucibleintelligencesmsapp.data.AppDatabase
import com.example.crucibleintelligencesmsapp.data.EntityProfile
import com.example.crucibleintelligencesmsapp.data.MessageRecord
import com.example.crucibleintelligencesmsapp.receiver.SmsActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

enum class SmsStatus {
    NONE,
    SENDING,
    SENT,
    DELIVERED,
    FAILED
}

data class SmsConversation(
    val threadId: Long,
    val address: String,
    val contactName: String?,
    val displayTitle: String,
    val lastMessage: String,
    val timestamp: Long,
    val unreadCount: Int = 0
)

data class SmsMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val timestamp: Long,
    val type: Int,
    val isIncoming: Boolean,
    val status: SmsStatus = SmsStatus.NONE
)

fun mapTelephonyStatus(type: Int, rawStatus: Int): SmsStatus {
    return when {
        type == Telephony.Sms.MESSAGE_TYPE_OUTBOX || type == Telephony.Sms.MESSAGE_TYPE_QUEUED -> SmsStatus.SENDING
        type == Telephony.Sms.MESSAGE_TYPE_FAILED -> SmsStatus.FAILED
        type == Telephony.Sms.MESSAGE_TYPE_SENT -> {
            when (rawStatus) {
                Telephony.Sms.STATUS_COMPLETE -> SmsStatus.DELIVERED
                Telephony.Sms.STATUS_FAILED -> SmsStatus.FAILED
                Telephony.Sms.STATUS_PENDING -> SmsStatus.SENDING
                else -> SmsStatus.SENT
            }
        }
        else -> SmsStatus.NONE
    }
}

private data class SmsConversationBuilder(
    var threadId: Long,
    val address: String,
    val contactName: String?,
    val displayTitle: String,
    val lastMessage: String,
    val timestamp: Long,
    var unreadCount: Int
) {
    fun build() = SmsConversation(
        threadId = threadId,
        address = address,
        contactName = contactName,
        displayTitle = displayTitle,
        lastMessage = lastMessage,
        timestamp = timestamp,
        unreadCount = unreadCount
    )
}

class SmsRepository(
    private val context: Context,
    private val db: AppDatabase? = null
) {
    private val contactNameCache = ConcurrentHashMap<String, String>()
    private val threadIdCache = ConcurrentHashMap<String, Long>()
    private val nonDigitPlusRegex = Regex("[^0-9+]")

    private fun normalizeAddress(address: String): String {
        return address.replace(nonDigitPlusRegex, "")
    }

    private fun resolveContactNameCached(phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        val cached = contactNameCache[phoneNumber]
        if (cached != null) {
            return if (cached == NO_CONTACT_FOUND) null else cached
        }
        val name = resolveContactName(phoneNumber)
        contactNameCache[phoneNumber] = name ?: NO_CONTACT_FOUND
        return name
    }

    companion object {
        private const val NO_CONTACT_FOUND = "___NO_CONTACT_FOUND___"
    }

    suspend fun markAsRead(threadId: Long, address: String) = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
            }
            val selection: String
            val selectionArgs: Array<String>
            val norm = normalizeAddress(address)
            val resolvedThreadId = if (threadId > 0) threadId else threadIdCache[norm] ?: 0L

            if (resolvedThreadId > 0 && address.isNotBlank()) {
                selection = "(${Telephony.Sms.THREAD_ID} = ? OR ${Telephony.Sms.ADDRESS} = ?) AND ${Telephony.Sms.READ} = 0"
                selectionArgs = arrayOf(resolvedThreadId.toString(), address)
            } else if (resolvedThreadId > 0) {
                selection = "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0"
                selectionArgs = arrayOf(resolvedThreadId.toString())
            } else if (address.isNotBlank()) {
                selection = "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.READ} = 0"
                selectionArgs = arrayOf(address)
            } else {
                return@withContext
            }
            context.contentResolver.update(Telephony.Sms.CONTENT_URI, values, selection, selectionArgs)
            context.contentResolver.notifyChange(Telephony.Sms.CONTENT_URI, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getConversations(): List<SmsConversation> = withContext(Dispatchers.IO) {
        val conversationsMap = LinkedHashMap<String, SmsConversationBuilder>()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ
        )

        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )

            cursor?.use { c ->
                val threadIdx = c.getColumnIndex(Telephony.Sms.THREAD_ID)
                val addressIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = c.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = c.getColumnIndex(Telephony.Sms.DATE)
                val readIdx = c.getColumnIndex(Telephony.Sms.READ)

                while (c.moveToNext()) {
                    val threadId = if (threadIdx != -1) c.getLong(threadIdx) else 0L
                    val address = if (addressIdx != -1) c.getString(addressIdx) ?: "" else ""
                    val body = if (bodyIdx != -1) c.getString(bodyIdx) ?: "" else ""
                    val date = if (dateIdx != -1) c.getLong(dateIdx) else 0L
                    val read = if (readIdx != -1) c.getInt(readIdx) else 1

                    if (address.isBlank()) continue

                    val normalizedAddress = normalizeAddress(address)
                    val key = if (threadId > 0) "thread_$threadId" else "addr_$normalizedAddress"

                    if (!conversationsMap.containsKey(key)) {
                        val resolvedThreadId = if (threadId > 0) {
                            if (normalizedAddress.isNotBlank()) {
                                threadIdCache[normalizedAddress] = threadId
                            }
                            threadId
                        } else {
                            if (normalizedAddress.isNotBlank() && threadIdCache.containsKey(normalizedAddress)) {
                                threadIdCache[normalizedAddress]!!
                            } else {
                                val tid = try {
                                    Telephony.Threads.getOrCreateThreadId(context, address)
                                } catch (e: Exception) {
                                    0L
                                }
                                if (tid > 0 && normalizedAddress.isNotBlank()) {
                                    threadIdCache[normalizedAddress] = tid
                                }
                                tid
                            }
                        }
                        val contactName = resolveContactNameCached(address)
                        val displayTitle = if (!contactName.isNullOrBlank()) contactName else address

                        conversationsMap[key] = SmsConversationBuilder(
                            threadId = resolvedThreadId,
                            address = address,
                            contactName = contactName,
                            displayTitle = displayTitle,
                            lastMessage = body,
                            timestamp = date,
                            unreadCount = if (read == 0) 1 else 0
                        )
                    } else {
                        val builder = conversationsMap[key]
                        if (builder != null) {
                            if (read == 0) {
                                builder.unreadCount += 1
                            }
                            if (builder.threadId == 0L && threadId > 0) {
                                builder.threadId = threadId
                                if (normalizedAddress.isNotBlank()) {
                                    threadIdCache[normalizedAddress] = threadId
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        conversationsMap.values.map { it.build() }
    }

    suspend fun getMessagesForThread(threadId: Long, address: String): List<SmsMessage> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<SmsMessage>()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.STATUS
        )

        var actualThreadId = threadId
        val normalizedAddress = normalizeAddress(address)
        if (actualThreadId <= 0 && normalizedAddress.isNotBlank()) {
            actualThreadId = threadIdCache[normalizedAddress] ?: try {
                Telephony.Threads.getOrCreateThreadId(context, address)
            } catch (e: Exception) {
                0L
            }
            if (actualThreadId > 0) {
                threadIdCache[normalizedAddress] = actualThreadId
            }
        }

        val selection: String?
        val selectionArgs: Array<String>?

        if (actualThreadId > 0 && address.isNotBlank()) {
            selection = "${Telephony.Sms.THREAD_ID} = ? OR ${Telephony.Sms.ADDRESS} = ?"
            selectionArgs = arrayOf(actualThreadId.toString(), address)
        } else if (actualThreadId > 0) {
            selection = "${Telephony.Sms.THREAD_ID} = ?"
            selectionArgs = arrayOf(actualThreadId.toString())
        } else if (address.isNotBlank()) {
            selection = "${Telephony.Sms.ADDRESS} = ?"
            selectionArgs = arrayOf(address)
        } else {
            return@withContext emptyList()
        }

        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} ASC"
            )

            cursor?.use { c ->
                val idIdx = c.getColumnIndex(Telephony.Sms._ID)
                val threadIdx = c.getColumnIndex(Telephony.Sms.THREAD_ID)
                val addressIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = c.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = c.getColumnIndex(Telephony.Sms.DATE)
                val typeIdx = c.getColumnIndex(Telephony.Sms.TYPE)
                val statusIdx = c.getColumnIndex(Telephony.Sms.STATUS)

                val seenIds = HashSet<Long>()

                while (c.moveToNext()) {
                    val id = if (idIdx != -1) c.getLong(idIdx) else 0L
                    if (id != 0L && !seenIds.add(id)) continue

                    val tId = if (threadIdx != -1) c.getLong(threadIdx) else actualThreadId
                    val addr = if (addressIdx != -1) c.getString(addressIdx) ?: address else address
                    val body = if (bodyIdx != -1) c.getString(bodyIdx) ?: "" else ""
                    val date = if (dateIdx != -1) c.getLong(dateIdx) else 0L
                    val type = if (typeIdx != -1) c.getInt(typeIdx) else Telephony.Sms.MESSAGE_TYPE_INBOX
                    val rawStatus = if (statusIdx != -1) c.getInt(statusIdx) else Telephony.Sms.STATUS_NONE

                    if (normalizedAddress.isNotBlank()) {
                        val msgNorm = normalizeAddress(addr)
                        if (actualThreadId > 0 && tId > 0 && tId != actualThreadId && msgNorm != normalizedAddress) {
                            continue
                        }
                    }

                    val isIncoming = (type == Telephony.Sms.MESSAGE_TYPE_INBOX)
                    val status = if (isIncoming) SmsStatus.NONE else mapTelephonyStatus(type, rawStatus)

                    messages.add(
                        SmsMessage(
                            id = id,
                            threadId = tId,
                            address = addr,
                            body = body,
                            timestamp = date,
                            type = type,
                            isIncoming = isIncoming,
                            status = status
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        messages
    }

    fun observeConversations(): Flow<List<SmsConversation>> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                launch(Dispatchers.IO) {
                    trySend(getConversations())
                }
            }
        }
        try {
            context.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        launch(Dispatchers.IO) {
            trySend(getConversations())
        }
        awaitClose {
            try {
                context.contentResolver.unregisterContentObserver(observer)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }.flowOn(Dispatchers.IO)

    fun observeMessagesForThread(threadId: Long, address: String): Flow<List<SmsMessage>> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                launch(Dispatchers.IO) {
                    trySend(getMessagesForThread(threadId, address))
                }
            }
        }
        try {
            context.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        launch(Dispatchers.IO) {
            trySend(getMessagesForThread(threadId, address))
        }
        awaitClose {
            try {
                context.contentResolver.unregisterContentObserver(observer)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun sendMessage(address: String, body: String) = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()

        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, timestamp)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_NONE)
        }

        val insertedUri: Uri? = try {
            context.contentResolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values)
                ?: context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        try {
            context.contentResolver.notifyChange(Telephony.Sms.CONTENT_URI, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java) ?: return@withContext
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

        val uriString = insertedUri?.toString() ?: ""
        val requestCode = (timestamp and 0x7FFFFFFF).toInt()

        val sentIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(SmsActions.ACTION_SMS_SENT).apply {
                putExtra(SmsActions.EXTRA_SMS_URI, uriString)
                setPackage(context.packageName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deliveredIntent = PendingIntent.getBroadcast(
            context,
            requestCode + 1,
            Intent(SmsActions.ACTION_SMS_DELIVERED).apply {
                putExtra(SmsActions.EXTRA_SMS_URI, uriString)
                setPackage(context.packageName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val parts = smsManager.divideMessage(body)
        if (parts.size > 1) {
            val sentIntents = ArrayList<PendingIntent>()
            val deliveredIntents = ArrayList<PendingIntent>()
            for (i in parts.indices) {
                val pSentIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode + i * 2,
                    Intent(SmsActions.ACTION_SMS_SENT).apply {
                        putExtra(SmsActions.EXTRA_SMS_URI, uriString)
                        setPackage(context.packageName)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val pDeliveredIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode + i * 2 + 1,
                    Intent(SmsActions.ACTION_SMS_DELIVERED).apply {
                        putExtra(SmsActions.EXTRA_SMS_URI, uriString)
                        setPackage(context.packageName)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                sentIntents.add(pSentIntent)
                deliveredIntents.add(pDeliveredIntent)
            }
            try {
                smsManager.sendMultipartTextMessage(address, null, parts, sentIntents, deliveredIntents)
            } catch (e: Exception) {
                e.printStackTrace()
                if (insertedUri != null) {
                    val failValues = ContentValues().apply {
                        put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED)
                    }
                    try {
                        context.contentResolver.update(insertedUri, failValues, null, null)
                        context.contentResolver.notifyChange(Telephony.Sms.CONTENT_URI, null)
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }
        } else {
            try {
                smsManager.sendTextMessage(address, null, body, sentIntent, deliveredIntent)
            } catch (e: Exception) {
                e.printStackTrace()
                if (insertedUri != null) {
                    val failValues = ContentValues().apply {
                        put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED)
                    }
                    try {
                        context.contentResolver.update(insertedUri, failValues, null, null)
                        context.contentResolver.notifyChange(Telephony.Sms.CONTENT_URI, null)
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }
        }

        if (db != null) {
            val normalized = normalizeAddress(address)
            val profileDao = db.profileDao()
            val messageDao = db.messageDao()

            var profile = profileDao.getProfileByPhoneNumber(normalized)
            if (profile == null) {
                val contactName = resolveContactNameCached(address)
                val id = profileDao.insertProfile(
                    EntityProfile(
                        name = contactName,
                        phoneNumber = address,
                        normalizedNumber = normalized
                    )
                )
                profile = profileDao.getProfileById(id)
            }

            if (profile != null) {
                messageDao.insertMessage(
                    MessageRecord(
                        entityId = profile.id,
                        content = body,
                        timestamp = timestamp,
                        isIncoming = false,
                        status = SmsStatus.SENDING.name
                    )
                )
            }
        }
    }

    suspend fun getOrCreateProfileForAddress(address: String): EntityProfile? {
        if (db == null) return null
        return withContext(Dispatchers.IO) {
            val normalized = normalizeAddress(address)
            val profileDao = db.profileDao()
            var profile = profileDao.getProfileByPhoneNumber(normalized)
            if (profile == null) {
                val contactName = resolveContactNameCached(address)
                val id = profileDao.insertProfile(
                    EntityProfile(
                        name = contactName,
                        phoneNumber = address,
                        normalizedNumber = normalized
                    )
                )
                profile = profileDao.getProfileById(id)
            }
            profile
        }
    }

    private fun resolveContactName(phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIdx != -1) {
                        val name = cursor.getString(nameIdx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        } catch (e: Exception) {
            // Permission or Uri error
        }
        return null
    }
}
