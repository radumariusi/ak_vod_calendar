package com.ak.vodcalendar.ui.launch

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.ak.vodcalendar.AppContainer
import com.ak.vodcalendar.core.launch.ContentRef
import com.ak.vodcalendar.core.launch.LaunchPlanner
import com.ak.vodcalendar.core.model.AppSettings
import com.ak.vodcalendar.core.model.ProviderAvailability
import com.ak.vodcalendar.core.model.StreamingApp
import com.ak.vodcalendar.launch.ContentLauncher
import com.ak.vodcalendar.launch.LaunchResult
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Long press = play: the first installed app that has the title (in its country),
 * else the default player from Settings. Returns false when neither exists, so the
 * caller can open the details page instead.
 */
class QuickPlayer(context: Context, private val container: AppContainer) {
    private val context = context.applicationContext
    private val launcher = ContentLauncher(context)

    suspend fun play(
        ref: ContentRef,
        wikidataId: String?,
        availability: List<ProviderAvailability>,
        preferred: StreamingApp?,
        settings: AppSettings,
    ): Boolean {
        val ids = if (wikidataId != null) {
            withTimeoutOrNull(3_000) { container.streamingIds.ids(wikidataId, ref.type) }.orEmpty()
        } else {
            emptyMap()
        }
        val resolved = ref.copy(serviceIds = ids)
        val candidates = LaunchPlanner.streamingTargets(resolved, settings, availability, preferred) +
            listOfNotNull(LaunchPlanner.defaultPlayerTarget(resolved, settings))
        for (target in candidates) {
            val result = try {
                launcher.launch(target)
            } catch (e: Throwable) {
                continue
            }
            if (result is LaunchResult.Opened) {
                Toast.makeText(context, "▶ ${target.label}", Toast.LENGTH_SHORT).show()
                return true
            }
        }
        Toast.makeText(context, "Nu e pe aplicațiile tale. Alege un player implicit în Setări.", Toast.LENGTH_LONG).show()
        return false
    }
}

/** Opens a YouTube trailer in the YouTube app, or anything that handles the link. */
fun openTrailer(context: Context, youtubeKey: String) {
    val intents = listOf(
        Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$youtubeKey")),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$youtubeKey")),
    )
    for (intent in intents) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            return
        } catch (e: Exception) {
            // try the next one
        }
    }
    Toast.makeText(context, "Nu am găsit YouTube pe TV.", Toast.LENGTH_LONG).show()
}
