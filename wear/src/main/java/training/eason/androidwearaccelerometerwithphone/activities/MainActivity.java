package training.eason.androidwearaccelerometerwithphone.activities;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import org.jtransforms.fft.DoubleFFT_1D;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import training.eason.androidwearaccelerometerwithphone.R;
import training.eason.androidwearaccelerometerwithphone.libs.LimitQueue;

public class MainActivity extends WearableActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    public static final String CALLER_EVENT = "/drowningNotify";
    public static final String TAG = "MainActivity";
    public static final int ACC_DATA_INTERVAL = 256;

    @BindView(R.id.accEventButton)
    Button mAccEventButton;
    @BindView(R.id.accSamplingRateTextView)
    TextView mAccSamplingRate;
    @BindView(R.id.accXTextView)
    TextView mAccXTextView;
    @BindView(R.id.accYTextView)
    TextView mAccYTextView;
    @BindView(R.id.accZTextView)
    TextView mAccZTextView;

    private SensorManager mSensorManager;
    private SensorEventListener mSensorEventListener;
    private Sensor mSensor;
    private Unbinder mUnbinder;
    private int mCurrentStatus = 0;
    private long mPrevMillis = 0;
    private GoogleApiClient mGoogleApiClient;
    private Node mNode;
    private List<AccDataAccess> mAccDataAccessList = new ArrayList<>();
    private LimitQueue<Boolean> mLimitQueue = new LimitQueue<>(5);

    class AccDataAccess {
        float mAccX;
        float mAccY;
        float mAccZ;
        double mPower;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mUnbinder = ButterKnife.bind(this);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        assert mSensorManager != null;
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        // Enables Always-on
        setAmbientEnabled();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        mAccEventButton.setText("開始");
        mSensorManager.unregisterListener(mSensorEventListener);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mUnbinder.unbind();
        mGoogleApiClient.disconnect();
        super.onDestroy();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        resolveNode();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.e(TAG,
                String.format("GoogleClientApi connect failure: %s",
                        connectionResult.getErrorMessage()));
    }

    @OnClick(R.id.accEventButton)
    protected void onAccEventButtonClick(View view) {
        if (mCurrentStatus == 0) {
            mAccEventButton.setText("停止");
            mCurrentStatus = 1;
            mSensorEventListener = new SensorEventListener() {

                @Override
                public void onSensorChanged(final SensorEvent sensorEvent) {
                    if (sensorEvent.accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE) {
                        final long nowMillis = TimeUnit.MILLISECONDS.convert(sensorEvent.timestamp, TimeUnit.NANOSECONDS);
                        final long diffMillis = (nowMillis - mPrevMillis);
                        mPrevMillis = nowMillis;

                        final float accX = sensorEvent.values[0];
                        final float accY = sensorEvent.values[1];
                        final float accZ = sensorEvent.values[2];

                        if (mAccDataAccessList.size() < ACC_DATA_INTERVAL) {
                            mAccDataAccessList.add(new AccDataAccess() {{
                                mAccX = accX;
                                mAccY = accY;
                                mAccZ = accZ;

                                //計算能量
                                mPower = Math.sqrt(Math.pow(accX, 2) + Math.pow(accY, 2) + Math.pow(accZ, 2));
                            }});

                            if (mAccDataAccessList.size() == ACC_DATA_INTERVAL) {
                                convertFrequencyByFFT();
                            }
                        }

                        mAccSamplingRate.setText(String.format("Sampling rate per second: %s", (1000 / diffMillis)));
                        mAccXTextView.setText(String.format(Locale.getDefault(), "X: %s", accX));
                        mAccYTextView.setText(String.format(Locale.getDefault(), "Y: %s", accY));
                        mAccZTextView.setText(String.format(Locale.getDefault(), "Z: %s", accZ));
                    }
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int i) {

                }

            };

            //per second 100 Hz
            mSensorManager.registerListener(mSensorEventListener, mSensor, SensorManager.SENSOR_DELAY_FASTEST);
        } else {
            mSensorManager.unregisterListener(mSensorEventListener);
            mAccEventButton.setText("開始");
            mCurrentStatus = 0;
        }
    }

    /**
     * 取得與配對的行動裝置連線用的節點
     */
    private void resolveNode() {
        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient)
                .setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {

                    @Override
                    public void onResult(@NonNull NodeApi.GetConnectedNodesResult nodesResult) {
                        for (Node node : nodesResult.getNodes()) {
                            mNode = node;
                        }
                    }
                });
    }

    /**
     * 將能量利用傅立葉轉換為頻率數據
     */
    private void convertFrequencyByFFT() {

        //取出能量數據
        double[] inputAccDataPowers = new double[mAccDataAccessList.size()];
        for (int i = 0; i < mAccDataAccessList.size(); i++) {
            inputAccDataPowers[i] = mAccDataAccessList.get(i).mPower;
            mAccDataAccessList.remove(i);
        }

        //利用 JTransforms FFT library 轉換為頻率數據
        DoubleFFT_1D fftDo = new DoubleFFT_1D(inputAccDataPowers.length);
        double[] fft = Arrays.copyOf(inputAccDataPowers, inputAccDataPowers.length * 2);
        fftDo.realForwardFull(fft);

//        for(double d: fft) {
//            Log.e(TAG, "fft: " + d);
//        }

        //check
        if (checkDrowning()) {
            notifyMobile();
        }
    }

    /**
     * 檢查存放溺水狀態的集合是否有達到連續五次疑似溺水標準
     *
     * @return boolean
     */
    private boolean checkDrowning() {
//        for (boolean state : mLimitQueue) {
//            if (!state) {
//                return false;
//            }
//        }

        return true;
    }

    /**
     * 發生溺水，通知手機
     */
    private void notifyMobile() {
        if (mNode != null && mGoogleApiClient != null) {

            String message = "drowning notify";

            Wearable.MessageApi.sendMessage(
                    mGoogleApiClient,
                    mNode.getId(),
                    CALLER_EVENT,
                    message.getBytes())
                    .setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {

                        @Override
                        public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                            if (!sendMessageResult.getStatus().isSuccess()) {
                                Log.e(TAG,
                                        String.format("sendMessage failure statusCode: %s",
                                                sendMessageResult.getStatus().getStatusCode()));
                            }
                        }
                    });

        }

    }
}
