package training.eason.fftwithwearable.activities

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.Wearable
import kotlinx.android.synthetic.main.activity_main.*
import training.eason.fftwithwearable.R
import training.eason.fftwithwearable.services.ListenWearableDataService.Companion.DROWNING_EXTRA_NAME_VALUE
import training.eason.fftwithwearable.services.ListenWearableDataService.Companion.VIBRATION_MILLIS
import kotlin.concurrent.thread

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        mGoogleApiClient?.connect()
        detectConnectedWearables()
        detectDrowningNotify()

        drowningConfirmButton.setOnClickListener {
            drowningWarningTextView!!.visibility = View.INVISIBLE
            mVibrator?.cancel()
        }
    }

    override fun onDestroy() {
        mGoogleApiClient?.disconnect()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        detectDrowningNotify(intent)
    }

    private fun detectConnectedWearables() {
        thread {
            Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await().nodes
                    ?.also { _nodeList ->
                        _nodeList.forEach { _node ->
                            val wearNodeId = _node.id
                            val xx = ""
                        }



                        wearablesConnectedCountTextView.apply {
                            post {
                                text = _nodeList.size.toString()
                            }
                        }
                    }
        }
    }

    private fun detectDrowningNotify(newIntent: Intent? = null) {
        val intentObj = newIntent ?: intent

        intentObj?.getStringExtra(DROWNING_EXTRA_NAME_VALUE)?.also {
            if (it == DROWNING_EXTRA_NAME_VALUE) {
                Log.e(TAG, "drowning coming")

                drowningWarningTextView!!.visibility = View.VISIBLE
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
