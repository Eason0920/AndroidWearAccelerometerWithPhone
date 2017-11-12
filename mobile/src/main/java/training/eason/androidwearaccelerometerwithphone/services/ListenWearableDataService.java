package training.eason.androidwearaccelerometerwithphone.services;

import android.annotation.SuppressLint;
import android.widget.Toast;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

public class ListenWearableDataService extends WearableListenerService {
    public static final String TAG = "ListenWearableDataService";

    @SuppressLint("LongLogTag")
    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        String path = messageEvent.getPath();
        Toast.makeText(this, path, Toast.LENGTH_SHORT).show();
//        Log.e(TAG, path);
    }
}
