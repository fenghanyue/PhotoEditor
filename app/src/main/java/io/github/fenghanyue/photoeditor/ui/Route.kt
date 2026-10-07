package io.github.fenghanyue.photoeditor.ui

import android.net.Uri
import androidx.compose.runtime.saveable.Saver
import androidx.core.net.toUri

/** App 里的页面。 */
sealed interface Route {
    data object Gallery : Route

    data class Detail(val uri: Uri) : Route
}

/** 把页面栈存成字符串列表，App 被系统回收后还能回到原来的页面。 */
val RouteStackSaver = Saver<List<Route>, ArrayList<String>>(
    save = { routes ->
        ArrayList(
            routes.map { route ->
                when (route) {
                    Route.Gallery -> GALLERY
                    is Route.Detail -> DETAIL_PREFIX + route.uri
                }
            },
        )
    },
    restore = { saved ->
        saved.map { if (it.startsWith(DETAIL_PREFIX)) Route.Detail(it.removePrefix(DETAIL_PREFIX).toUri()) else Route.Gallery }
    },
)

private const val GALLERY = "gallery"
private const val DETAIL_PREFIX = "detail:"
