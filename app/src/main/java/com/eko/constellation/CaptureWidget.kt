package com.eko.constellation

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Home-screen bar: tap the text to type, tap the mic to speak. */
class CaptureWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_capture)
            views.setOnClickPendingIntent(R.id.widget_capture, launch(context, MainActivity.ACTION_CAPTURE, 11))
            views.setOnClickPendingIntent(R.id.widget_voice, launch(context, MainActivity.ACTION_VOICE, 12))
            manager.updateAppWidget(id, views)
        }
    }

    private fun launch(ctx: Context, action: String, code: Int): PendingIntent = PendingIntent.getActivity(
        ctx, code,
        Intent(ctx, MainActivity::class.java)
            .setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
