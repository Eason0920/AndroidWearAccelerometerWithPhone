package training.eason.androidwearaccelerometerwithphone.activities;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.google.android.gms.wearable.Node;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import training.eason.androidwearaccelerometerwithphone.R;

public class MainActivity extends WearableActivity {

    private static final String CALLER_EVENT = "/sendAccelerometer";
    private static final String TAG = "MainActivity";
    private static final String SIMPLE_DATE_FORMAT = "(yyyyMMdd_HHmmss)";
    private static final String ACCELEROMETER_FLODER_NAME = "accelerometer";

    @BindView(R.id.accDelayChoiceRadioGroup)
    RadioGroup mAccDelayChoiceRadioGroup;
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
    private Integer mCurrentSwimmingMode;
    private Node mNode;
    private String mAccelerometerFileName;
    private String mAccelerometerFileDateToken;
    private File mFileDir;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mUnbinder = ButterKnife.bind(this);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        assert mSensorManager != null;
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        mAccDelayChoiceRadioGroup.check(R.id.accDelayChoiceFreeStyleRadioButton);
        mAccDelayChoiceRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int id) {
                final RadioButton checkRadioButton = findViewById(id);
                mCurrentSwimmingMode = Integer.valueOf(checkRadioButton.getTag().toString());
            }
        });

        //create save accelerometer data folder
        mFileDir = getApplicationContext().getExternalFilesDir(ACCELEROMETER_FLODER_NAME);
        if (mFileDir != null && !mFileDir.exists()) {
            mFileDir.mkdir();
        }

        // Enables Always-on
        setAmbientEnabled();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        final RadioButton checkRadioButton = findViewById(mAccDelayChoiceRadioGroup.getCheckedRadioButtonId());
        mCurrentSwimmingMode = Integer.valueOf(checkRadioButton.getTag().toString());
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
        super.onDestroy();
    }

    @OnClick(R.id.accEventButton)
    protected void onAccEventButtonClick(View view) {
        if (mCurrentStatus == 0) {
            mAccEventButton.setText("停止");
            mCurrentStatus = 1;
            mAccDelayChoiceRadioGroup.setVisibility(View.GONE);

            //set fileName by radio button choice tag
            switch (mCurrentSwimmingMode) {
                case 0:
                    mAccelerometerFileName = "freeStyle";
                    break;
                case 1:
                    mAccelerometerFileName = "breaststroke";
                    break;
                case 2:
                    mAccelerometerFileName = "drowning";
                    break;
            }

            //set file date token date format
            mAccelerometerFileDateToken =
                    new SimpleDateFormat(SIMPLE_DATE_FORMAT, Locale.getDefault()).format(new Date());

            mSensorEventListener = new SensorEventListener() {

                @Override
                public void onSensorChanged(final SensorEvent sensorEvent) {
                    if (sensorEvent.accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE) {
                        final long nowMillis = TimeUnit.MILLISECONDS.convert(sensorEvent.timestamp, TimeUnit.NANOSECONDS);
                        final long diffMillis = (nowMillis - mPrevMillis);
                        mPrevMillis = nowMillis;
                        final String accX = String.valueOf(sensorEvent.values[0]);
                        final String accY = String.valueOf(sensorEvent.values[1]);
                        final String accZ = String.valueOf(sensorEvent.values[2]);

                        //show data on ui
                        mAccSamplingRate.setText(String.format("Sampling rate per second: %s", (1000 / diffMillis)));
                        mAccXTextView.setText(String.format(Locale.getDefault(), "X: %s", accX));
                        mAccYTextView.setText(String.format(Locale.getDefault(), "Y: %s", accY));
                        mAccZTextView.setText(String.format(Locale.getDefault(), "Z: %s", accZ));

                        //write sensor data to file
                        writeFileOnWearableSync(new String[]{accX, accY, accZ});
                    }
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int i) {

                }

            };

            //start sensor listener
            mSensorManager.registerListener(mSensorEventListener,
                    mSensor,
                    SensorManager.SENSOR_DELAY_FASTEST);
        } else {
            mSensorManager.unregisterListener(mSensorEventListener);
            mAccEventButton.setText("開始");
            mCurrentStatus = 0;
            mAccDelayChoiceRadioGroup.setVisibility(View.VISIBLE);
        }
    }

    /**
     * write accelerometer data file on wearable storage
     *
     * @param data accelerometer x, y, z
     */
    private synchronized void writeFileOnWearableSync(String[] data) {
        String fileFullPath = String.format("%s/%s_%s.csv",
                mFileDir,
                mAccelerometerFileName,
                mAccelerometerFileDateToken
        );

        File file = new File(fileFullPath);
        CSVWriter csvWriter = null;
        String[] contents;

        try {

            //File exist
            if (file.exists() && !file.isDirectory()) {
                csvWriter = new CSVWriter(new FileWriter(fileFullPath, true));
            } else {    //File not exist
                csvWriter = new CSVWriter(new FileWriter(fileFullPath));
                contents = new String[]{"acc_x", "acc_y", "acc_z"};
                csvWriter.writeNext(contents);
            }

            contents = data;
            csvWriter.writeNext(contents);
            csvWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            try {
                assert csvWriter != null;
                csvWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
