package com.pindou.studio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import kotlin.math.roundToInt
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pindou.studio.ui.theme.Ds

/** 13sp 次级标签。 */
@Composable
fun MetaLabel(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    Text(
        text,
        modifier = modifier,
        style = Ds.label,
        color = color ?: Ds.c.inkSoft,
        maxLines = 1,
    )
}

/** 1px 分隔线。 */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Ds.stroke)
            .background(Ds.c.hairline),
    )
}

/** 板块标题：22sp 标题 + 右侧可选计数。 */
@Composable
fun SectionHeader(title: String, trailing: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = Ds.sectionTop, bottom = Ds.s3),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, style = Ds.h2, color = Ds.c.ink)
        Spacer(Modifier.width(Ds.s2))
        if (trailing != null) {
            Text(
                trailing,
                style = Ds.label,
                color = Ds.c.inkSoft,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

/** 兼容旧调用的板块标题。 */
@Composable
fun SectionTitle(text: String, trailing: String? = null) = SectionHeader(text, trailing)

/** 主按钮：全页唯一的填充强调色按钮。 */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: PIcon? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = androidx.compose.runtime.remember { MutableInteractionSource() }
    val haptics = rememberTapHaptics()
    // 按下时图标做一次小豆弹跳
    val pressed by interaction.collectIsPressedAsState()
    val iconBounce by animateFloatAsState(
        targetValue = if (pressed) 1.22f else 1f,
        animationSpec = Motion.bouncy(),
        label = "primaryIconBounce",
    )
    Button(
        onClick = {
            haptics.performHapticFeedback(
                androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove,
            )
            onClick()
        },
        modifier = modifier.height(50.dp).pressScale(interaction, 0.95f),
        enabled = enabled,
        shape = CircleShape,
        interactionSource = interaction,
        colors = ButtonDefaults.buttonColors(
            containerColor = Ds.c.accent,
            contentColor = Ds.c.onAccent,
            disabledContainerColor = Ds.c.surfaceAlt,
            disabledContentColor = Ds.c.inkFaint,
        ),
        elevation = null,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Ds.s6),
    ) {
        if (icon != null) {
            PIconView(icon, Ds.c.onAccent, Modifier.size(18.dp).graphicsLayer {
                scaleX = iconBounce
                scaleY = iconBounce
            })
            Spacer(Modifier.width(Ds.s2))
        }
        Text(text, style = Ds.button)
    }
}

/** 次按钮：描边胶囊，中性色。 */
@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(Ds.stroke, if (enabled) Ds.c.hairline else Ds.c.hairline),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = Ds.c.ink,
            disabledContentColor = Ds.c.inkFaint,
        ),
        elevation = null,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Ds.s6),
    ) {
        Text(text, style = Ds.button)
    }
}

/** 浅色底的彩色按钮：同一套形状，只换颜色，用于次级动作。 */
@Composable
fun TintedButton(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
    icon: PIcon? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = androidx.compose.runtime.remember { MutableInteractionSource() }
    val haptics = rememberTapHaptics()
    val pressed by interaction.collectIsPressedAsState()
    val iconBounce by animateFloatAsState(
        targetValue = if (pressed) 1.22f else 1f,
        animationSpec = Motion.bouncy(),
        label = "tintedIconBounce",
    )
    // 浅底上的彩色字对比度不够，把色值往墨色压 38% 再用
    val labelColor = androidx.compose.ui.graphics.lerp(tint, Ds.c.ink, 0.38f)
    Box(
        modifier
            .height(50.dp)
            .pressScale(interaction, 0.95f)
            .clip(CircleShape)
            .background(tint.copy(alpha = if (enabled) 0.20f else 0.08f))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                haptics.performHapticFeedback(
                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove,
                )
                onClick()
            }
            .padding(horizontal = Ds.s6),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                PIconView(
                    icon,
                    if (enabled) labelColor else labelColor.copy(alpha = 0.5f),
                    Modifier.size(18.dp).graphicsLayer {
                        scaleX = iconBounce
                        scaleY = iconBounce
                    },
                )
                Spacer(Modifier.width(Ds.s2))
            }
            Text(
                text,
                style = Ds.button,
                color = if (enabled) labelColor else labelColor.copy(alpha = 0.5f),
            )
        }
    }
}

