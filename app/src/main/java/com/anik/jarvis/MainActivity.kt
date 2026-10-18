package com.anik.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null

    private var status by mutableStateOf("প্রস্তুত")
    private var transcript by mutableStateOf("")
    private var reply by mutableStateOf("")
    private var listening by mutableStateOf(false)

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startListening()
            } else {
                status = "মাইক্রোফোনের অনুমতি দেওয়া হয়নি"
                speak("অনিক স্যার, মাইক্রোফোনের অনুমতি ছাড়া আমি আপনার কথা শুনতে পারব না।")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent { Screen() }
    }

    override fun onInit(code: Int) {
        if (code != TextToSpeech.SUCCESS) {
            status = "ভয়েস ইঞ্জিন চালু করা যায়নি"
            return
        }
        val engine = tts ?: return
        var result = engine.setLanguage(Locale.forLanguageTag("bn-BD"))
        if (result < 0) {
            result = engine.setLanguage(Locale.forLanguageTag("bn-IN"))
        }
        if (result < 0) {
            ttsReady = false
            status = "বাংলা ভয়েস পাওয়া যায়নি।"
            reply = "জি অনিক স্যার, বলুন কী করতে হবে?"
        } else {
            ttsReady = true
            speak("জি অনিক স্যার, বলুন কী করতে হবে?")
        }
    }

    private fun speak(text: String) {
        reply = text
        if (!ttsReady) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    private fun onTalkClicked() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) startListening() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status = "এই ফোনে স্পিচ রিকগনিশন সার্ভিস নেই"
            speak("অনিক স্যার, এই ফোনে কথা শোনার সার্ভিস পাওয়া যায়নি।")
            return
        }
        tts?.stop()
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(listener)
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        transcript = ""
        listening = true
        status = "শুনছি..."
        recognizer?.startListening(intent)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            status = "বুঝছি..."
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) transcript = text
        }

        override fun onResults(results: Bundle?) {
            listening = false
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (text.isNullOrBlank()) {
                status = "কিছু বুঝতে পারিনি"
                speak("দুঃখিত অনিক স্যার, আমি কিছু বুঝতে পারিনি। আবার বলবেন?")
            } else {
                transcript = text
                status = "প্রস্তুত"
                speak("জি অনিক স্যার, আপনি বলেছেন: $text")
            }
        }

        override fun onError(error: Int) {
            listening = false
            val message = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "আপনার কথা বুঝতে পারিনি, আবার বলবেন?"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "কোনো কথা শোনা যায়নি।"
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ইন্টারনেট সংযোগে সমস্যা হচ্ছে।"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "মাইক্রোফোনের অনুমতি নেই।"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "রিকগনিশন সার্ভিস ব্যস্ত, একটু পরে চেষ্টা করুন।"
                SpeechRecognizer.ERROR_AUDIO -> "অডিও রেকর্ডিংয়ে সমস্যা হয়েছে।"
                else -> "একটি সমস্যা হয়েছে (কোড $error)।"
            }
            status = "ত্রুটি"
            speak("অনিক স্যার, $message")
        }
    }

    @Composable
    private fun Screen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF050B18))
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("JARVIS", color = Color(0xFF00E5FF), fontSize = 36.sp)
            Text(
                status,
                color = if (listening) Color(0xFF00E5FF) else Color(0xFF8FA3B8),
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            Button(
                onClick = { onTalkClicked() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B8D4)),
                modifier = Modifier.padding(top = 32.dp)
            ) {
                Text("🎤 কথা বলুন", color = Color.Black, fontSize = 20.sp)
            }
            Text(
                if (transcript.isEmpty()) "" else "আপনি: $transcript",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 32.dp)
            )
            Text(
                if (reply.isEmpty()) "" else "JARVIS: $reply",
                color = Color(0xFF00E5FF),
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
