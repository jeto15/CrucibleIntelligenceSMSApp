package com.example.crucibleintelligencesmsapp.receiver

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.crucibleintelligencesmsapp.data.AppDatabase
import com.example.crucibleintelligencesmsapp.data.EntityProfile
import com.example.crucibleintelligencesmsapp.data.MessageRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CrucibleSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION && intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (messages.isEmpty()) return@launch

                val address = messages[0].displayOriginatingAddress ?: return@launch
                val body = messages.joinToString("") { it.displayMessageBody ?: "" }
                val timestamp = messages[0].timestampMillis

                val normalized = address.replace(Regex("[^0-9+]"), "")

                val db = AppDatabase.getDatabase(context)
                val profileDao = db.profileDao()
                val messageDao = db.messageDao()

                var profile = profileDao.getProfileByPhoneNumber(normalized)
                if (profile == null) {
                    val id = profileDao.insertProfile(
                        EntityProfile(
                            name = null,
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
                            isIncoming = true
                        )
                    )
                }

                // Mirror to system provider
                val threadId = try {
                    Telephony.Threads.getOrCreateThreadId(context, address)
                } catch (e: Exception) {
                    0L
                }

                val values = ContentValues().apply {
                    if (threadId > 0) {
                        put(Telephony.Sms.THREAD_ID, threadId)
                    }
                    put(Telephony.Sms.ADDRESS, address)
                    put(Telephony.Sms.BODY, body)
                    put(Telephony.Sms.DATE, timestamp)
                    put(Telephony.Sms.READ, 0)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                }
                context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
                context.contentResolver.notifyChange(Telephony.Sms.CONTENT_URI, null)

            } finally {
                pendingResult.finish()
            }
        }
    }
}