/** 纯文字链接。 */
@Composable
fun TextLink(text: String, modifier: Modifier = Modifier, color: Color? = null, onClick: () -> Unit) {
    Text(
        text,
        modifier = modifier.clickable { onClick() }.padding(vertical = Ds.s2),
        style = Ds.button,
        color = color ?: Ds.c.ink,
    )
}

/** 工具 / 图标按钮：无填充，选中时用次级底 + 墨色。 */
@Composable
fun IconSquare(
    icon: PIcon,
    label: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    size: Int = 46,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        targetValue = when {
            !enabled -> Ds.c.inkFaint
            selected -> Ds.c.clay
            else -> Ds.c.inkSoft
        },
        animationSpec = androidx.compose.animation.core.tween(220),
        label = "toolTint",
    )
    val bg by animateColorAsState(
        targetValue = when {
            !enabled -> Color.Transparent
            selected -> Ds.c.claySoft
            else -> Color.Transparent
        },
        animationSpec = androidx.compose.animation.core.tween(220),
        label = "toolBg",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = Motion.springy(),
        label = "toolScale",
    )
    val haptics = rememberTapHaptics()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(Ds.rControl))
            .background(bg)
            .width(size.dp)
            .heightIn(min = Ds.hitTarget)
            .hapticClick(haptics, enabled = enabled) { onClick() }
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        PIconView(icon, tint, Modifier.size((size * 0.48f).dp))
        if (label != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = Ds.label.copy(fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp)),
                color = tint,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 圆形色块。 */
@Composable
fun ColorSwatch(
    argb: Int,
    code: String,
    selected: Boolean,
    size: Int = 42,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(argb), CircleShape)
            .border(
                width = if (selected) 2.dp else Ds.stroke,
                color = if (selected) Ds.c.ink else Ds.c.hairline,
                shape = CircleShape,
            )
            .clickable { onClick() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (code.isNotEmpty()) {
            Text(
                code,
                style = Ds.label.copy(fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp)),
                color = if (luminanceOf(argb) > 0.55f) Color(0xFF3A342C) else Color.White,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

private fun luminanceOf(argb: Int): Float {
    val r = (argb ushr 16) and 0xFF
    val g = (argb ushr 8) and 0xFF
    val b = argb and 0xFF
    return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
}

/** 设置行。 */
@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = Ds.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Ds.body, color = Ds.c.ink)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = Ds.label, color = Ds.c.inkSoft)
            }
        }
        Spacer(Modifier.width(Ds.s3))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ds.c.onAccent,
                checkedTrackColor = Ds.c.accent,
                checkedBorderColor = Ds.c.accent,
                uncheckedThumbColor = Ds.c.inkSoft,
                uncheckedTrackColor = Ds.c.surfaceAlt,
                uncheckedBorderColor = Ds.c.hairline,
            ),
        )
    }
}

/** 带标题与数值的滑杆。 */
@Composable
fun LabeledSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String = value.toString(),
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = Ds.s1)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = Ds.body, color = Ds.c.ink)
            Text(valueText, style = Ds.label, color = Ds.c.inkSoft)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Ds.c.ink,
                activeTrackColor = Ds.c.ink,
                inactiveTrackColor = Ds.c.surfaceAlt,
            ),
        )
    }
}

/**
 * 带设计感的参数滑块：轨道里排一串「豆子」当刻度，当前值用胶囊标签显示，
 * 拖动手柄是一颗描边圆豆。支持连续与离散两种模式（[steps] > 0 时吸附刻度）。
 */
