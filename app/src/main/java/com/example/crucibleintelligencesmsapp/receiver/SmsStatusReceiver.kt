package com.example.crucibleintelligencesmsapp.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object SmsActions {
    const val ACTION_SMS_SENT = "com.crucible.intelligence.sms.SMS_SENT"
    const val ACTION_SMS_DELIVERED = "com.crucible.intelligence.sms.SMS_DELIVERED"
    const val EXTRA_SMS_URI = "extra_sms_uri"
}

class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val smsUriString = intent.getStringExtra(SmsActions.EXTRA_SMS_URI)
        if (smsUriString.isNullOrBlank()) return

        val smsUri = Uri.parse(smsUriString)
        val resultCode = resultCode

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    SmsActions.ACTION_SMS_SENT -> {
                        val values = ContentValues()
                        if (resultCode == Activity.RESULT_OK) {
                            values.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                        } else {
                            values.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED)
                            values.put(Telephony.Sms.ERROR_CODE, resultCode)
                        }
                        try {
                            context.contentResolver.update(smsUri, values, null, null)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        try {
                            context.contentResolver.notifyChange(Telephony.Sms.CONTENT_URI, null)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    SmsActions.ACTION_SMS_DELIVERED -> {
                        val values = ContentValues()
                        if (resultCode == Activity.RESULT_OK) {
                            values.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                            values.put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_COMPLETE)
                        } else {
                            values.put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_FAILED)
                        }
                        try {
                            context.contentResolver.update(smsUri, values, null, null)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        try {
                            context.contentResolver.notifyChange(Telephony.Sms.CONTENT_URI, null)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
