package training.eason.androidwearaccelerometerwithphone.services;

import android.annotation.SuppressLint;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ListenWearableDataService extends WearableListenerService {
    public static final String TAG = "ListenWearableDataService";

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint("LongLogTag")
    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

//        String path = messageEvent.getPath();
        final byte[] data = messageEvent.getData();
//        Toast.makeText(this, path, Toast.LENGTH_SHORT).show();
//        Log.e(TAG, path);

        File fileDir = getApplicationContext().getExternalFilesDir("accelerometer");
        if (fileDir != null && !fileDir.exists()) {
            fileDir.mkdir();
        }

        String fileFullName = String.format("%s/%s", fileDir, "myfile.txt");
        String string = "test";
        FileOutputStream outputStream;
        try {
            outputStream = new FileOutputStream(fileFullName, true);
            outputStream.write(data);
            outputStream.flush();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
