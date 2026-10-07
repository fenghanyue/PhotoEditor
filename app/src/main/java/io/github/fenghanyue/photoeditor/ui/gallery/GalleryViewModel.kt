package io.github.fenghanyue.photoeditor.ui.gallery

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.fenghanyue.photoeditor.media.MediaAccess
import io.github.fenghanyue.photoeditor.media.MediaBucket
import io.github.fenghanyue.photoeditor.media.MediaImage
import io.github.fenghanyue.photoeditor.media.MediaPermissions
import io.github.fenghanyue.photoeditor.media.MediaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class GalleryState(
    val access: MediaAccess = MediaAccess.NONE,
    val loading: Boolean = false,
    val images: List<MediaImage> = emptyList(),
    val buckets: List<MediaBucket> = emptyList(),
    /** 选中的文件夹；null 表示"全部"。 */
    val selectedBucketId: Long? = null,
) {
    val visibleImages: List<MediaImage> by lazy {
        selectedBucketId?.let { id -> images.filter { it.bucketId == id } } ?: images
    }
}

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository(application)
    private var loadJob: Job? = null

    var state by mutableStateOf(GalleryState())
        private set

    /** 每次回到相册页都重新查一遍：权限可能变了，也可能新拷进来了照片。 */
    fun refresh() {
        val access = MediaPermissions.access(getApplication())
        loadJob?.cancel()
        if (access == MediaAccess.NONE) {
            state = GalleryState(access = access)
            return
        }
        state = state.copy(access = access, loading = state.images.isEmpty())
        loadJob = viewModelScope.launch {
            val images = repository.loadImages()
            val buckets = MediaRepository.buckets(images)
            state = state.copy(
                loading = false,
                images = images,
                buckets = buckets,
                selectedBucketId = state.selectedBucketId?.takeIf { id -> buckets.any { it.id == id } },
            )
        }
    }

    fun selectBucket(id: Long?) {
        state = state.copy(selectedBucketId = id)
    }
}
