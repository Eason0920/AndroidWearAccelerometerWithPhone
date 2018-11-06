package training.eason.fftwithwearable.activities

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.support.wearable.activity.WearableActivity
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.android.synthetic.main.activity_main.*
import org.jtransforms.fft.DoubleFFT_1D
import training.eason.fftwithwearable.R
import training.eason.fftwithwearable.libs.LimitQueue
import java.lang.Math.abs
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.round

class MainActivity : WearableActivity(), GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private companion object {
        const val CALLER_EVENT = "/drowningNotify"      //傳送至手機的識別 key
        const val TAG = "MainActivity"
        const val ACC_DATA_SAMPLE_COUNT = 500       //每次進行傅立葉轉換的能量樣本數量
        const val DROWNING_CHECK_COUNT = 3       //連續發生溺水次數
        const val FFT_SAMPLING_INTERVAL = 10     //傅立葉資料取樣間隔 (分割成 50Hz 用)
        const val FFT_CHECK_BEGIN_INDEX = 4     //檢查是否有溺水的傅立葉數據起始判斷點(包含)
        const val FFT_CHECK_END_INDEX = 9     //檢查是否有溺水的傅立葉數據結束判斷點(不包含)
        const val FFT_DROWNING_THRESHOLD = 715     //傅立葉溺水閥值
    }

    //存放加速度資料的類別物件
    data class AccDataAccess(var mAccX: Float,
                             var mAccY: Float,
                             var mAccZ: Float,
                             var mPower: Double
    )

    private val mSensorManager: SensorManager? by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    private val mSensor: Sensor? by lazy { mSensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    private var mCurrentStatus = 0
    private var mPrevMillis = 0L
    private var mNode: Node? = null
    private val mAccDataAccessList = arrayListOf<AccDataAccess>()
    private val mCheckDrowningQueue = LimitQueue<Boolean>(DROWNING_CHECK_COUNT)
    private val mGoogleApiClient: GoogleApiClient? by lazy {
        GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build()
    }

    private lateinit var mSensorEventListener: SensorEventListener
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val mWearableActivity: WearableActivity by lazy { this }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    // Got last known location. In some rare situations this can be null.
                    val ss = ""
                }

        accEventButton.setOnClickListener { onAccEventButtonClick() }

        //開啟微光模式
        setAmbientEnabled()

        //需保持螢幕常亮，以避免微光模式下無法發送訊息至手機
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onStart() {
        super.onStart()
        mGoogleApiClient?.connect()
    }

    override fun onPause() {
        accEventButton?.text = "開始"
        currentPowerTextView?.text = ""
        mSensorManager?.unregisterListener(mSensorEventListener)
        super.onPause()
    }

    override fun onDestroy() {
        mGoogleApiClient?.disconnect()
        super.onDestroy()
    }

    override fun onConnected(bundle: Bundle?) = resolveNode()

    override fun onConnectionSuspended(i: Int) {

    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        Log.e(TAG, "GoogleClientApi connect failure: ${connectionResult.errorMessage}")
    }

    private fun onAccEventButtonClick() {
        if (mCurrentStatus == 0) {
            accEventButton?.text = "停止"
            mCurrentStatus = 1
            mSensorEventListener = object : SensorEventListener {

                @SuppressLint("SetTextI18n")
                override fun onSensorChanged(sensorEvent: SensorEvent) {
                    if (sensorEvent.accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE) {
                        val nowMillis = TimeUnit.MILLISECONDS.convert(sensorEvent.timestamp, TimeUnit.NANOSECONDS)
                        val diffMillis = nowMillis - mPrevMillis
                        mPrevMillis = nowMillis

                        val accX = sensorEvent.values[0]
                        val accY = sensorEvent.values[1]
                        val accZ = sensorEvent.values[2]

                        //每收集 500 個點進行傅立葉轉換，判斷是否有發生溺水
                        if (mAccDataAccessList.size < ACC_DATA_SAMPLE_COUNT) {
                            mAccDataAccessList.add(
                                    AccDataAccess(
                                            accX,
                                            accY,
                                            accZ,
                                            //計算能量
                                            Math.sqrt(Math.pow(accX.toDouble(), 2.0) + Math.pow(accY.toDouble(), 2.0) + Math.pow(accZ.toDouble(), 2.0)))
                            )

                            if (mAccDataAccessList.size == ACC_DATA_SAMPLE_COUNT) {
                                convertFrequencyByFFT()
                            }
                        }

//                        accSamplingRateTextView?.text = "Sampling rate per second: ${1000 / diffMillis}"
//                        accXTextView?.text = "X: $accX"
//                        accYTextView?.text = "Y: $accY"
//                        accZTextView?.text = "Z: $accZ"
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor, i: Int) {

                }

            }

            //per second 100 Hz
            mSensorManager?.registerListener(mSensorEventListener, mSensor, SensorManager.SENSOR_DELAY_FASTEST)
        } else {
            mSensorManager?.unregisterListener(mSensorEventListener)
            accEventButton?.text = "開始"
            currentPowerTextView?.text = ""
            mCurrentStatus = 0

            //每次按結束就清空資料集合
            mAccDataAccessList.clear()
            mCheckDrowningQueue.clear()
        }
    }

    /**
     * 取得與配對的行動裝置連線用的節點
     */
    private fun resolveNode() {
        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient)
                .setResultCallback { nodesResult ->
                    nodesResult.nodes.forEach {
                        mNode = it
                    }
                }
    }

    /**
     * 將能量利用傅立葉轉換為頻率數據
     */
    private fun convertFrequencyByFFT() {

        //取出計算後的能量數據
        val inputAccDataPowers = DoubleArray(mAccDataAccessList.size)
        mAccDataAccessList.indices.forEach {
            inputAccDataPowers[it] = mAccDataAccessList[it].mPower
        }

        //利用 JTransforms FFT library 轉換為頻率數據
        val fftDo = DoubleFFT_1D(inputAccDataPowers.size.toLong())
        val fft = Arrays.copyOf(inputAccDataPowers, inputAccDataPowers.size)
        fftDo.realForward(fft)

        //取得當前要判斷是否溺水的傅立葉頻率區間加總合
        val currentRangeValue = fft?.map { abs(it) }        //數據轉正數
                ?.filterIndexed { idx, _ -> idx % FFT_SAMPLING_INTERVAL == 0 }     //每次取樣數據 500 個，每 10 點取一次，最後取得 50 個點(50Hz)
                ?.subList(FFT_CHECK_BEGIN_INDEX, FFT_CHECK_END_INDEX)       //取得要判斷的範圍數據
                ?.map { round(it) }        //數據四捨五入
                ?.sum()     //將範圍數據加總

        //加總數據超過閥值視為溺水: true
        mCheckDrowningQueue.offer(currentRangeValue!! > FFT_DROWNING_THRESHOLD)
        currentPowerTextView?.text = "$currentRangeValue"
        currentPowerTextView?.setTextColor(if (currentRangeValue > FFT_DROWNING_THRESHOLD) Color.RED else Color.WHITE)


        println(message = currentRangeValue)
        println(message = currentRangeValue > FFT_DROWNING_THRESHOLD)

        //檢查是否達到連續溺水警示次數並通知手機
        if ((mCheckDrowningQueue.size == DROWNING_CHECK_COUNT) && (!mCheckDrowningQueue.contains(false))) {
            Toast.makeText(mWearableActivity, "發送溺水通報", Toast.LENGTH_LONG).show()

            thread {
                notifyMobile()
            }.start()

        }

        //test
//        if (true) {
//            notifyMobile()
//        }

        //清空存放數據的集合，等待下一次資料集合進來
        mAccDataAccessList.clear()
    }

    /**
     * 發生溺水，通知手機
     */
    private fun notifyMobile() {
        if (mNode != null && mGoogleApiClient != null) {
            val message = "drowning notify"

            Wearable.MessageApi.sendMessage(
                    mGoogleApiClient,
                    mNode?.id,
                    CALLER_EVENT,
                    message.toByteArray())
                    .setResultCallback { sendMessageResult ->
                        if (!sendMessageResult.status.isSuccess) {
                            Log.e(TAG, "sendMessage failure statusCode: ${sendMessageResult.status.statusCode}")
                        }
                    }

        } else {
            Log.e("mNode: ", mNode?.toString())
            Log.e("mGoogleApiClient: ", mGoogleApiClient.toString())
        }
    }
}
