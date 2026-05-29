package com.spendwise.app.data.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.spendwise.app.R
import com.spendwise.app.data.local.preferences.TokenManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BudgetWidget : AppWidgetProvider() {

    @Inject lateinit var tokenManager: TokenManager

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId, tokenManager)
        }
    }

    companion object {
        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int, tokenManager: TokenManager) {
            val views = RemoteViews(context.packageName, R.layout.widget_budget)
            val streak = tokenManager.spendingStreak
            val roundUp = tokenManager.roundUpSavings

            // Read daily budget from cache written by DailyPulseWorker
            val widgetPrefs = context.getSharedPreferences("widget_cache", Context.MODE_PRIVATE)
            val dailyRemaining = widgetPrefs.getFloat("daily_remaining", 0f)

            // Main number — "₹1,234" or "--" if not yet computed
            views.setTextViewText(
                R.id.widget_daily_budget,
                if (dailyRemaining > 0f) "₹${"%,.0f".format(dailyRemaining)}" else "--"
            )
            views.setTextViewText(R.id.widget_label, "left today")

            views.setTextViewText(
                R.id.widget_streak,
                when {
                    streak >= 3 -> "🔥 $streak day streak!"
                    roundUp > 0 -> "₹${"%,.0f".format(roundUp)} round-up saved"
                    else -> ""
                }
            )
            manager.updateAppWidget(widgetId, views)
        }
    }
}