@Composable
fun BeadSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    val accent = tint ?: Ds.c.clay
    val trackColor = Ds.c.surfaceAlt
    val tickIdle = Ds.c.ink.copy(alpha = 0.18f)
    val thumbInner = Ds.c.surface
    var trackWidth by remember { mutableStateOf(0) }
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it != 0f } ?: 1f
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    fun update(x: Float) {
        if (trackWidth <= 0) return
        val raw = valueRange.start + (x / trackWidth).coerceIn(0f, 1f) * span
        val snapped = if (steps > 1) {
            val stepValue = span / (steps - 1)
            valueRange.start + ((raw - valueRange.start) / stepValue).roundToInt() * stepValue
        } else raw
        onChange(snapped.coerceIn(valueRange.start, valueRange.endInclusive))
    }

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Ds.body, color = Ds.c.ink)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                Text(valueText, style = Ds.label, color = accent)
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .onSizeChanged { trackWidth = it.width }
                .pointerInput(valueRange, steps) {
                    detectTapGestures { offset -> update(offset.x) }
                }
                .pointerInput(valueRange, steps) {
                    detectHorizontalDragGestures { change, _ -> update(change.position.x) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Canvas(Modifier.fillMaxWidth().height(30.dp)) {
                val cy = size.height / 2f
                val trackH = 6.dp.toPx()
                val radius = trackH / 2f
                // 轨道
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(0f, cy - radius),
                    size = Size(size.width, trackH),
                    cornerRadius = CornerRadius(radius, radius),
                )
                // 已选段
                drawRoundRect(
                    color = accent.copy(alpha = 0.85f),
                    topLeft = Offset(0f, cy - radius),
                    size = Size(size.width * fraction, trackH),
                    cornerRadius = CornerRadius(radius, radius),
                )
                // 豆子刻度
                val ticks = if (steps > 1) steps else 5
                for (i in 0 until ticks) {
                    val f = if (ticks == 1) 0f else i / (ticks - 1).toFloat()
                    val x = size.width * f
                    val active = f <= fraction + 0.001f
                    drawCircle(
                        color = if (active) thumbInner else tickIdle,
                        radius = if (active) 2.dp.toPx() else 2.6.dp.toPx(),
                        center = Offset(x.coerceIn(3.dp.toPx(), size.width - 3.dp.toPx()), cy),
                    )
                }
                // 手柄：一颗描边圆豆
                val hx = (size.width * fraction).coerceIn(9.dp.toPx(), size.width - 9.dp.toPx())
                drawCircle(color = accent, radius = 9.dp.toPx(), center = Offset(hx, cy))
                drawCircle(
                    color = thumbInner,
                    radius = 4.dp.toPx(),
                    center = Offset(hx, cy),
                )
            }
        }
    }
}

/**
 * 预设条：一排已存预设 + 一个「存为预设」。
 * 点预设 = 套用，长按预设 = 删除。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PresetRow(
    presets: List<com.pindou.studio.data.ParamPresets.Stored>,
    onApply: (com.pindou.studio.data.ParamPresets.Stored) -> Unit,
    onSave: () -> Unit,
    onDelete: (com.pindou.studio.data.ParamPresets.Stored) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Ds.s2),
    ) {
        items(presets, key = { it.id }) { preset ->
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(Ds.c.claySoft)
                    .border(Ds.stroke, Ds.c.clay.copy(alpha = 0.35f), CircleShape)
                    .combinedClickable(
                        onClick = { onApply(preset) },
                        onLongClick = { onDelete(preset) },
                    )
                    .padding(horizontal = Ds.s3, vertical = 7.dp),
            ) {
                Text(preset.name, style = Ds.label, color = Ds.c.ink, maxLines = 1)
            }
        }
        item {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(Ds.c.surface)
                    .border(Ds.stroke, Ds.c.hairline, CircleShape)
                    .clickable(onClick = onSave)
                    .padding(horizontal = Ds.s3, vertical = 7.dp),
            ) {
                Text("＋ 存为预设", style = Ds.label, color = Ds.c.inkSoft, maxLines = 1)
            }
        }
    }
}

/** 给预设起名字的小弹窗。 */
@Composable
fun PresetNameDialog(
    title: String = "存为预设",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { androidx.compose.runtime.mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ds.c.surface,
        title = { Text(title, style = Ds.h2, color = Ds.c.ink) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(12) },
                label = { Text("预设名称", style = Ds.label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
            ) { Text("保存", style = Ds.label, color = Ds.c.clay) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("取消", style = Ds.label, color = Ds.c.inkSoft)
            }
        },
    )
}

