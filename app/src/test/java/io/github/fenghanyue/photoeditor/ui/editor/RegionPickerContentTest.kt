package io.github.fenghanyue.photoeditor.ui.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import io.github.fenghanyue.photoeditor.R
import io.github.fenghanyue.photoeditor.geo.Region
import io.github.fenghanyue.photoeditor.geo.TestRegions
import io.github.fenghanyue.photoeditor.ui.theme.PhotoEditorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RegionPickerContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val index = TestRegions.index
    private var selected: Region? = null
    private var selectCalls = 0

    private fun show(current: Region? = null, gpsRegion: Region? = null, recent: List<Region> = emptyList()) {
        composeRule.setContent {
            PhotoEditorTheme {
                RegionPickerContent(
                    index = index,
                    current = current,
                    gpsRegion = gpsRegion,
                    recent = recent,
                    onSelect = {
                        selected = it
                        selectCalls++
                    },
                )
            }
        }
    }

    /** 列表很长，测试屏幕小，先滚到那一行再点。 */
    private fun tap(text: String) {
        composeRule.onNodeWithTag(REGION_LIST_TAG).performScrollToNode(hasText(text))
        composeRule.onNodeWithText(text).performClick()
    }

    @Test
    fun searchAndSelect() {
        show()
        composeRule.onNodeWithTag(REGION_SEARCH_TAG).performTextInput("敦煌")
        composeRule.onNodeWithText("甘肃省 / 酒泉市").assertIsDisplayed()
        composeRule.onNodeWithText("敦煌市").performClick()
        assertEquals(620982, selected?.code)
    }

    @Test
    fun searchWithoutResult() {
        show()
        composeRule.onNodeWithTag(REGION_SEARCH_TAG).performTextInput("火星")
        composeRule.onNodeWithText(context.getString(R.string.picker_no_result)).assertIsDisplayed()
    }

    @Test
    fun drillDownProvinceCityCounty() {
        show()
        tap("甘肃省")
        tap("酒泉市")
        tap("敦煌市")
        assertEquals(620982, selected?.code)
    }

    @Test
    fun municipalityListsDistrictsDirectly() {
        show()
        tap("北京市")
        tap("朝阳区")
        assertEquals(110105, selected?.code)
    }

    @Test
    fun canChooseAWholeCityAndGoBackUp() {
        show()
        tap("甘肃省")
        tap("酒泉市")
        // 返回上一级再进去，然后只选到市
        composeRule.onNodeWithText("甘肃省 / 酒泉市").performClick()
        tap("酒泉市")
        composeRule.onNodeWithText(context.getString(R.string.picker_whole, "酒泉市")).performClick()
        assertEquals(620900, selected?.code)
    }

    @Test
    fun systemBackGoesUpOneLevel() {
        show()
        tap("甘肃省")
        tap("酒泉市")
        // 等点击处理完再按返回键
        composeRule.waitForIdle()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText(context.getString(R.string.picker_whole, "甘肃省")).assertIsDisplayed()
        assertNull(selected)
    }

    @Test
    fun regionWithoutChildrenIsSelectedRightAway() {
        show()
        tap("台湾省")
        assertEquals(710000, selected?.code)
    }

    @Test
    fun gpsRegionRecentAndClear() {
        val dunhuang = requireNotNull(index.find(620982))
        val chaoyang = requireNotNull(index.find(110105))
        show(current = dunhuang, gpsRegion = dunhuang, recent = listOf(chaoyang))

        composeRule.onNodeWithText(context.getString(R.string.picker_gps)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.picker_recent)).assertIsDisplayed()
        composeRule.onNodeWithText("朝阳区").performClick()
        assertEquals(chaoyang, selected)

        composeRule.onNodeWithText(context.getString(R.string.picker_clear)).performClick()
        assertNull(selected)
        assertEquals(2, selectCalls)
    }
}
