package com.dj.dot.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Size
import android.widget.RemoteViews
import com.dj.dot.DotApp
import com.dj.dot.MainActivity
import com.dj.dot.R
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Home-screen widget: cover, title in dot type, artist, play/pause and next. */
class DotWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = push(context)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE, ACTION_NEXT -> {
                val expected = context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
                    .getString(TOKEN_KEY, null)
                val supplied = runCatching { intent.getStringExtra(EXTRA_TOKEN) }.getOrNull()
                if (!tokensMatch(expected, supplied)) return
                val pending = goAsync()
                RemoteControl.send(context, done = { pending.finish() }) { c ->
                    if (intent.action == ACTION_NEXT) c.seekToNext() else if (c.isPlaying) c.pause() else c.play()
                }
            }
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        private const val ACTION_TOGGLE = "com.dj.dot.widget.TOGGLE"
        private const val ACTION_NEXT = "com.dj.dot.widget.NEXT"
        private const val EXTRA_TOKEN = "com.dj.dot.widget.ACTION_TOKEN"
        private const val TOKEN_PREFS = "dot_widget_auth"
        private const val TOKEN_KEY = "action_token"
        private val tokenLock = Any()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private fun actionToken(context: Context): String = synchronized(tokenLock) {
            val prefs = context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
            prefs.getString(TOKEN_KEY, null) ?: UUID.randomUUID().toString().also {
                check(prefs.edit().putString(TOKEN_KEY, it).commit()) { "Cannot save widget action token" }
            }
        }

        internal fun tokensMatch(expected: String?, supplied: String?): Boolean =
            expected != null && supplied != null && expected.length == supplied.length &&
                MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), supplied.toByteArray(Charsets.UTF_8))

        /** Redraws every placed widget from the engine's current state. Cheap no-op when none are placed. */
        fun push(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DotWidget::class.java))
            if (ids.isEmpty()) return
            val state = DotApp.instance.engine.state.value
            val song = state.current
            scope.launch {
                val art: Bitmap? = song?.let { s ->
                    runCatching { context.contentResolver.loadThumbnail(s.uri, Size(192, 192), null) }.getOrNull()
                }
                val views = RemoteViews(context.packageName, R.layout.widget_dot).apply {
                    setTextViewText(R.id.widget_title, song?.title ?: "Dot")
                    setTextViewText(R.id.widget_artist, song?.artist ?: "Tap to start")
                    if (art != null) setImageViewBitmap(R.id.widget_art, art) else setImageViewResource(R.id.widget_art, R.drawable.ic_launcher_foreground)
                    setImageViewResource(R.id.widget_play, if (state.playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
                    setOnClickPendingIntent(R.id.widget_play, broadcast(context, ACTION_TOGGLE, 1))
                    setOnClickPendingIntent(R.id.widget_next, broadcast(context, ACTION_NEXT, 2))
                    setOnClickPendingIntent(
                        R.id.widget_root,
                        PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
                    )
                }
                manager.updateAppWidget(ids, views)
            }
        }

        private fun broadcast(context: Context, action: String, code: Int) = PendingIntent.getBroadcast(
            context, code, Intent(context, DotWidget::class.java).setAction(action)
                .putExtra(EXTRA_TOKEN, actionToken(context)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
