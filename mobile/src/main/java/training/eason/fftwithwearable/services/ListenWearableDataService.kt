package training.eason.fftwithwearable.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import training.eason.fftwithwearable.R
import training.eason.fftwithwearable.activities.MainActivity

class ListenWearableDataService : WearableListenerService() {

    companion object {
        private const val TAG = "ListenWearableDataService"
        internal const val DROWNING_EXTRA_NAME_VALUE = "drowningService"
    }

    @SuppressLint("LongLogTag")
    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        val data = messageEvent.data

        val intent = Intent(this, MainActivity::class.java)

//        //關閉 Intent 啟動動畫
//        //強制銷毀原有的 MainActivity 並重新產生一份
//        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
//                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        //利用當前系統時間毫秒數來讓每次產生的 Intent 都視為是不同的動作
        //以解決發生接收到連續推播後只能開啟第一則，無法開啟第二則之後的問題
        intent.action = System.currentTimeMillis().toString()
        intent.putExtra(DROWNING_EXTRA_NAME_VALUE, DROWNING_EXTRA_NAME_VALUE)

        val notificationSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT)       //重複使用此 PendingIntent 並更新內容

        val notificationBuilder = Notification.Builder(this)

        notificationBuilder.setSmallIcon(R.mipmap.ic_launcher)
        notificationBuilder.setContentTitle("溺水通報")
        notificationBuilder.setContentText("發生溺水！")
        notificationBuilder.setAutoCancel(true)
        notificationBuilder.setSound(notificationSound)
        notificationBuilder.setContentIntent(pendingIntent)
        notificationBuilder.setPriority(Notification.PRIORITY_HIGH)
        notificationBuilder.setVisibility(Notification.VISIBILITY_PUBLIC)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        //利用不同的 HashCode 來產生每次都不同的通知代號
        //以達到 Android 系統能夠保留之前的通知紀錄直到使用者觸發
        notificationManager.notify(data.hashCode(), notificationBuilder.build())
    }
}
