package com.kitchensync.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

object Widgets {
    private const val PREF = "ks_widget"

    fun pi(ctx: Context, code: Int, go: String): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).putExtra("go", go)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun text(ctx: Context): String =
        ctx.getSharedPreferences(PREF, 0).getString("shop", "Відкрийте застосунок, щоб оновити") ?: ""

    fun pushShop(ctx: Context, list: List<Shop>) {
        val open = list.filter { !it.done }
        val body = if (open.isEmpty()) "Список порожній ✓" else {
            val lines = open.take(8).joinToString("\n") { shopLine(it) }
            if (open.size > 8) lines + "\n… ще " + (open.size - 8) else lines
        }
        ctx.getSharedPreferences(PREF, 0).edit().putString("shop", body).apply()
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, ShopWidget::class.java))
        if (ids.isNotEmpty()) ShopWidget().onUpdate(ctx, mgr, ids)
    }
}

class ShopWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            val v = RemoteViews(context.packageName, R.layout.widget_shop)
            v.setTextViewText(R.id.shop_text, Widgets.text(context))
            v.setOnClickPendingIntent(R.id.widget_root, Widgets.pi(context, 11, "shop"))
            appWidgetManager.updateAppWidget(id, v)
        }
    }
}

class QuickWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            val v = RemoteViews(context.packageName, R.layout.widget_quick)
            v.setOnClickPendingIntent(R.id.btn_add, Widgets.pi(context, 21, "add"))
            v.setOnClickPendingIntent(R.id.btn_shop, Widgets.pi(context, 22, "shop"))
            v.setOnClickPendingIntent(R.id.btn_menu, Widgets.pi(context, 23, "menu"))
            v.setOnClickPendingIntent(R.id.btn_dish, Widgets.pi(context, 24, "newdish"))
            appWidgetManager.updateAppWidget(id, v)
        }
    }
}
