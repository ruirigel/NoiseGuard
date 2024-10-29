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
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.sin

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
    var consecutiveHighCount by remember { mutableIntStateOf(0) }
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

                        // Se o nível de som é maior ou igual a 60 dB
                        if (currentDecibels >= 60) {
                            consecutiveHighCount++

                            audioRecord?.stop()
                            playTone()
                            audioRecord?.startRecording()

                            soundLevel.value = "Detectado: $consecutiveHighCount vezes"


                            // Verifica se o contador atingiu 5
                            if (consecutiveHighCount >= 5) {

                                consecutiveHighCount = 0

                                alarmActive = true
                                soundLevel.value =
                                    "Alarme ativado! Nível de Som detectado: ${currentDecibels.toInt()} dB"

                                // Para a gravação de áudio
                                audioRecord?.stop()

                                if (mediaPlayer == null) {
                                    mediaPlayer =
                                        MediaPlayer.create(context, R.raw.alarm_sound).apply {
                                            isLooping = true
                                            start()
                                        }
                                    volumeAdjustmentJob = adjustVolumeBasedOnTime(mediaPlayer!!)
                                }

                                // Vibra o dispositivo
                                val pattern = longArrayOf(0, 500, 500)
                                @Suppress("DEPRECATION")
                                vibrator.vibrate(pattern, 0)

                                // Aguarda uma hora antes de parar o alarme
                                delay(3600000)

                                // Para o alarme e libera recursos
                                mediaPlayer?.stop()
                                vibrator.cancel()
                                mediaPlayer?.release()
                                mediaPlayer = null
                                alarmActive = false
                                volumeAdjustmentJob?.cancel()

                                // Reinicia a gravação após o alarme
                                audioRecord?.startRecording()
                            }
                        }
                    }

                    delay(100) // Delay para evitar uso excessivo da CPU
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
                mediaPlayer.setVolume(0.3f, 0.3f) // 50% de volume
            } else {
                mediaPlayer.setVolume(1.0f, 1.0f) // 100% de volume
            }
            delay(60000) // Verifica o horário a cada minuto
        }
    }
}

// Função que emite um beep de aviso
fun playTone(frequency: Int = 1300, durationMs: Int = 3000) {
    val sampleRate = 44100 // Taxa de amostragem padrão para áudio
    val numSamples = durationMs * sampleRate / 1000
    val audioBuffer = ShortArray(numSamples)

    // Gera a onda senoidal para a frequência desejada
    for (i in audioBuffer.indices) {
        val angle = 2.0 * Math.PI * i / (sampleRate / frequency)
        audioBuffer[i] = (sin(angle) * Short.MAX_VALUE).toInt().toShort()
    }

    // Configura o AudioTrack para reproduzir o som
    @Suppress("DEPRECATION") val audioTrack = AudioTrack(
        AudioManager.STREAM_MUSIC,
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        audioBuffer.size * 2, // Tamanho do buffer em bytes
        AudioTrack.MODE_STATIC
    )

    // Carrega e toca o som
    audioTrack.write(audioBuffer, 0, audioBuffer.size)
    audioTrack.play()

    // Libera o AudioTrack após a duração especificada
    Thread.sleep(durationMs.toLong())
    audioTrack.stop()
    audioTrack.release()
}

@Preview(showBackground = true)
@Composable
fun SoundMonitorPreview() {
    NoiseGuardTheme {
        SoundMonitor()
    }
}

