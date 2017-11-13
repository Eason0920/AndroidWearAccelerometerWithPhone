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
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import training.eason.androidwearaccelerometerwithphone.R;

public class MainActivity extends WearableActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    public static final String CALLER_EVENT = "/sendAccelerometer";
    public static final String TAG = "MainActivity";

    @BindView(R.id.accDelayChoiceRadioGroup)
    RadioGroup mAccDelayChoiceRadioGroup;
    //    @BindView(R.id.accDelayChoiceFastestRadioButton)
//    RadioButton mAccDelayChoiceFastestRadioButton;
//    @BindView(R.id.accDelayChoiceNormalRadioButton)
//    RadioButton mAccDelayChoiceNormalRadioButton;
//    @BindView(R.id.accDelayChoiceGameRadioButton)
//    RadioButton mAccDelayChoiceGameRadioButton;
//    @BindView(R.id.accDelayChoiceUiRadioButton)
//    RadioButton mAccDelayChoiceUiRadioButton;
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
    private Integer mCurrentDelayMode;
    private GoogleApiClient mGoogleApiClient;
    private Node mNode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mUnbinder = ButterKnife.bind(this);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        assert mSensorManager != null;
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        mAccDelayChoiceRadioGroup.check(R.id.accDelayChoiceFastestRadioButton);
        mAccDelayChoiceRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int id) {
                final RadioButton checkRadioButton = findViewById(id);
                mCurrentDelayMode = Integer.valueOf(checkRadioButton.getTag().toString());
            }
        });

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
        final RadioButton checkRadioButton = findViewById(mAccDelayChoiceRadioGroup.getCheckedRadioButtonId());
        mCurrentDelayMode = Integer.valueOf(checkRadioButton.getTag().toString());
    }

    @Override
    protected void onPause() {
        mAccEventButton.setText("開始");
        mSensorManager.unregisterListener(mSensorEventListener);
        mAccDelayChoiceRadioGroup.setVisibility(View.VISIBLE);
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
            mAccDelayChoiceRadioGroup.setVisibility(View.GONE);
            mSensorEventListener = new SensorEventListener() {

                @Override
                public void onSensorChanged(final SensorEvent sensorEvent) {
                    if (sensorEvent.accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE) {
                        final long nowMillis = TimeUnit.MILLISECONDS.convert(sensorEvent.timestamp, TimeUnit.NANOSECONDS);
                        final long diffMillis = (nowMillis - mPrevMillis);
                        mPrevMillis = nowMillis;

                        mAccSamplingRate.setText(String.format("Sampling rate per second: %s", (1000 / diffMillis)));
                        mAccXTextView.setText(String.format(Locale.getDefault(), "X: %s", sensorEvent.values[0]));
                        mAccYTextView.setText(String.format(Locale.getDefault(), "Y: %s", sensorEvent.values[1]));
                        mAccZTextView.setText(String.format(Locale.getDefault(), "Z: %s", sensorEvent.values[2]));

                        if (mNode != null && mGoogleApiClient != null) {
                            Wearable.MessageApi.sendMessage(
                                    mGoogleApiClient,
                                    mNode.getId(),
                                    CALLER_EVENT,
                                    String.valueOf(sensorEvent.values[0]).getBytes())
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

                @Override
                public void onAccuracyChanged(Sensor sensor, int i) {

                }

            };

            mSensorManager.registerListener(mSensorEventListener, mSensor, mCurrentDelayMode);
        } else {
            mSensorManager.unregisterListener(mSensorEventListener);
            mAccEventButton.setText("開始");
            mCurrentStatus = 0;
            mAccDelayChoiceRadioGroup.setVisibility(View.VISIBLE);
        }

///////////////////////////////////////////////////////
//        if (mCurrentStatus == 0) {
//            mAccEventButton.setText("停止");
//            mCurrentStatus = 1;
//            mAccDelayChoiceRadioGroup.setVisibility(View.GONE);
//            mSensorEventListener = new SensorEventListener() {
//
//                @Override
//                public void onSensorChanged(final SensorEvent sensorEvent) {
//                    if (sensorEvent.accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE) {
//                        final long nowMillis = TimeUnit.MILLISECONDS.convert(sensorEvent.timestamp, TimeUnit.NANOSECONDS);
//                        final long diffMillis = (nowMillis - mPrevMillis);
//                        mPrevMillis = nowMillis;
//
//                        mAccSamplingRate.setText(String.format("Sampling rate per second: %s", (1000 / diffMillis)));
//                        mAccXTextView.setText(String.format(Locale.getDefault(), "X: %s", sensorEvent.values[0]));
//                        mAccYTextView.setText(String.format(Locale.getDefault(), "Y: %s", sensorEvent.values[1]));
//                        mAccZTextView.setText(String.format(Locale.getDefault(), "Z: %s", sensorEvent.values[2]));
//                    }
//                }
//
//                @Override
//                public void onAccuracyChanged(Sensor sensor, int i) {
//
//                }
//
//            };
//
//            mSensorManager.registerListener(mSensorEventListener, mSensor, mCurrentDelayMode);
//        } else {
//            mSensorManager.unregisterListener(mSensorEventListener);
//            mAccEventButton.setText("開始");
//            mCurrentStatus = 0;
//            mAccDelayChoiceRadioGroup.setVisibility(View.VISIBLE);
//        }
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
}
