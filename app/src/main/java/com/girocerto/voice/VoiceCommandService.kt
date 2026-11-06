package com.girocerto.voice

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.speech.*
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import com.girocerto.app.MainActivity
import com.girocerto.monitoring.GiroCertoAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class VoiceCommandService : Service(), RecognitionListener, TextToSpeech.OnInitListener {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var readyToSpeak = false
    private var pending: PendingAction? = null
    private var listening = false

    private sealed interface PendingAction {
        data class Reject(val packageName: String) : PendingAction
        data class Disconnect(val app: WorkApp?, val packageName: String) : PendingAction
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        tts = TextToSpeech(this, this)
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(this) }
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVoiceCommands()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification("Aguardando seu comando"))
        _running.value = true
        scheduleListening(250)
        return START_STICKY
    }

    private fun startListening() {
        if (!_running.value || listening || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        listening = true
        _status.value = if (pending == null) "Ouvindo comando…" else "Diga confirmar ou cancelar"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        runCatching { recognizer?.startListening(intent) }.onFailure { listening = false; scheduleListening(1_000) }
    }

    private fun scheduleListening(delay: Long) {
        handler.removeCallbacksAndMessages(LISTEN_TOKEN)
        handler.postAtTime({ startListening() }, LISTEN_TOKEN, SystemClock.uptimeMillis() + delay)
    }

    override fun onResults(results: Bundle?) {
        listening = false
        val phrases = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        val command = phrases.asSequence().map(VoiceCommandParser::parse).firstOrNull { it !is VoiceCommand.Unknown } ?: VoiceCommand.Unknown
        handle(command)
    }

    private fun handle(command: VoiceCommand) {
        val confirmation = pending
        if (confirmation != null) {
            when (command) {
                VoiceCommand.Confirm -> { pending = null; execute(confirmation) }
                VoiceCommand.Cancel -> { pending = null; speak("Comando cancelado") }
                else -> speak("Diga confirmar ou cancelar")
            }
            return
        }
        when (command) {
            VoiceCommand.RejectOffer -> {
                val pkg = GiroCertoAccessibilityService.currentOfferPackage()
                if (pkg.isBlank()) speak("Não encontrei uma oferta ativa")
                else { pending = PendingAction.Reject(pkg); speak("Confirmar recusa da oferta?") }
            }
            is VoiceCommand.Disconnect -> {
                val pkg = command.app?.packages?.firstOrNull(::isInstalled)
                    ?: GiroCertoAccessibilityService.currentOfferPackage().takeIf(String::isNotBlank).orEmpty()
                if (pkg.isBlank()) speak("Diga o nome do aplicativo que deseja desconectar")
                else { pending = PendingAction.Disconnect(command.app, pkg); speak("Confirmar desconexão${command.app?.let { " do ${it.label}" }.orEmpty()}?") }
            }
            is VoiceCommand.OpenNavigation -> openNavigator(command.navigator)
            VoiceCommand.StopListening -> stopVoiceCommands()
            VoiceCommand.Cancel -> speak("Não há comando pendente")
            VoiceCommand.Confirm -> speak("Não há comando para confirmar")
            VoiceCommand.Help -> speak(helpText())
            VoiceCommand.Unknown -> scheduleListening(400)
        }
    }

    private fun helpText(): String = "Comandos de voz disponíveis: recusar ou aceitar uma oferta, " +
        "desconectar do app de transporte, abrir Waze ou Google Maps, parar de ouvir, " +
        "ou pedir ajuda. Quando um comando pede confirmação, diga apenas \"confirmar\" ou \"cancelar\"."

    private fun execute(action: PendingAction) {
        when (action) {
            is PendingAction.Reject -> {
                val ok = GiroCertoAccessibilityService.requestRejectOffer(action.packageName)
                if (ok) handler.postDelayed({
                    GiroCertoAccessibilityService.requestRejectOffer(action.packageName)
                    speak("Comando de recusa executado")
                }, 600) else speak("Não encontrei o botão de recusar")
            }
            is PendingAction.Disconnect -> {
                launchPackage(action.packageName)
                handler.postDelayed({
                    val ok = GiroCertoAccessibilityService.requestDisconnect(action.packageName)
                    if (ok) handler.postDelayed({
                        GiroCertoAccessibilityService.requestDisconnect(action.packageName)
                        speak("Comando para ficar offline executado")
                    }, 700) else speak("Não encontrei o botão ficar offline")
                }, 1_500)
            }
        }
    }

    private fun openNavigator(navigator: Navigator) {
        val pkg = if (navigator == Navigator.WAZE) "com.waze" else "com.google.android.apps.maps"
        if (launchPackage(pkg)) speak(if (navigator == Navigator.WAZE) "Abrindo Waze" else "Abrindo Google Maps")
        else speak("Aplicativo de navegação não está instalado")
    }

    private fun launchPackage(packageName: String): Boolean {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        startActivity(launch)
        return true
    }

    private fun isInstalled(packageName: String) = packageManager.getLaunchIntentForPackage(packageName) != null

    private fun speak(message: String) {
        _status.value = message
        listening = false
        runCatching { recognizer?.cancel() }
        if (readyToSpeak) tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "voice_command")
        scheduleListening((message.length * 55L).coerceIn(1_200L, 3_000L))
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            readyToSpeak = tts?.setLanguage(Locale.forLanguageTag("pt-BR"))?.let { it >= 0 } == true
        }
    }

    override fun onError(error: Int) { listening = false; if (_running.value) scheduleListening(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1_500 else 500) }
    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) {
        // Detect speech onset: when the signal rises well above the noise floor,
        // begin capture immediately instead of waiting for the next tick.
        if (listening || pending != null || rmsdB < -50f) return
        startListening()
    }
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun notification(status: String): Notification {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 2, Intent(this, VoiceCommandService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.girocerto.app.R.drawable.ic_notification)
            .setContentTitle("Comandos de voz do Giro Certo")
            .setContentText(status).setOngoing(true).setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Parar", stop).build()).build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Comandos de voz", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun stopVoiceCommands() {
        getSharedPreferences("copilot_preferences", MODE_PRIVATE).edit().putBoolean("voice_commands", false).apply()
        _running.value = false; _status.value = "Comandos de voz desativados"
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    override fun onDestroy() {
        _running.value = false; listening = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy(); recognizer = null
        tts?.shutdown(); tts = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "giro_voice_commands"
        private const val NOTIFICATION_ID = 3402
        private const val ACTION_STOP = "com.girocerto.voice.STOP"
        private val LISTEN_TOKEN = Any()
        private val _running = MutableStateFlow(false)
        val running = _running.asStateFlow()
        private val _status = MutableStateFlow("Comandos de voz desativados")
        val status = _status.asStateFlow()

        fun start(context: Context) = ContextCompat.startForegroundService(context, Intent(context, VoiceCommandService::class.java))
        fun stop(context: Context) = context.startService(Intent(context, VoiceCommandService::class.java).setAction(ACTION_STOP))
    }
}
