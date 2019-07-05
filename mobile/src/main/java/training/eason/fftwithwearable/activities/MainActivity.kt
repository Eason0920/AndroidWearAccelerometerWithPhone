package training.eason.fftwithwearable.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.Wearable
import kotlinx.android.synthetic.main.activity_main.*
import training.eason.fftwithwearable.R
import training.eason.fftwithwearable.services.ListenWearableDataService.Companion.VIBRATION_MILLIS

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private val mGoogleApiClient: GoogleApiClient? by lazy {
        GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build()
    }

    private val mVibrator: Vibrator? by lazy { getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }
    private val mCurrentDrowningIdSet = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        mGoogleApiClient?.connect()
        detectConnectedWearables()
        detectEvents()

        drowningConfirmButton.apply {
            setOnClickListener {
                this.visibility = View.INVISIBLE
                drowningWarningTextView!!.visibility = View.INVISIBLE
                mVibrator?.cancel()
                mCurrentDrowningIdSet.clear()
                for (i in 0 until registerListGridLayout.childCount) {
                    (registerListGridLayout.getChildAt(i) as? Button)?.setBackgroundColor(Color.GREEN)
                }
            }
        }
    }

    override fun onDestroy() {
        mGoogleApiClient?.disconnect()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        detectEvents(intent)
    }

    @SuppressLint("SetTextI18n")
    private fun detectConnectedWearables() {
//        thread {
//            Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await().nodes
//                    ?.also { _nodeList ->
//                        wearablesConnectedCountTextView.apply {
//                            post {
//                                text = _nodeList.size.toString()
//                            }
//                        }
//                    }
//        }

        val sharedPreferences = this.getSharedPreferences("drowningManager", Context.MODE_PRIVATE)
        sharedPreferences.getString("registerList", "")?.split(",")
                ?.forEach {
                    if (it.isNotBlank()) {
                        val button = Button(this).apply {
                            text = "${it.split("-")[1]}歲"
                            textSize = 20f
//                            setPadding(20, 20, 20, 20)
                            setBackgroundColor(Color.GREEN)
                            layoutParams = GridLayout.LayoutParams().apply {
                                setMargins(10, 10, 10, 10)
                                width = GridLayout.LayoutParams.WRAP_CONTENT
                                height = GridLayout.LayoutParams.WRAP_CONTENT
                                minWidth = 300
                            }
                        }

                        registerListGridLayout.addView(button)
                    }
                }

        wearablesConnectedCountTextView.text = registerListGridLayout.childCount.toString()
    }

    @SuppressLint("SetTextI18n")
    private fun detectEvents(newIntent: Intent? = null) {
        drowningConfirmButton.visibility = View.INVISIBLE
        drowningWarningTextView!!.visibility = View.INVISIBLE
        val intentObj = newIntent ?: intent

        intentObj?.getStringExtra("event")?.also { _event ->
            when (_event) {
                "register", "delete", "drowning" -> {
                    intentObj.getStringExtra("drowningId")?.also { _drowningId ->
                        mCurrentDrowningIdSet.add(_drowningId)
                    }

                    registerListGridLayout.removeAllViews()
                    val sharedPreferences = this.getSharedPreferences("drowningManager", Context.MODE_PRIVATE)
                    sharedPreferences.getString("registerList", "")?.split(",")
                            ?.forEach {
                                if (it.isNotBlank()) {
                                    val button = Button(this).apply {
                                        text = "${it.split("-")[1]}歲"
                                        textSize = 20f
//                                        setPadding(20, 20, 20, 20)
                                        setBackgroundColor(
                                                if (mCurrentDrowningIdSet.contains(it.split("-")[0]))
                                                    Color.RED
                                                else
                                                    Color.GREEN)
                                        layoutParams = GridLayout.LayoutParams().apply {
                                            setMargins(10, 10, 10, 10)
                                            width = GridLayout.LayoutParams.WRAP_CONTENT
                                            height = GridLayout.LayoutParams.WRAP_CONTENT
                                            minWidth = 300
                                        }
                                    }

                                    registerListGridLayout.addView(button)
                                }
                            }

                    wearablesConnectedCountTextView.text = registerListGridLayout.childCount.toString()

                    if (_event == "drowning") {
                        drowningConfirmButton.visibility = View.VISIBLE
                        drowningWarningTextView.visibility = View.VISIBLE
                        mVibrator?.also { _vibrator ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                _vibrator.vibrate(VibrationEffect.createOneShot(VIBRATION_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                _vibrator.vibrate(VIBRATION_MILLIS)
                            }
                        }
                    }
                }
            }
        }
    }
}
