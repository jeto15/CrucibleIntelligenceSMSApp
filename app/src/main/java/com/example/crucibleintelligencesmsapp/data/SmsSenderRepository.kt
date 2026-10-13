package com.example.crucibleintelligencesmsapp.data

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmsSenderRepository(private val context: Context, private val db: AppDatabase) {

    suspend fun sendMessage(address: String, body: String) {
        withContext(Dispatchers.IO) {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java) ?: return@withContext
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val sentIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent("SMS_SENT"),
                PendingIntent.FLAG_IMMUTABLE
            )

            val deliveredIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent("SMS_DELIVERED"),
                PendingIntent.FLAG_IMMUTABLE
            )

            val parts = smsManager.divideMessage(body)
            if (parts.size > 1) {
                val sentIntents = ArrayList<PendingIntent>()
                val deliveredIntents = ArrayList<PendingIntent>()
                for (i in parts.indices) {
                    sentIntents.add(sentIntent)
                    deliveredIntents.add(deliveredIntent)
                }
                smsManager.sendMultipartTextMessage(address, null, parts, sentIntents, deliveredIntents)
            } else {
                smsManager.sendTextMessage(address, null, body, sentIntent, deliveredIntent)
            }

            val timestamp = System.currentTimeMillis()
            val normalized = address.replace(Regex("[^0-9+]"), "")
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
                        isIncoming = false
                    )
                )
            }

            // Mirror to system provider
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            }
            context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
        }
    }
}
