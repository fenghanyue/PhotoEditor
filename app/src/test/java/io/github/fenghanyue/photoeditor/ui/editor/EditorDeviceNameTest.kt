package io.github.fenghanyue.photoeditor.ui.editor

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import io.github.fenghanyue.photoeditor.meta.DeviceCatalog
import io.github.fenghanyue.photoeditor.meta.CaptureParameters
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 真实 ViewModel 读取不同设备的照片，验证两种方式的记忆不会串到另一台设备。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorDeviceNameTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val store = ViewModelStore()
    private val viewModel = EditorViewModel(application).also { store.put("editor", it) }

    @After
    fun clearViewModel() {
        store.clear()
    }

    private fun source(name: String, make: String, model: String): Uri {
        val file = File(application.cacheDir, name)
        requireNotNull(javaClass.classLoader?.getResourceAsStream("exif/camera_portrait.jpg")).use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_MAKE, make)
            setAttribute(ExifInterface.TAG_MODEL, model)
            saveAttributes()
        }
        return Uri.fromFile(file)
    }

    private fun open(uri: Uri) {
        viewModel.show(uri)
        // 文件读取在 IO 线程，完成后回到 Android 主线程；同时让两边的任务执行。
        val deadline = System.nanoTime() + 20_000_000_000L
        while (viewModel.state.loading && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertFalse("照片没有完成读取：$uri", viewModel.state.loading)
    }

    @Test
    fun switchingPhotosRestoresEachDevicesModeManualNameAndSelectedModel() {
        val nikon = source("nikon.jpg", "NIKON CORPORATION", "NIKON Z5_2")
        val phone = source("phone.jpg", "OnePlus", "OnePlus 15T")
        open(nikon)
        assertEquals("Nikon Z5II", viewModel.state.deviceName.displayName)
        viewModel.updateDeviceName("我的尼康")
        viewModel.selectDeviceModel(DeviceCatalog.models.first())
        // 选择模式拒绝文字输入，必须先切回手动。
        viewModel.updateDeviceName("不应覆盖选择结果")
        assertEquals("Nikon Z5 II", viewModel.state.deviceName.displayName)
        assertEquals("我的尼康", viewModel.state.deviceName.manualName)
        assertEquals("Nikon", viewModel.state.deviceName.brandOverride)

        open(phone)
        assertEquals(DeviceNameMode.MANUAL, viewModel.state.deviceName.mode)
        assertEquals("OnePlus 15T", viewModel.state.deviceName.displayName)
        assertNull(viewModel.state.deviceName.selectedModel)
        viewModel.updateDeviceName("我的一加")
        viewModel.selectDeviceModel(DeviceCatalog.models.last())
        viewModel.setDeviceNameMode(DeviceNameMode.MANUAL)

        open(nikon)
        assertEquals(DeviceNameMode.PICKER, viewModel.state.deviceName.mode)
        assertEquals("Nikon Z5 II", viewModel.state.deviceName.displayName)
        viewModel.setDeviceNameMode(DeviceNameMode.MANUAL)
        assertEquals("我的尼康", viewModel.state.deviceName.displayName)
        assertNull(viewModel.state.deviceName.brandOverride)

        open(phone)
        assertEquals("我的一加", viewModel.state.deviceName.displayName)
        viewModel.setDeviceNameMode(DeviceNameMode.PICKER)
        assertEquals("OnePlus 15T", viewModel.state.deviceName.displayName)
        assertEquals("OnePlus", viewModel.state.deviceName.brandOverride)
    }

    @Test
    fun pickerModeOnlyBecomesActiveAfterACompleteSelection() {
        open(source("no-device.jpg", "", ""))
        viewModel.setDeviceNameMode(DeviceNameMode.PICKER)
        assertEquals(DeviceNameMode.MANUAL, viewModel.state.deviceName.mode)
        viewModel.selectDeviceModel(DeviceCatalog.models.last())
        assertEquals("OnePlus 15T", viewModel.state.deviceName.displayName)
        assertEquals("OnePlus", viewModel.state.deviceName.brandOverride)
    }

    @Test
    fun captureEditsBelongToThePhotoAndRestoreDoesNotChangeTheOriginalMetadata() {
        val first = source("capture-first.jpg", "NIKON CORPORATION", "NIKON Z5_2")
        val second = source("capture-second.jpg", "NIKON CORPORATION", "NIKON Z5_2")
        open(first)
        val original = CaptureParameters.from(requireNotNull(viewModel.state.meta))
        val edited = original.copy(iso = 100, exposureBiasEv = 2.0 / 3)
        viewModel.updateCaptureParameters(edited)
        assertEquals(edited, viewModel.state.captureParameters)
        assertEquals(original, CaptureParameters.from(requireNotNull(viewModel.state.meta)))
        open(second)
        assertEquals(original, viewModel.state.captureParameters)
        assertNull(viewModel.state.parameterEdits)
        open(first)
        assertEquals(edited, viewModel.state.captureParameters)
        viewModel.updateCaptureParameters(original)
        assertNull(viewModel.state.parameterEdits)
        open(second)
        open(first)
        assertEquals(original, viewModel.state.captureParameters)
    }
}
