package io.github.fenghanyue.photoeditor.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fenghanyue.photoeditor.R
import io.github.fenghanyue.photoeditor.geo.Region
import io.github.fenghanyue.photoeditor.geo.RegionIndex
import kotlinx.coroutines.launch

internal const val REGION_LIST_TAG = "regionList"
internal const val REGION_SEARCH_TAG = "regionSearch"

/** 从底部弹出的地区选择。选中或清除后自动收起。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionPickerSheet(
    index: RegionIndex,
    current: Region?,
    gpsRegion: Region?,
    recent: List<Region>,
    onSelect: (Region?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        RegionPickerContent(
            index = index,
            current = current,
            gpsRegion = gpsRegion,
            recent = recent,
            onSelect = { region ->
                onSelect(region)
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) onDismiss()
                }
            },
        )
    }
}

/**
 * 地区选择的内容：搜索框；不搜索时依次是照片 GPS 所在地区、最近用过、逐级点选（省 → 市 → 区县）。
 * 有的地方没有市一级（直辖市的区、东莞这类不设区的市），点到没有下一级的地区就直接选中。
 */
@Composable
fun RegionPickerContent(
    index: RegionIndex,
    current: Region?,
    gpsRegion: Region?,
    recent: List<Region>,
    onSelect: (Region?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // 逐级点选时正在看哪个地区的下一级；null 表示在省级列表
    var browsingCode by rememberSaveable { mutableStateOf<Int?>(null) }
    val browsing = browsingCode?.let(index::find)
    val searching = query.isNotBlank()
    val results = remember(query) { index.search(query) }
    // 换了一级、或者搜索的字变了，列表都从头显示
    val listState = remember(browsingCode, query) { LazyListState() }

    // 从当时正在看的那一级往上退一级（读当前的值，不用界面上次刷新时记下的）
    fun goUp() {
        browsingCode = browsingCode?.let(index::find)?.parent?.code
    }

    BackHandler(enabled = !searching && browsing != null, onBack = ::goUp)

    fun open(region: Region) {
        if (index.hasChildren(region)) browsingCode = region.code else onSelect(region)
    }

    fun selected(region: Region) = current != null && region in current.path

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.picker_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (current != null) {
                TextButton(onClick = { onSelect(null) }) { Text(stringResource(R.string.picker_clear)) }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.picker_search_hint)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag(REGION_SEARCH_TAG),
        )
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .testTag(REGION_LIST_TAG),
            state = listState,
        ) {
            when {
                searching -> {
                    if (results.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                stringResource(R.string.picker_no_result),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                            )
                        }
                    }
                    items(results, key = { "result-${it.code}" }) { region ->
                        RegionItem(region, selected(region), showParents = true) { onSelect(region) }
                    }
                }
                browsing == null -> {
                    if (gpsRegion != null) {
                        item(key = "gps-header") { SectionHeader(stringResource(R.string.picker_gps)) }
                        item(key = "gps") {
                            RegionItem(gpsRegion, selected(gpsRegion), showParents = true) { onSelect(gpsRegion) }
                        }
                    }
                    if (recent.isNotEmpty()) {
                        item(key = "recent-header") { SectionHeader(stringResource(R.string.picker_recent)) }
                        items(recent, key = { "recent-${it.code}" }) { region ->
                            RegionItem(region, selected(region), showParents = true) { onSelect(region) }
                        }
                    }
                    item(key = "all-header") { SectionHeader(stringResource(R.string.picker_all)) }
                    items(index.provinces, key = { "child-${it.code}" }) { region ->
                        RegionItem(region, selected(region), drillable = index.hasChildren(region)) { open(region) }
                    }
                }
                else -> {
                    item(key = "up") {
                        ListItem(
                            headlineContent = { Text(browsing.path.joinToString(" / ") { it.name }) },
                            leadingContent = {
                                Icon(
                                    painterResource(R.drawable.ic_arrow_back),
                                    contentDescription = stringResource(R.string.picker_up),
                                )
                            },
                            modifier = Modifier.clickable(onClick = ::goUp),
                        )
                    }
                    item(key = "whole") {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.picker_whole, browsing.name)) },
                            colors = itemColors(current == browsing),
                            modifier = Modifier.clickable { onSelect(browsing) },
                        )
                    }
                    items(index.children(browsing), key = { "child-${it.code}" }) { region ->
                        RegionItem(region, selected(region), drillable = index.hasChildren(region)) { open(region) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** 一行地区。selected：当前选中的地区或它的上级，标上底色，逐级点选时能看出选在哪里。 */
@Composable
private fun RegionItem(
    region: Region,
    selected: Boolean,
    showParents: Boolean = false,
    drillable: Boolean = false,
    onClick: () -> Unit,
) {
    val parents = region.parent?.path?.joinToString(" / ") { it.name }
    ListItem(
        headlineContent = { Text(region.name) },
        supportingContent = if (showParents && parents != null) {
            { Text(parents) }
        } else {
            null
        },
        trailingContent = if (drillable) {
            { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) }
        } else {
            null
        },
        colors = itemColors(selected),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun itemColors(selected: Boolean) =
    if (selected) {
        ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    } else {
        ListItemDefaults.colors()
    }
