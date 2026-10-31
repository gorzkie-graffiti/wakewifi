package dev.krystian.wakewifi.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AlarmState {
    private val _isRinging = MutableStateFlow(false)
    val isRinging: StateFlow<Boolean> = _isRinging.asStateFlow()

    fun setRinging(ringing: Boolean) {
        _isRinging.value = ringing
    }
}

class AlarmEngine(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var ringtone: Ringtone? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        stopAlarm()
    }

    companion object {
        private var instance: AlarmEngine? = null

        fun startAlarm(context: Context) {
            if (instance == null) {
                instance = AlarmEngine(context.applicationContext)
            }
            instance?.startAlarm()
        }

        fun stopAlarm(context: Context) {
            instance?.stopAlarm()
            instance = null
        }
    }

    fun startAlarm() {
        try {
            stopAlarm()
            AlarmState.setRinging(true)

            // Automatic timeout after 30 seconds so it never rings infinitely
            handler.removeCallbacks(timeoutRunnable)
            handler.postDelayed(timeoutRunnable, 30_000L)

            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, alarmUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                ringtone = RingtoneManager.getRingtone(context, alarmUri)
                ringtone?.play()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            AlarmState.setRinging(false)
        }
    }

    fun stopAlarm() {
        try {
            handler.removeCallbacks(timeoutRunnable)
            AlarmState.setRinging(false)

            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null

            ringtone?.let {
                if (it.isPlaying) {
                    it.stop()
                }
            }
            ringtone = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
