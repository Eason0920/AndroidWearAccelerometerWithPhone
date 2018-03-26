package training.eason.fftwithwearable.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import training.eason.fftwithwearable.R;
import training.eason.fftwithwearable.activities.MainActivity;

public class ListenWearableDataService extends WearableListenerService {
    private static final String TAG = "ListenWearableDataService";

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint("LongLogTag")
    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);
        final byte[] data = messageEvent.getData();

        Intent intent = new Intent(this, MainActivity.class);

        //關閉 Intent 啟動動畫
        //強制銷毀原有的 MainActivity 並重新產生一份
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        //利用當前系統時間毫秒數來讓每次產生的 Intent 都視為是不同的動作
        //以解決發生接收到連續推播後只能開啟第一則，無法開啟第二則之後的問題
        intent.setAction(Long.toString(System.currentTimeMillis()));

        final Uri notificationSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        final PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT);       //此 PendingIntent 只作用一次

        final Notification.Builder notificationBuilder = new Notification.Builder(this);

        notificationBuilder.setSmallIcon(R.mipmap.ic_launcher);
        notificationBuilder.setContentTitle("溺水通報");
        notificationBuilder.setContentText("發生溺水！");
        notificationBuilder.setAutoCancel(true);
        notificationBuilder.setSound(notificationSound);
        notificationBuilder.setContentIntent(pendingIntent);
        notificationBuilder.setPriority(Notification.PRIORITY_HIGH);
        notificationBuilder.setVisibility(Notification.VISIBILITY_PUBLIC);

        final NotificationManager notificationManager
                = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        //利用不同的 HashCode 來產生每次都不同的通知代號
        //以達到 Android 系統能夠保留之前的通知紀錄直到使用者觸發
        if (notificationManager != null) {
            notificationManager.notify(data.hashCode(), notificationBuilder.build());
        }

//        File fileDir = getApplicationContext().getExternalFilesDir("accelerometer");
//        if (fileDir != null && !fileDir.exists()) {
//            fileDir.mkdir();
//        }
//
//        String fileFullPath = String.format("%s/%s", fileDir, "accelerometer.txt");
//        File f = new File(fileFullPath);
//        CSVWriter writer;
//        String[] contents;
//
//        try {
//
//            // File exist
//            if (f.exists() && !f.isDirectory()) {
//                writer = new CSVWriter(new FileWriter(fileFullPath, true));
//            } else {    // File not exist
//                writer = new CSVWriter(new FileWriter(fileFullPath));
//                contents = new String[]{"acc_x", "acc_y", "acc_z"};
//                writer.writeNext(contents);
//            }
//
//            contents = new String(data).split(",");
//
//            writer.writeNext(contents);
//            writer.close();
//
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }
}
