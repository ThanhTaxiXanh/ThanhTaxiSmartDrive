package com.thanhtaxi.smartdrive.navigation

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Privacy-first, opt-in speech adapter. Select ONLY an installed Vietnamese
 * voice that doesn't require network. If unavailable, remain silent. The pure
 * GuidancePromptPolicy determines when content is appropriate.
 */
class LocalVietnameseGuidance(context: Context) {
    private var tts: TextToSpeech? = null
    private var closed = false
    private var ready = false
    private val locale = Locale("vi", "VN")

    val available get() = !closed && ready

    init {
        val appContext=context.applicationContext
        val engine = TextToSpeech(appContext) { status ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (closed) return@post
                val speech=tts ?:return@post
                if(status!=TextToSpeech.SUCCESS) return@post
                val voices=runCatching { speech.voices.orEmpty() }.getOrDefault(emptySet())
                val offline=voices.filter {
                    it.locale.language=="vi" && !it.isNetworkConnectionRequired &&
                        !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
                }.sortedWith(compareByDescending<android.speech.tts.Voice> {
                    it.locale.country=="VN"
                }.thenByDescending { it.quality }).firstOrNull()
                if(offline==null) return@post
                if(speech.setVoice(offline)!=TextToSpeech.SUCCESS) return@post
                speech.setSpeechRate(0.92f)
                speech.setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                ready=true
            }
        }
        tts=engine
    }

    fun speak(message:String) {
        if(!available || message.isBlank() || message.length>380)return
        tts?.speak(message,TextToSpeech.QUEUE_FLUSH,null,"surveynav-" + System.nanoTime())
    }

    fun stop() { tts?.stop() }

    fun close() {
        closed=true
        ready=false
        tts?.stop()
        tts?.shutdown()
        tts=null
    }
}
