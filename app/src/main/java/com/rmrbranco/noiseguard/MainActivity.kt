package com.rmrbranco.noiseguard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.rmrbranco.noiseguard.ui.theme.NoiseGuardTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.log10
import kotlin.math.pow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import kotlinx.coroutines.CoroutineScope
import java.util.Calendar
import kotlinx.coroutines.Job

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Solicita permissão para o microfone
        requestMicrophonePermission()

        // Configura para manter a tela sempre ligada
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            NoiseGuardTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black) // Define o fundo preto aqui
                ) { innerPadding ->
                    SoundMonitor(
                        modifier = Modifier
                            .padding(innerPadding)
                            .background(Color.Black) // Também define o fundo preto para o conteúdo
                    )
                }
            }
        }
    }

    private fun requestMicrophonePermission() {
        // Implementação da solicitação de permissão de microfone
    }
}

@Composable
fun SoundMonitor(modifier: Modifier = Modifier) {
    val soundLevel = remember { mutableStateOf("Nível de Som: Aguardando...") }
    var alarmActive by remember { mutableStateOf(false) }
    var currentDecibels by remember { mutableDoubleStateOf(0.0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    @Suppress("DEPRECATION") val vibrator =
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var audioRecord: AudioRecord?
    var volumeAdjustmentJob: Job? = null

    Text(
        text = soundLevel.value,
        modifier = modifier,
        color = Color.White // Define o texto em branco
    )

    LaunchedEffect(Unit) {
        val permissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (permissionGranted) {
            scope.launch(Dispatchers.Default) {
                val bufferSize = AudioRecord.getMinBufferSize(
                    44100,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    44100,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                val buffer = ShortArray(bufferSize)
                audioRecord?.startRecording()

                while (true) {
                    if (!alarmActive) {
                        val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        val amplitude = buffer.take(readSize).map { it.toDouble().pow(2) }.average()
                        currentDecibels = if (amplitude > 0) {
                            10 * log10(amplitude)
                        } else {
                            0.0
                        }

                        soundLevel.value =
                            "Nível de som em tempo real: ${currentDecibels.toInt()} dB"

                        if (currentDecibels > 65) {
                            alarmActive = true
                            soundLevel.value =
                                "Nível de Som detectado: ${currentDecibels.toInt()} dB"
                            currentDecibels = 0.0 // Reinicia currentDecibels

                            audioRecord?.stop()

                            if (mediaPlayer == null) {
                                mediaPlayer = MediaPlayer.create(context, R.raw.alarm_sound).apply {
                                    isLooping = true
                                    start()
                                }

                                // Inicia o ajuste de volume com base no horário
                                volumeAdjustmentJob = adjustVolumeBasedOnTime(mediaPlayer!!)
                            }

                            val pattern = longArrayOf(0, 500, 500)
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(pattern, 0)

                            delay(3600000)

                            mediaPlayer?.stop()
                            vibrator.cancel()
                            mediaPlayer?.release()
                            mediaPlayer = null
                            alarmActive = false

                            // Cancela o ajuste de volume quando o alarme parar
                            volumeAdjustmentJob?.cancel()

                            delay(5000)

                            audioRecord?.startRecording()
                        }
                    }

                    delay(100)
                }
            }
        } else {
            soundLevel.value = "Permissão para o microfone não concedida"
        }
    }

    Text(
        text = soundLevel.value,
        modifier = modifier
    )
}

// Função para ajustar o volume do MediaPlayer com base no horário
fun adjustVolumeBasedOnTime(mediaPlayer: MediaPlayer): Job {
    return CoroutineScope(Dispatchers.Default).launch {
        while (true) {
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (currentHour in 20..23 || currentHour in 0..9) {
                mediaPlayer.setVolume(0.5f, 0.5f) // 50% de volume
            } else {
                mediaPlayer.setVolume(1.0f, 1.0f) // 100% de volume
            }
            delay(60000) // Verifica o horário a cada minuto
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SoundMonitorPreview() {
    NoiseGuardTheme {
        SoundMonitor()
    }
}
