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
import android.view.View
import android.view.WindowManager
import android.widget.RadioButton
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
import kotlin.concurrent.timerTask

class MainActivity : WearableActivity(), GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private companion object {
        const val CALLER_EVENT = "/drowningNotify"      //傳送至手機的識別 key
        const val TAG = "MainActivity"
        const val ACC_DATA_SAMPLE_COUNT = 500       //每次進行傅立葉轉換的能量樣本數量
        const val DROWNING_CHECK_COUNT = 3       //連續發生溺水次數
        const val FFT_SAMPLING_INTERVAL = 20     //傅立葉資料取樣間隔 (分割成 25Hz 用)
        const val FFT_CHECK_BEGIN_INDEX = 3     //檢查是否有溺水的傅立葉數據起始判斷點(包含)
        const val FFT_CHECK_END_INDEX = 6     //檢查是否有溺水的傅立葉數據結束判斷點(不包含)
        const val FFT_DROWNING_THRESHOLD = 0.55     //傅立葉溺水閥值
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
    private var mPairPhoneNode: Node? = null
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
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private lateinit var mLocalNode: Node
    private var mPromptUnRegisteredTimer: Timer? = null
    private var mIsRegisteredMonitorStatus = false
    private val mWearableActivity: WearableActivity by lazy { this }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mFusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    // Got last known location. In some rare situations this can be null.
                }

        accEventButton.setOnClickListener { onAccEventButtonClick() }

        //發送游泳者監控要求
        sendMonitorButton.setOnClickListener {
            sexRadioGroup.findViewById<RadioButton>(sexRadioGroup.checkedRadioButtonId)?.also { _sexRadioButton ->
                if (mPairPhoneNode != null && mGoogleApiClient != null) {
                    val message = "register,${_sexRadioButton.text}"

                    Wearable.MessageApi.sendMessage(
                            mGoogleApiClient,
                            mPairPhoneNode?.id,
                            CALLER_EVENT,
                            message.toByteArray())
                            .setResultCallback { sendMessageResult ->
                                if (!sendMessageResult.status.isSuccess)
                                    Log.e(TAG, "sendMessage failure statusCode: ${sendMessageResult.status.statusCode}")
                                else {
                                    registerMonitorLayout.visibility = View.GONE
                                    unRegisteredMonitorLayout.visibility = View.VISIBLE
                                    mIsRegisteredMonitorStatus = true
                                }
                            }
                }
            } ?: kotlin.run {
                Toast.makeText(this@MainActivity, "請先選擇性別資料", Toast.LENGTH_LONG).show()
            }

        }

        //刪除游泳者監控要求
        unRegisteredMonitorButton.setOnClickListener {
            if (mPairPhoneNode != null && mGoogleApiClient != null) {
                val message = "delete"

                Wearable.MessageApi.sendMessage(
                        mGoogleApiClient,
                        mPairPhoneNode?.id,
                        CALLER_EVENT,
                        message.toByteArray())
                        .setResultCallback { sendMessageResult ->
                            if (!sendMessageResult.status.isSuccess)
                                Log.e(TAG, "sendMessage failure statusCode: ${sendMessageResult.status.statusCode}")
                            else {
                                registerMonitorLayout.visibility = View.VISIBLE
                                unRegisteredMonitorLayout.visibility = View.GONE
                                sexRadioGroup.check(sexRadioMale.id)
                                mIsRegisteredMonitorStatus = false
                            }
                        }
            }
        }

        //切換至游泳能量監控畫面
        switchMonitorButton.setOnClickListener {
            registerMonitorLayout.visibility = View.GONE
            unRegisteredMonitorLayout.visibility = View.GONE
            drowningMonitorLayout.visibility = View.VISIBLE
            switchMonitorButton.visibility = View.GONE
        }

        backRegisterLayoutButton.setOnClickListener {
            drowningMonitorLayout.visibility = View.GONE
            switchMonitorButton.visibility = View.VISIBLE

            if (mIsRegisteredMonitorStatus) {
                unRegisteredMonitorLayout.visibility = View.VISIBLE
                registerMonitorLayout.visibility = View.GONE
            } else {
                unRegisteredMonitorLayout.visibility = View.GONE
                registerMonitorLayout.visibility = View.VISIBLE
            }
        }

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
        accEventButton?.text = "啟動游泳監控"
        currentPowerTextView?.text = "0.0"
        currentStatusTextView?.text = "停止"

        if (::mSensorEventListener.isInitialized)
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
            if (!mIsRegisteredMonitorStatus) {
                mPromptUnRegisteredTimer = Timer()
                mPromptUnRegisteredTimer?.scheduleAtFixedRate(timerTask {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "請注意！您尚未註冊溺水通報事件！", Toast.LENGTH_LONG).show()
                    }
                }, 0, 5000)
            }

            backRegisterLayoutButton.visibility = View.GONE
            accWrapLayout.setBackgroundColor(Color.GREEN)
            accEventButton?.text = "停止游泳監控"
            currentStatusTextView?.text = "正常"
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

                        //將每份三軸資料進行加速度能量轉換
                        if (mAccDataAccessList.size < ACC_DATA_SAMPLE_COUNT) {
                            mAccDataAccessList.add(
                                    AccDataAccess(
                                            accX,
                                            accY,
                                            accZ,
                                            //計算能量
                                            Math.sqrt(Math.pow(accX.toDouble(), 2.0) + Math.pow(accY.toDouble(), 2.0) + Math.pow(accZ.toDouble(), 2.0)))
                            )

                            //達到 500 份資料後進行頻譜能量轉換
                            if (mAccDataAccessList.size == ACC_DATA_SAMPLE_COUNT) {
                                convertFrequencyByFFT()
                            }
                        }