/** 参数分组：左侧一条色条 + 标题，组内控件统一放在白卡里。 */
@Composable
fun ParamGroup(title: String, tint: Color, content: @Composable () -> Unit) {
    Spacer(Modifier.height(Ds.s4))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = 4.dp, height = 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tint),
        )
        Spacer(Modifier.width(Ds.s2))
        Text(title, style = Ds.h3, color = Ds.c.ink)
    }
    Spacer(Modifier.height(Ds.s2))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ds.rPanel))
            .background(Ds.c.surface)
            .padding(horizontal = Ds.s3, vertical = Ds.s3),
    ) {
        content()
    }
}

/** 胶囊选项：选中为墨色填充。 */
@Composable
fun ChipButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(CircleShape)
            .background(if (selected) Ds.c.ink else Color.Transparent)
            .border(Ds.stroke, if (selected) Color.Transparent else Ds.c.hairline, CircleShape)
            .clickable { onClick() }
            .padding(horizontal = Ds.s4, vertical = Ds.s2),
    ) {
        Text(
            text,
            style = Ds.label,
            color = if (selected) Ds.c.ground else Ds.c.inkSoft,
        )
    }
}

/** 彩色胶囊选项：选中时用该颜色的浅底 + 同色文字。 */
@Composable
fun ColoredChip(
    text: String,
    tint: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        if (selected) tint.copy(alpha = 0.16f) else Color.Transparent,
        androidx.compose.animation.core.tween(200),
        label = "chipBg",
    )
    val fg by animateColorAsState(
        if (selected) tint else Ds.c.inkSoft,
        androidx.compose.animation.core.tween(200),
        label = "chipFg",
    )
    val interaction = androidx.compose.runtime.remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(interaction, 0.94f)
            .clip(CircleShape)
            .background(bg)
            .border(
                Ds.stroke,
                if (selected) tint.copy(alpha = 0.55f) else Ds.c.hairline,
                CircleShape,
            )
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = Ds.s4, vertical = Ds.s2),
    ) {
        Text(text, style = Ds.label, color = fg)
    }
}

/** 空状态。 */
@Composable
fun EmptyState(
    icon: PIcon,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Ds.gutter, vertical = Ds.s8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PIconView(icon, Ds.c.inkFaint, Modifier.size(40.dp))
        if (title.isNotBlank()) {
            Spacer(Modifier.height(Ds.s4))
            Text(title, style = Ds.h3, color = Ds.c.ink, textAlign = TextAlign.Center)
        }
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(Ds.s2))
            Text(
                subtitle,
                style = Ds.small,
                color = Ds.c.inkSoft,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(Ds.s6))
            action()
        }
    }
}

/** 数据小块：数值 + 标签，数值可按功能上色。 */
@Composable
fun FigureBlock(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    Column(modifier) {
        Text(value, style = Ds.figure, color = color ?: Ds.c.ink)
        Spacer(Modifier.height(2.dp))
        Text(label, style = Ds.label, color = Ds.c.inkSoft)
    }
}

/**
 * 图案画框：一层淡色底 + 内嵌白画板。
 *
 * 位图本身是方角的白色底图，直接贴在卡片里会和卡片的圆角打架，
 * 所以统一套一层画板：内缩 8dp、圆角 16dp，与卡片 24dp 圆角同心（24 − 8 = 16）。
 */
