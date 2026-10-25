package com.girocerto.overlay

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import com.girocerto.monitoring.CopilotPreferences
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.girocerto.domain.model.RideAnalysis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controlador de permissões e ciclo de vida da Janela Flutuante (Overlay / Bolha Flutuante).
 */
object OverlayController {

    private var owner: Context? = null
    private var boundService: RideOverlayService? = null
    private var pendingAnalysis: RideAnalysis? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            boundService = (binder as? RideOverlayService.LocalBinder)?.service
            owner?.let { context ->
                syncCopilot(context)
                pendingAnalysis?.let { showOverlay(context, it) }
                pendingAnalysis = null
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            boundService = null
            setOverlayActive(false)
        }
    }

    // The system-bound accessibility service owns the overlay's lifetime.
    fun connect(context: Context) {
        if (owner != null) return
        owner = context
        if (!context.bindService(Intent(context, RideOverlayService::class.java), connection, Context.BIND_AUTO_CREATE)) owner = null
    }

    fun disconnect() {
        pendingAnalysis = null
        boundService?.dismiss()
        owner?.unbindService(connection)
        owner = null
        boundService = null
        setOverlayActive(false)
    }

    fun syncCopilot(context: Context) {
        if (owner == null) return
        if (CopilotPreferences(context).read().enabled && canDrawOverlays(context)) boundService?.ensureBubble()
        else {
            pendingAnalysis = null
            boundService?.dismiss()
        }
    }

    private val _isOverlayActive = MutableStateFlow(false)
    val isOverlayActive: StateFlow<Boolean> = _isOverlayActive.asStateFlow()

    internal fun setOverlayActive(active: Boolean) {
        _isOverlayActive.value = active
    }

    /**
     * Verifica se o usuário concedeu permissão de sobreposição em tela (SYSTEM_ALERT_WINDOW).
     */
    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Cria a Intent para o usuário autorizar a permissão de sobreposição nas Configurações do Android.
     */
    fun createOverlayPermissionIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Inicia a bolha flutuante persistente do Copiloto Giro Certo.
     */
    fun startFloatingBubble(context: Context) {
        if (!canDrawOverlays(context) || !CopilotPreferences(context).read().enabled) return
        if (owner != null) { syncCopilot(context); return }
        val intent = Intent(context, RideOverlayService::class.java).apply {
            action = RideOverlayService.ACTION_START_BUBBLE
        }
        try {
            context.startService(intent)
        } catch (error: Exception) {
            Log.e("GiroOverlay", "Bubble service request failed: " + error.javaClass.simpleName)
        }
    }

    /**
     * Encerra a bolha flutuante e fecha o serviço de sobreposição.
     */
    fun stopFloatingBubble(context: Context) {
        pendingAnalysis = null
        if (boundService != null) boundService?.dismiss()
        else context.stopService(Intent(context, RideOverlayService::class.java))
    }

    /**
     * Inicia ou atualiza a Janela Flutuante com a análise informada.
     */
    fun showOverlay(context: Context, analysis: RideAnalysis): Boolean {
        if (!canDrawOverlays(context)) {
            Log.w("GiroOverlay", "Offer request blocked: overlay permission missing")
            return false
        }

        val intent = Intent(context, RideOverlayService::class.java).apply {
            action = RideOverlayService.ACTION_SHOW
            putExtra(RideOverlayService.EXTRA_KM_RATE, analysis.grossPerKm)
            putExtra(RideOverlayService.EXTRA_HOUR_RATE, analysis.grossPerHour)
            putExtra(RideOverlayService.EXTRA_PROFIT, analysis.estimatedProfit)
            putExtra(RideOverlayService.EXTRA_STATUS, analysis.trafficLight.name)
            putExtra(RideOverlayService.EXTRA_REASON, analysis.decisionReason)
            putExtra(RideOverlayService.EXTRA_APP_NAME, analysis.offer.appName)
            putExtra(RideOverlayService.EXTRA_DESTINATION, analysis.offer.destinationAddress)
            putExtra(RideOverlayService.EXTRA_FARE, analysis.offer.fare)
            putExtra(RideOverlayService.EXTRA_DISTANCE, analysis.offer.distanceKm)
            putExtra(RideOverlayService.EXTRA_DURATION, analysis.offer.durationMinutes)
            putExtra("pickup_distance", analysis.offer.pickupDistanceKm)
            putExtra("pickup_address", analysis.offer.pickupAddress)
            putExtra("category", analysis.offer.category)
            analysis.offer.passengerRating?.let { putExtra("rating", it) }
        }
        if (owner != null) {
            if (!CopilotPreferences(context).read().enabled) return false
            val service = boundService
            if (service == null) { pendingAnalysis = analysis; return false }
            service.onStartCommand(intent, 0, 0)
            return isOverlayActive.value
        }
        return try {
            val accepted = context.startService(intent) != null
            if (accepted) Log.i("GiroOverlay", "Offer service request accepted; waiting for window attachment")
            else Log.e("GiroOverlay", "Offer service request returned no component")
            accepted
        } catch (error: Exception) {
            Log.e("GiroOverlay", "Offer service request failed: " + error.javaClass.simpleName)
            false
        }
    }

    /**
     * Encerra a Janela Flutuante.
     */
    fun hideOverlay(context: Context) {
        stopFloatingBubble(context)
    }
}
