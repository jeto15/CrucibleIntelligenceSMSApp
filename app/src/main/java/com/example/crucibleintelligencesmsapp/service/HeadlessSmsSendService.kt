package com.example.crucibleintelligencesmsapp.service

import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Telephony
import android.telephony.SmsManager

class HeadlessSmsSendService : Service() {

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val action = intent.action
            if ("android.intent.action.RESPOND_VIA_MESSAGE" == action) {
                val extras = intent.extras
                if (extras != null) {
                    val message = extras.getString(Intent.EXTRA_TEXT)
                    val uri = intent.data
                    val recipients = getRecipients(uri)
                    if (!message.isNullOrEmpty() && recipients.isNotEmpty()) {
                        sendSms(recipients, message)
                    }
                }
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun getRecipients(uri: Uri?): List<String> {
        if (uri == null) return emptyList()
        val schemeSpecific = uri.schemeSpecificPart
        if (schemeSpecific.isNullOrEmpty()) return emptyList()
        return schemeSpecific.split(";", ",").filter { it.isNotBlank() }
    }

    private fun sendSms(recipients: List<String>, message: String) {
        val smsManager = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        } catch (_: Exception) {
            null
        }

        for (recipient in recipients) {
            try {
                smsManager?.sendTextMessage(recipient, null, message, null, null)

                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, recipient)
                    put(Telephony.Sms.BODY, message)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                }
                contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