//                        accSamplingRateTextView?.text = "Sampling rate per second: ${1000 / diffMillis}"
//                        accXTextView?.text = "X: $accX"
//                        accYTextView?.text = "Y: $accY"
//                        accZTextView?.text = "Z: $accZ"

//                        Log.e("HERE", "X: $accX")
//                        Log.e("HERE", "Y: $accY")
//                        Log.e("HERE", "Z: $accZ")
//                        Log.e("HERE", "power: ${Math.sqrt(Math.pow(accX.toDouble(), 2.0) + Math.pow(accY.toDouble(), 2.0) + Math.pow(accZ.toDouble(), 2.0))}")
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor, i: Int) {

                }

            }

            //per second 100 Hz
            mSensorManager?.registerListener(mSensorEventListener, mSensor, SensorManager.SENSOR_DELAY_FASTEST)
        } else {
            mSensorManager?.unregisterListener(mSensorEventListener)
            accWrapLayout.setBackgroundColor(Color.DKGRAY)
            accEventButton?.text = "啟動游泳監控"
            currentPowerTextView?.text = "0.0"
            currentStatusTextView?.text = "停止"
            mCurrentStatus = 0
            mPromptUnRegisteredTimer?.cancel()
            mPromptUnRegisteredTimer = null
            backRegisterLayoutButton.visibility = View.VISIBLE

            //每次按結束就清空資料集合
            mAccDataAccessList.clear()
            mCheckDrowningQueue.clear()
        }
    }

    /**
     * 取得與配對的行動裝置連線用的節點與自己的節點
     */
    private fun resolveNode() {
        Wearable.NodeApi.getLocalNode(mGoogleApiClient)
                .setResultCallback {
                    mLocalNode = it.node
                }

        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient)
                .setResultCallback { nodesResult ->
                    nodesResult.nodes.forEach {
                        mPairPhoneNode = it
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
        fftDo.realForward(fft)      //realForward: 轉頻譜能量(已排除一半對稱數值)、realForwardFull: 轉頻譜能量(包含一半對稱數值，長度 x 2)

        //取得當前的頻率能量區間最大值
        val rangeMaxValue =
                fft?.map {
                    (abs(it) / fft.size)     //數據轉正數，並進行常態化
                }?.filterIndexed { idx, _ ->
                    idx % FFT_SAMPLING_INTERVAL == 0      //每次取樣數據 500 個，每 20 點取一次，最後取得 25 個點(25Hz)
                }?.subList(
                        FFT_CHECK_BEGIN_INDEX, FFT_CHECK_END_INDEX      //取得要判斷的範圍數據
                )?.map {
                    "%.2f".format(it).toDouble()        //數據四捨五入到小數點第二位
                }?.max()        //取得最大值

        //最大值超過閥值視為溺水: true
        mCheckDrowningQueue.offer(rangeMaxValue!! > FFT_DROWNING_THRESHOLD)

        //目前能量數據
//        currentPowerTextView?.text = "$rangeMaxValue"
        currentPowerTextView?.text = if (rangeMaxValue > 0) "%.2f".format(rangeMaxValue + rangeMaxValue + 0.1) else "$rangeMaxValue"
//        currentPowerTextView?.setTextColor(if (rangeMaxValue > FFT_DROWNING_THRESHOLD) Color.RED else Color.WHITE)

        Log.e("RangeMaxValue: ", rangeMaxValue.toString())
        Log.e("IsNotify: ", (rangeMaxValue > FFT_DROWNING_THRESHOLD).toString())

        //檢查是否達到連續溺水警示次數並通知手機
        if ((mCheckDrowningQueue.size == DROWNING_CHECK_COUNT) && (!mCheckDrowningQueue.contains(false))) {
            accWrapLayout.setBackgroundColor(Color.RED)     //背景更改為紅色
            currentStatusTextView?.text = "溺水"
            Toast.makeText(mWearableActivity, "發送溺水通報", Toast.LENGTH_LONG).show()

            thread {
                notifyMobile()
            }.start()
        } else {
            accWrapLayout.setBackgroundColor(Color.GREEN)
            currentStatusTextView?.text = "正常"
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
        if (mPairPhoneNode != null && mGoogleApiClient != null) {
            val message = "drowning"

            Wearable.MessageApi.sendMessage(
                    mGoogleApiClient,
                    mPairPhoneNode?.id,
                    CALLER_EVENT,
                    message.toByteArray())
                    .setResultCallback { sendMessageResult ->
                        if (!sendMessageResult.status.isSuccess) {
                            Log.e(TAG, "sendMessage failure statusCode: ${sendMessageResult.status.statusCode}")
                        }
                    }

        } else {
            Log.e("mPairPhoneNode: ", mPairPhoneNode?.toString())
            Log.e("mGoogleApiClient: ", mGoogleApiClient.toString())
        }
    }
}
