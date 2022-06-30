package com.github.panpf.sketch.sample.receiver

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.RemoteViews
import com.github.panpf.sketch.request.DisplayRequest
import com.github.panpf.sketch.resize.Scale.CENTER_CROP
import com.github.panpf.sketch.sample.AssetImages
import com.github.panpf.sketch.sample.BuildConfig
import com.github.panpf.sketch.sample.R
import com.github.panpf.sketch.sample.R.id
import com.github.panpf.sketch.sketch
import com.github.panpf.sketch.target.RemoteViewsDisplayTarget
import com.github.panpf.sketch.transform.RoundedCornersTransformation
import com.github.panpf.tools4a.dimen.ktx.dp2px
import com.github.panpf.tools4a.dimen.ktx.dp2pxF

class RemoteViewsTestAppWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val BROADCAST_ACTION =
            "${BuildConfig.APPLICATION_ID}.BROADCAST_ACTION_REMOTE_VIEWS_TEST_APP_WIDGET_PROVIDER"
    }

    private val imageUris = arrayOf(
        AssetImages.STATICS[0],
        AssetImages.STATICS[2],
        AssetImages.STATICS[3]
    )
    private var imageUriIndex = 0

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        try {
            context.applicationContext.registerReceiver(this, IntentFilter(BROADCAST_ACTION))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == BROADCAST_ACTION) {
            intent.getIntExtra("appWidgetId", -1).takeIf { it != -1 }?.let {
                update(context, it)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager?,
        appWidgetIds: IntArray?
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        appWidgetIds?.forEach {
            update(context, it)
        }
    }

    private fun update(context: Context, appWidgetId: Int) {
        val nextImageUri = imageUris[imageUriIndex++ % imageUris.size]
        val remoteViews = RemoteViews(context.packageName, R.layout.remote_views_appwidget).apply {
            setOnClickPendingIntent(
                R.id.remoteViewsAppWidgetImage2,
                PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(BROADCAST_ACTION).apply {
                        putExtra("appWidgetId", appWidgetId)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        }
        context.sketch.enqueue(
            DisplayRequest(context, nextImageUri) {
                resize(200.dp2px, 200.dp2px, scale = CENTER_CROP)
                transformations(RoundedCornersTransformation(20.dp2pxF))
                target(
                    RemoteViewsDisplayTarget(
                        remoteViews = remoteViews,
                        imageViewId = id.remoteViewsAppWidgetImage1,
                        ignoreNullDrawable = true,
                        onUpdated = {
                            AppWidgetManager.getInstance(context)!!
                                .updateAppWidget(appWidgetId, remoteViews)
                        }
                    )
                )
            }
        )
    }
}