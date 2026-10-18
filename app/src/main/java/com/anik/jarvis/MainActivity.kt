package com.anik.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

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
                status = "মাইক্রোফোনের অনুমতি দেওয়া হয়নি"
                speak("অনিক স্যার, মাইক্রোফোনের অনুমতি প্রয়োজন।")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setupRecognizer()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0B101D)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "JARVIS",
                            fontSize = 36.sp,
                            color = Color(0xFF00E5FF)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = status,
                            fontSize = 16.sp,
                            color = Color.LightGray
                        )

                        Spacer(modifier = Modifier.height(36.dp))

                        Button(
                            onClick = { checkAndStart() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A3C4))
                        ) {
                            Text(text = if (listening) "শুনছি..." else "🎤 কথা বলুন", color = Color.White)
                        }

                        Spacer(modifier = Modifier.height(36.dp))

                        if (transcript.isNotEmpty()) {
                            Text(
                                text = "আপনি: $transcript",
                                fontSize = 18.sp,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (reply.isNotEmpty()) {
                            Text(
                                text = "JARVIS: $reply",
                                fontSize = 18.sp,
                                color = Color(0xFF00E5FF)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun setupRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    status = "কথা বলুন..."
                    listening = true
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    status = "প্রসেস করা হচ্ছে..."
                    listening = false
                }

                override fun onError(error: Int) {
                    listening = false
                    status = "পুনরায় চেষ্টা করা হচ্ছে..."
                    // Error 11 ও লুপ ফিক্স করতে ১ সেকেন্ড পর অটো-লিসেন চালু
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!listening) startListening()
                    }, 1000)
                }

                override fun onResults(results: Bundle?) {
                    listening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        transcript = text
                        processCommand(text)
                    } else {
                        restartListeningWithDelay()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun checkAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "কথা বলুন...")
        }
        recognizer?.startListening(intent)
    }

    private fun processCommand(command: String) {
        val lower = command.lowercase()
        when {
            lower.contains("ইউটিউব") || lower.contains("youtube") -> {
                val response = "জি অনিক স্যার, ইউটিউব ওপেন করছি।"
                reply = response
                speak(response)
                val launchIntent = packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                if (launchIntent != null) {
                    startActivity(launchIntent)
                }
            }
            lower.contains("সময়") || lower.contains("টাইম") -> {
                val currentTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                val response = "অনিক স্যার, এখন সময় $currentTime"
                reply = response
                speak(response)
            }
            else -> {
                val response = "জি অনিক স্যার, শুনেছি: $command"
                reply = response
                speak(response)
            }
        }
    }

    private fun speak(text: String) {
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "JARVIS_TTS")
            // বলা শেষ হলে আবার লিসেনিং চালু করার জন্য
            Handler(Looper.getMainLooper()).postDelayed({
                restartListeningWithDelay()
            }, 3000)
        }
    }

    private fun restartListeningWithDelay() {
        Handler(Looper.getMainLooper()).postDelayed({
            startListening()
        }, 1000)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("bn", "BD")
            ttsReady = true
            speak("জারভিস অনলাইন, অনিক স্যার।")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
        recognizer?.destroy()
    }
}
