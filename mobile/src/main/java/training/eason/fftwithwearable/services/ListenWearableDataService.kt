package training.eason.fftwithwearable.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import training.eason.fftwithwearable.R
import training.eason.fftwithwearable.activities.MainActivity

class ListenWearableDataService : WearableListenerService() {

    private val mVibrator: Vibrator? by lazy { getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }
    private val mDrowningReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.extras?.apply {
                if (intent.getStringExtra("event") == "drowning") {
                    mVibrator?.cancel()
                    mVibrator?.also { _vibrator ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            _vibrator.vibrate(VibrationEffect.createOneShot(VIBRATION_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            _vibrator.vibrate(VIBRATION_MILLIS)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "ListenWearableDataService"
        internal const val VIBRATION_MILLIS = 60000L
        internal const val DROWNING_EXTRA_NAME_VALUE = "drowningService"
    }

    @SuppressLint("LongLogTag")
    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        try {
            unregisterReceiver(mDrowningReceiver)
        } catch (e: Exception) {
        }

        registerReceiver(mDrowningReceiver, IntentFilter("drowning"))

        val eventString = String(messageEvent.data)
        val sourceNodeId = messageEvent.sourceNodeId

        when {
            eventString.startsWith("register") -> {

                //將游泳資料寫入 SharedPreferences (格式 - 來源ID:性別)
                val sharedPreferences = this.getSharedPreferences("drowningManager", Context.MODE_PRIVATE)
                var registerListString = sharedPreferences.getString("registerList", "")

                //判斷來源 id 不存在已註冊的 id 列表中才進行註冊
                if (!registerListString.contains(sourceNodeId)) {
                    registerListString += "$sourceNodeId:${eventString.split(",")[1]},D9EG789:女性,JSF63E5:女性,K1WQ52A:男性,L66RFNZ:男性,ADILXH8:女性,CCW56OI:男性,32ENBC9:女性,B2R6WC3:男性,KOO61S:男性,"
//                    registerListString += "$sourceNodeId:${eventString.split(",")[1]},"
                    val editor = sharedPreferences.edit()

                    //先進行移除動作再進行寫入動作，確保資料正確寫入
                    if (editor.remove("registerList").commit()) {
                        editor.putString("registerList", registerListString).apply()
                    }

                    val intent = Intent(this, MainActivity::class.java)

                    //利用當前系統時間毫秒數來讓每次產生的 Intent 都視為是不同的動作
                    //以解決發生接收到連續推播後只能開啟第一則，無法開啟第二則之後的問題
                    intent.action = System.currentTimeMillis().toString()
                    intent.putExtra("event", "register")

                    val notificationSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    val pendingIntent = PendingIntent.getActivity(this, 0, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT)       //重複使用此 PendingIntent 並更新內容

                    val notificationBuilder = Notification.Builder(this)

                    notificationBuilder.setSmallIcon(R.mipmap.ic_launcher)
                    notificationBuilder.setContentTitle("事件通報")
                    notificationBuilder.setContentText("【新增】一筆游泳者資料！")
                    notificationBuilder.setAutoCancel(true)
                    notificationBuilder.setSound(notificationSound)
                    notificationBuilder.setContentIntent(pendingIntent)
                    notificationBuilder.setPriority(Notification.PRIORITY_HIGH)
                    notificationBuilder.setVisibility(Notification.VISIBILITY_PUBLIC)

                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                    //利用不同的 HashCode 來產生每次都不同的通知代號
                    //以達到 Android 系統能夠保留之前的通知紀錄直到使用者觸發
                    notificationManager.notify(messageEvent.hashCode(), notificationBuilder.build())
                }
            }
            eventString.startsWith("delete") -> {

                //將游泳資料從 SharedPreferences 移除
                var newRegisterListString = ""
                val sharedPreferences = this.getSharedPreferences("drowningManager", Context.MODE_PRIVATE)
                val registerList = sharedPreferences.getString("registerList", "").split(",")
                registerList.filter {
                    it.indexOf(sourceNodeId) == -1 && it.isNotBlank()
                }.forEach {
                    newRegisterListString +=  "$it,"
                }

                val editor = sharedPreferences.edit()

                //先進行移除動作再進行寫入動作，確保資料正確寫入
                if (editor.remove("registerList").commit()) {
                    editor.putString("registerList", newRegisterListString).apply()
                }

                val intent = Intent(this, MainActivity::class.java)

                //利用當前系統時間毫秒數來讓每次產生的 Intent 都視為是不同的動作
                //以解決發生接收到連續推播後只能開啟第一則，無法開啟第二則之後的問題
                intent.action = System.currentTimeMillis().toString()
                intent.putExtra("event", "delete")

                val notificationSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                val pendingIntent = PendingIntent.getActivity(this, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT)       //重複使用此 PendingIntent 並更新內容

                val notificationBuilder = Notification.Builder(this)

                notificationBuilder.setSmallIcon(R.mipmap.ic_launcher)
                notificationBuilder.setContentTitle("事件通報")
                notificationBuilder.setContentText("【刪除】一筆游泳者資料！")
                notificationBuilder.setAutoCancel(true)
                notificationBuilder.setSound(notificationSound)
                notificationBuilder.setContentIntent(pendingIntent)
                notificationBuilder.setPriority(Notification.PRIORITY_HIGH)
                notificationBuilder.setVisibility(Notification.VISIBILITY_PUBLIC)

                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                //利用不同的 HashCode 來產生每次都不同的通知代號
                //以達到 Android 系統能夠保留之前的通知紀錄直到使用者觸發
                notificationManager.notify(messageEvent.hashCode(), notificationBuilder.build())
            }
            eventString.startsWith("drowning") -> {
                val intent = Intent(this, MainActivity::class.java)

                //利用當前系統時間毫秒數來讓每次產生的 Intent 都視為是不同的動作
                //以解決發生接收到連續推播後只能開啟第一則，無法開啟第二則之後的問題
                intent.action = System.currentTimeMillis().toString()
                intent.putExtra("event", "drowning")
                intent.putExtra("drowningId", sourceNodeId)

                val notificationSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                val pendingIntent = PendingIntent.getActivity(this, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT)       //重複使用此 PendingIntent 並更新內容

                val notificationBuilder = Notification.Builder(this)

                notificationBuilder.setSmallIcon(R.mipmap.ic_launcher)
                notificationBuilder.setContentTitle("事件通報")
                notificationBuilder.setContentText("【疑似發生溺水事件】，請注意！")
                notificationBuilder.setAutoCancel(true)
                notificationBuilder.setSound(notificationSound)
                notificationBuilder.setContentIntent(pendingIntent)
                notificationBuilder.setPriority(Notification.PRIORITY_HIGH)
                notificationBuilder.setVisibility(Notification.VISIBILITY_PUBLIC)

                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                //利用不同的 HashCode 來產生每次都不同的通知代號
                //以達到 Android 系統能夠保留之前的通知紀錄直到使用者觸發
                notificationManager.notify(messageEvent.hashCode(), notificationBuilder.build())

                sendBroadcast(Intent().apply {
                    action = "drowning"
                    putExtra("event", "drowning")
                })
            }
        }
    }
}
