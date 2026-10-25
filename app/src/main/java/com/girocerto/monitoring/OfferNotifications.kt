package com.girocerto.monitoring

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.girocerto.app.MainActivity
import com.girocerto.domain.model.RideAnalysis
import java.util.Locale

/** Optional, visible reminder of the latest analyzed offer. Never accepts a ride. */
object OfferNotifications {
    private const val CHANNEL = "ride_offers"
    fun publish(context: Context, analysis: RideAnalysis) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Ofertas analisadas", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Resumo da última oferta, com atalhos de endereço"
        })
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), flags)
        val offer = analysis.offer
        val details = listOfNotNull(
            if (offer.durationMinutes > 0)
                String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f/km · R$ %.2f/h · Lucro estimado R$ %.2f", analysis.grossPerKm, analysis.grossPerHour, analysis.estimatedProfit)
            else String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f/km · Tempo não informado · Lucro estimado R$ %.2f", analysis.grossPerKm, analysis.estimatedProfit),
            offer.pickupAddress.takeIf { it.isNotBlank() }?.let { "Embarque: $it" },
            offer.destinationAddress.takeIf { it.isNotBlank() }?.let { "Destino: $it" },
            analysis.decisionReason
        ).joinToString("\n")
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(com.girocerto.app.R.drawable.ic_notification)
            .setContentTitle(String.format(Locale.forLanguageTag("pt-BR"), "%s · R$ %.2f", offer.appName.ifBlank { "Oferta" }, offer.fare))
            .setContentText(details).setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        listOf("Embarque" to offer.pickupAddress, "Destino" to offer.destinationAddress).forEachIndexed { index, (label, address) ->
            if (address.isNotBlank()) {
                val map = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(address)}"))
                notification.addAction(0, label, PendingIntent.getActivity(context, index + 1, map, flags))
            }
        }
        manager.notify(1001, notification.build())
    }
}

