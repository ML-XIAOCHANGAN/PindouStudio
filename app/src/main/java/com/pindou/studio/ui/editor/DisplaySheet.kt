package com.pindou.studio.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pindou.studio.core.DisplayMode
import com.pindou.studio.core.PaletteColor
import com.pindou.studio.ui.components.ChipButton
import com.pindou.studio.ui.components.LabeledSlider
import com.pindou.studio.ui.components.SectionTitle
import com.pindou.studio.ui.components.SwitchRow
import com.pindou.studio.ui.theme.Ds

/** 显示设置面板。 */
@Composable
fun DisplaySheetContent(vm: EditorViewModel) {
    // 当前色卡里到底有哪些特殊色号——没有的话，切光效什么都不会变
    val specialCounts = remember(vm.palette) {
        val counts = LinkedHashMap<String, Int>()
        for (color in vm.palette.colors) {
            if (!color.isSpecial) continue
            val key = PaletteColor.typeLabel(color.type)
            counts[key] = (counts[key] ?: 0) + 1
        }
        counts
    }
    val specialTotal = specialCounts.values.sum()

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Ds.gutter),
    ) {
        SectionTitle("画布光效")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DisplayMode.entries.forEach { mode ->
                ChipButton(mode.label, vm.displayMode == mode) { vm.changeDisplayMode(mode) }
            }
        }
        Text(
            vm.displayMode.hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(6.dp))
        // 关键提示：这几种模式只对特殊色号有意义
        if (specialTotal == 0) {
            Text(
                "当前色卡（${vm.palette.name}）里没有特殊色号，所以切换温变 / 光变 / 特殊不会有任何变化。" +
                    "想看到效果，可以在「色卡管理」里筛「特殊色」换一套自带特殊色的色卡。",
                style = MaterialTheme.typography.labelSmall,
                color = Ds.c.rose,
            )
        } else {
            Text(
                "当前色卡（${vm.palette.name}）有 $specialTotal 个特殊色号：" +
                    specialCounts.entries.joinToString(" / ") { "${it.key} ${it.value}" } +
                    "。其它普通色号在任何模式下的颜色都一样。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionTitle("显示选项")
        SwitchRow("显示网格", checked = vm.showGrid) { vm.toggleShowGrid() }
        SwitchRow("加粗主网格（每 8 格）", checked = vm.showMajorGrid) { vm.toggleMajorGrid() }
        SwitchRow("显示色号", "放大到一定程度才显示", checked = vm.showCodes) { vm.toggleShowCodes() }
        SwitchRow("显示坐标尺", checked = vm.showRuler) { vm.toggleRuler() }
        SwitchRow("豆子显示为圆形", checked = vm.roundBeads) { vm.toggleRoundBeads() }

        SectionTitle("底图")
        Text(
            "底图是「垫在豆子下面的参考图」：把想临摹的照片铺在画布上，" +
                "按格子对着描。它只在编辑时显示，不会出现在导出的图纸里。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        SwitchRow(
            "显示底图",
            if (vm.baseImage == null) "还没有导入底图（右上角 ⋮ →「导入底图」）" else "关掉可以只看豆子",
            checked = vm.baseVisible,
        ) { vm.baseVisible = it }
        LabeledSlider(
            title = "底图不透明度",
            value = vm.baseOpacity,
            valueRange = 0f..1f,
            valueText = "${(vm.baseOpacity * 100).toInt()}%",
        ) { vm.baseOpacity = it }
        LabeledSlider(
            title = "底图缩放",
            value = vm.baseScale,
            valueRange = 0.5f..3f,
            valueText = if (vm.baseScale <= 1.001f) "100%（铺满画布）" else "${(vm.baseScale * 100).toInt()}%",
        ) { vm.baseScale = it }
        Text(
            "100% 表示参考图正好铺满画布（比例不同的图会自动裁掉多余的边），" +
                "调小可以缩小，调大继续放大，图始终只在画布里显示。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}
