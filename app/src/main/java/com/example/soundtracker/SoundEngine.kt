package com.example.soundtracker

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

object SoundEngine {
    private val exec = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    /** इवेंट के सेट किए हुए साउंड (अपनी फाइल या बीप) को बजाता है */
    fun playType(c: Context, t: EventType) {
        val vol = Prefs.volume(c, t)
        val uri = Prefs.custom(c, t)
        if (uri != null) playFile(c.applicationContext, Uri.parse(uri), vol)
        else playTone(Prefs.freq(c, t), vol, t.durMs)
    }

    /** बिना किसी फाइल के जनरेट किया हुआ बीप (sine wave) */
    fun playTone(freq: Int, vol: Int, durMs: Int) {
        exec.execute {
            try {
                val rate = 22050
                val n = rate * durMs / 1000
                if (n <= 0) return@execute
                val buf = ShortArray(n)
                val amp = (vol.coerceIn(0, 100) / 100.0) * 30000.0
                val fade = (n * 0.15).toInt().coerceAtLeast(1)
                for (i in 0 until n) {
                    var env = 1.0
                    if (i < fade) env = i.toDouble() / fade
                    else if (i > n - fade) env = (n - i).toDouble() / fade
                    val s = Math.sin(2.0 * Math.PI * freq * i / rate) * amp * env
                    buf[i] = s.toInt().toShort()
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(n * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(buf, 0, n)
                track.play()
                main.postDelayed({
                    try {
                        track.release()
                    } catch (e: Exception) {
                    }
                }, durMs + 400L)
            } catch (e: Exception) {
            }
        }
    }

    /** यूज़र की चुनी हुई ऑडियो फाइल बजाता है */
    fun playFile(c: Context, uri: Uri, vol: Int) {
        main.post {
            try {
                val mp = MediaPlayer()
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                mp.setDataSource(c, uri)
                val v = vol.coerceIn(0, 100) / 100f
                mp.setVolume(v, v)
                mp.setOnPreparedListener { it.start() }
                mp.setOnCompletionListener { it.release() }
                mp.setOnErrorListener { p, _, _ ->
                    p.release()
                    true
                }
                mp.prepareAsync()
                main.postDelayed({
                    try {
                        mp.release()
                    } catch (e: Exception) {
                    }
                }, 5000)
            } catch (e: Exception) {
            }
        }
    }
}