@Composable
fun PatternPlate(
    bitmap: ImageBitmap?,
    tint: Color,
    modifier: Modifier = Modifier,
    /** 外框圆角：应当等于「卡片圆角 − 画板离卡边的距离」，默认 24 − 8 = 16。 */
    radius: Dp = Ds.rPanel,
    /** 白画板相对外框的内缩，内缩多少，圆角就减多少，两级保持同心。 */
    inset: Dp = Ds.s2,
    artPadding: Dp = Ds.s2,
    placeholder: @Composable () -> Unit = {},
) {
    val plateRadius = (radius - inset).coerceAtLeast(0.dp)
    // 垫板色：在表面色里掺一点木色，模拟拼豆垫板的暖调，而不是纯白
    val boardColor = androidx.compose.ui.graphics.lerp(Ds.c.surface, Ds.c.amber, 0.10f)
    Box(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(tint),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(inset)
                .clip(RoundedCornerShape(plateRadius))
                .background(boardColor)
                .border(Ds.stroke, Ds.c.hairline, RoundedCornerShape(plateRadius)),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(artPadding),
                    contentScale = ContentScale.Fit,
                )
            } else {
                placeholder()
            }
        }
    }
}

/**
 * 页面底纹：极淡的拼豆格子（默认 5% 墨色，几乎看不见，但能透出手作感）。
 */
@Composable
fun Modifier.beadGridTexture(cell: Dp = 18.dp, alpha: Float = 0.05f): Modifier {
    val ink = Ds.c.ink
    return this.drawBehind {
        val step = cell.toPx()
        if (step <= 0f) return@drawBehind
        val line = ink.copy(alpha = alpha)
        val width = 1.dp.toPx()
        var x = 0f
        while (x <= size.width) {
            drawLine(line, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), width)
            x += step
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(line, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), width)
            y += step
        }
    }
}

/**
 * 触摸热区透明扩容：视觉尺寸不动，交互尺寸至少 [min]。
 *
 * 规范：视觉 16dp、热区下限 28dp、推荐 40dp、相邻热区间隙 ≥ 4dp。
 */
fun Modifier.tapTarget(min: Dp = Ds.hitTarget): Modifier =
    this.sizeIn(minWidth = min, minHeight = min)

/** 轻触反馈（Taptic 手感），用于所有点击命中。 */
@Composable
fun rememberTapHaptics(): androidx.compose.ui.hapticfeedback.HapticFeedback =
    androidx.compose.ui.platform.LocalHapticFeedback.current

/** 带回弹反馈的点击：先震一下，再执行。 */
fun Modifier.hapticClick(
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = this.clickable(enabled = enabled) {
    haptics.performHapticFeedback(
        androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove,
    )
    onClick()
}

/** 统一卡片容器：白底 + 24 圆角 + 1px 描边。 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DsCard(
    modifier: Modifier = Modifier,
    padding: Dp = Ds.s4,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .then(if (onClick != null) Modifier.pressScale(interaction, 0.975f) else Modifier)
            // 极轻的投影，让卡片浮起来一点，但不要脏
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(Ds.rCard),
                clip = false,
                ambientColor = Ds.c.ink.copy(alpha = 0.10f),
                spotColor = Ds.c.ink.copy(alpha = 0.12f),
            )
            .clip(RoundedCornerShape(Ds.rCard))
            .background(Ds.c.surface)
            .border(Ds.stroke, Ds.c.hairline, RoundedCornerShape(Ds.rCard))
            .then(
                when {
                    onClick == null -> Modifier
                    onLongClick == null ->
                        Modifier.clickable(interactionSource = interaction, indication = null) { onClick() }
                    else -> Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onClick() },
                        onLongClick = { onLongClick() },
                    )
                },
            )
            .padding(padding),
    ) {
        content()
    }
}

/** 列表入场：淡入 + 轻微上浮，按 index 错开。 */
@Composable
fun Modifier.enterStagger(index: Int, stepMillis: Long = 55L): Modifier {
    var shown by remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * stepMillis)
        shown = true
    }
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        if (shown) 1f else 0f,
        androidx.compose.animation.core.tween(260),
        label = "enterAlpha",
    )
    val ty by androidx.compose.animation.core.animateFloatAsState(
        if (shown) 0f else 56f,
        Motion.soft(),
        label = "enterY",
    )
    return this.graphicsLayer {
        this.alpha = alpha
        translationY = ty
    }
}
