package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class MessageCheckService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var sharedPreferences: SharedPreferences

    private val supabaseApi: SupabaseApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.SUPABASE_URL.let { if (it.endsWith("/")) it else "$it/" })
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(SupabaseApi::class.java)
    }

    override fun onCreate() {
        super.onCreate()
        sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundServiceNotification()
        startPolling()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun startForegroundServiceNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_FOREGROUND)
            .setContentTitle("تطبيق الطفل يعمل")
            .setContentText("جاري الاتصال بالنظام لتلقي التحديثات")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID_FOREGROUND,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
            )
        } else {
            startForeground(NOTIFICATION_ID_FOREGROUND, notification)
        }
    }

    private fun startPolling() {
        serviceScope.launch {
            val apiKey = BuildConfig.SUPABASE_ANON_KEY
            val authHeader = "Bearer $apiKey"
            
            while (isActive) {
                try {
                    val messages = supabaseApi.getMessages(apiKey, authHeader)
                    if (messages.isNotEmpty()) {
                        val latestMessage = messages.first()
                        val lastMessageId = sharedPreferences.getString("last_message_id", null)
                        
                        // Notify UI that a successful check occurred (using a broadcast)
                        val intent = Intent(ACTION_UPDATE_STATUS)
                        intent.putExtra("status", "متصل بالنظام")
                        intent.putExtra("last_msg_text", latestMessage.getDisplayText())
                        sendBroadcast(intent)

                        if (lastMessageId != latestMessage.id) {
                            // New message found
                            sharedPreferences.edit().putString("last_message_id", latestMessage.id).apply()
                            
                            // Don't alert if it's the very first time we boot up this logic,
                           // actually let's alert anyway unless it's first boot, to avoid spam, just alert
                            if (lastMessageId != null) {
                                showMessageNotification(latestMessage.getDisplayText())
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    val intent = Intent(ACTION_UPDATE_STATUS)
                    intent.putExtra("status", "خطأ في الاتصال")
                    sendBroadcast(intent)
                }
                delay(10_000) // Poll every 10 seconds
            }
        }
    }

    private fun showMessageNotification(text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_MESSAGES)
            .setContentTitle("تنبيه من المشرف")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
            
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val fgChannel = NotificationChannel(
                CHANNEL_ID_FOREGROUND,
                "حالة التطبيق",
                NotificationManager.IMPORTANCE_LOW
            )
            val msgChannel = NotificationChannel(
                CHANNEL_ID_MESSAGES,
                "الرسائل الجديدة",
                NotificationManager.IMPORTANCE_HIGH
            )
            
            manager.createNotificationChannels(listOf(fgChannel, msgChannel))
        }
    }

    companion object {
        const val CHANNEL_ID_FOREGROUND = "child_app_foreground"
        const val CHANNEL_ID_MESSAGES = "child_app_messages"
        const val NOTIFICATION_ID_FOREGROUND = 1
        const val ACTION_UPDATE_STATUS = "com.example.UPDATE_STATUS"
    }
}
