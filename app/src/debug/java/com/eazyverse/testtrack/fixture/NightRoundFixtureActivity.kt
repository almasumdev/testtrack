package com.eazyverse.testtrack.fixture

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.eazyverse.testtrack.data.RoundStore
import com.eazyverse.testtrack.data.VisitCameraService
import com.eazyverse.testtrack.ui.wholeScreenCapture
import com.eazyverse.testtrack.viewmodel.NightRound

/**
 * A night round on the device with no account: Android's screen-share yes, the night camera on,
 * and a round of one app (the phone's own Settings) with the testing control floating over it.
 *
 * Debug builds only, for the device tests. The round takes its picture at its random moment as
 * a real one does; recording the visit then fails for want of a session, which is expected here.
 * Nothing here reaches the server or Drive.
 */
class NightRoundFixtureActivity : ComponentActivity() {
    private val consent =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode != Activity.RESULT_OK || data == null) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY, "refused").apply()
                finish()
                return@registerForActivityResult
            }
            VisitCameraService.start(this, result.resultCode, data)
            NightRound.start(
                this,
                listOf(RoundStore.Stop("seat-fixture", intent.getStringExtra(EXTRA_APP) ?: SETTINGS, "Settings", "")),
                float = NightRound.canFloat(this),
                pictures = true,
            )
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY, "started").apply()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY, "asking").apply()
        if (!intent.getBooleanExtra(EXTRA_CAMERA, true)) {
            // The round without the camera, to tell the control's own trouble from the camera's.
            NightRound.start(this, listOf(RoundStore.Stop("seat-fixture", SETTINGS, "Settings", "")), float = NightRound.canFloat(this))
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY, "started").apply()
            return
        }
        consent.launch(wholeScreenCapture(this))
    }

    companion object {
        const val PREFS = "night_round_fixture"
        const val KEY = "state"
        const val EXTRA_APP = "app"
        const val EXTRA_CAMERA = "camera"
        const val SETTINGS = "com.android.settings"
    }
}
