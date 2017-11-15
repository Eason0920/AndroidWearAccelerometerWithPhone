package training.eason.androidwearaccelerometerwithphone.services;

import android.annotation.SuppressLint;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class ListenWearableDataService extends WearableListenerService {
    public static final String TAG = "ListenWearableDataService";

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint("LongLogTag")
    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);
        final byte[] data = messageEvent.getData();

        File fileDir = getApplicationContext().getExternalFilesDir("accelerometer");
        if (fileDir != null && !fileDir.exists()) {
            fileDir.mkdir();
        }

        String fileFullPath = String.format("%s/%s", fileDir, "accelerometer.txt");
        File f = new File(fileFullPath);
        CSVWriter writer;
        String[] contents;

        try {

            // File exist
            if (f.exists() && !f.isDirectory()) {
                writer = new CSVWriter(new FileWriter(fileFullPath, true));
            } else {    // File not exist
                writer = new CSVWriter(new FileWriter(fileFullPath));
                contents = new String[]{"acc_x", "acc_y", "acc_z"};
                writer.writeNext(contents);
            }

            contents = new String(data).split(",");

            writer.writeNext(contents);
            writer.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
