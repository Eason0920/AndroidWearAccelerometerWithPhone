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
import kotlinx.android.synthetic.main.activity_main.*
import training.eason.fftwithwearable.R
import training.eason.fftwithwearable.services.ListenWearableDataService.Companion.DROWNING_EXTRA_NAME_VALUE

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val VIBRATION_MILLIS = 5000L
    }

    private val mVibrator: Vibrator? by lazy { getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        detectDrowningNotify()

        drowningNoticeButton.setOnClickListener {
            drowningLabelTextView!!.visibility = View.INVISIBLE
            mVibrator?.cancel()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        detectDrowningNotify()
    }

    private fun detectDrowningNotify() {
        intent?.getStringExtra(DROWNING_EXTRA_NAME_VALUE)?.also {
            if (it == DROWNING_EXTRA_NAME_VALUE) {
                Log.e(TAG, "drowning coming")

                drowningLabelTextView!!.visibility = View.VISIBLE
                mVibrator?.also {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        it.vibrate(VibrationEffect.createOneShot(VIBRATION_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        it.vibrate(VIBRATION_MILLIS)
                    }
                }
            }
        }
    }
}
