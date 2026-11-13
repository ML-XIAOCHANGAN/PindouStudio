package com.pindou.studio

import com.pindou.studio.core.BeadGrid
import com.pindou.studio.core.ColorMath
import com.pindou.studio.core.GridHistory
import com.pindou.studio.core.Palette
import com.pindou.studio.core.PaletteColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLogicTest {

    private fun testPalette(): Palette = Palette(
        id = "test",
        name = "测试色卡",
        colors = listOf(
            PaletteColor("A1", "A1", "#FF0000", "A", PaletteColor.TYPE_NORMAL, null),
            PaletteColor("A2", "A2", "#00FF00", "A", PaletteColor.TYPE_NORMAL, null),
            PaletteColor("B1", "B1", "#0000FF", "B", PaletteColor.TYPE_NORMAL, null),
            PaletteColor("L1", "L1", "#112233", "L", PaletteColor.TYPE_LIGHT, "#EEEEEE"),
        ),
    )

    @Test
    fun gridBasicSetGet() {
        val grid = BeadGrid(4, 3)
        assertEquals(0, grid.get(0, 0))
        grid.set(2, 1, 3)
        assertEquals(3, grid.get(2, 1))
        assertEquals(1, grid.paintedCount())
        // 越界安全
        grid.set(-1, 0, 2)
        grid.set(4, 0, 2)
        assertEquals(0, grid.get(-1, 0))
        assertEquals(1, grid.paintedCount())
    }

    @Test
    fun gridBoundsAndUsage() {
        val grid = BeadGrid(6, 6)
        grid.set(1, 1, 1)
        grid.set(3, 2, 1)
        grid.set(2, 4, 3)
        val b = grid.bounds()
        assertNotNull(b)
        assertEquals(1, b!![0])
        assertEquals(1, b[1])
        assertEquals(3, b[2])
        assertEquals(4, b[3])
        val usage = grid.colorUsage()
        assertEquals(2, usage.size)
        assertEquals(1, usage[0].first)
        assertEquals(2, usage[0].second)
    }

    @Test
    fun floodFillReplacesRegion() {
        val grid = BeadGrid(5, 5)
        grid.set(0, 0, 1)
        val changed = grid.floodFill(2, 2, 2)
        assertEquals(24, changed)
        // 已上色的 (0,0) 不属于目标区域，应保持不变
        assertEquals(1, grid.get(0, 0))
        assertEquals(2, grid.get(4, 4))
    }

    @Test
    fun floodFillStopsAtBoundary() {
        val grid = BeadGrid(3, 3)
        // 画一条竖线把画布分隔开
        for (r in 0 until 3) grid.set(1, r, 1)
        grid.floodFill(0, 0, 2)
        assertEquals(2, grid.get(0, 0))
        assertEquals(2, grid.get(0, 2))
        assertEquals(1, grid.get(1, 0))
        assertEquals(0, grid.get(2, 0))
    }

    @Test
    fun rotateAndFlip() {
        val grid = BeadGrid(3, 2)
        grid.set(0, 0, 1)
        grid.set(2, 1, 2)
        val cw = grid.rotatedClockwise()
        assertEquals(2, cw.width)
        assertEquals(3, cw.height)
        assertEquals(1, cw.get(1, 0))
        assertEquals(2, cw.get(0, 2))

        val flipped = grid.copy()
        flipped.flipHorizontal()
        assertEquals(1, flipped.get(2, 0))
        assertEquals(2, flipped.get(0, 1))
    }

    @Test
    fun resizeKeepsOffset() {
        val grid = BeadGrid(2, 2)
        grid.set(0, 0, 1)
        val bigger = grid.resized(4, 4, 1, 1)
        assertEquals(1, bigger.get(1, 1))
        val smaller = grid.resized(1, 1, 0, 0)
        assertEquals(1, smaller.get(0, 0))
    }

    @Test
    fun codeGridRoundTrip() {
        val palette = testPalette()
        val grid = BeadGrid(3, 3)
        grid.set(0, 0, 1)
        grid.set(2, 2, 4)
        val codes = grid.toCodeGrid(palette)
        assertEquals("A1", codes[0][0])
        assertEquals("", codes[1][1])
        assertEquals("L1", codes[2][2])
        val back = BeadGrid.fromCodeGrid(codes, palette)
        assertEquals(1, back.get(0, 0))
        assertEquals(4, back.get(2, 2))
    }

    @Test
    fun paletteLookups() {
        val palette = testPalette()
        assertEquals(4, palette.size)
        assertEquals(2, palette.indexOf("a2"))
        assertEquals(0, palette.indexOf("ZZ"))
        assertEquals("#00FF00", palette.colorAt(2)?.hex)
        assertNull(palette.colorAt(9))
        assertEquals(3, palette.seriesGroups().size)
        assertTrue(palette.colorAt(4)!!.isDualState)
    }

    @Test
    fun historyPatchUndoRedo() {
        val grid = BeadGrid(4, 4)
        val history = GridHistory()
        val recorder = GridHistory.StrokeRecorder(grid)
        recorder.record(0, 0, 1)
        recorder.record(1, 0, 2)
        val patch = recorder.build()
        assertNotNull(patch)
        history.push(patch!!)
        assertEquals(1, grid.get(0, 0))

        val afterUndo = history.undo(grid)
        assertNotNull(afterUndo)
        assertEquals(0, afterUndo!!.get(0, 0))
        assertEquals(0, afterUndo.get(1, 0))

        val afterRedo = history.redo(grid)
        assertNotNull(afterRedo)
        assertEquals(1, afterRedo!!.get(0, 0))
        assertEquals(2, afterRedo.get(1, 0))
    }

    @Test
    fun historySnapshotRestoresSize() {
        val grid = BeadGrid(2, 2)
        grid.set(0, 0, 1)
        val history = GridHistory()
        val before = grid.cells.copyOf()
        val resized = grid.resized(3, 3, 0, 0)
        history.push(GridHistory.Snapshot(2, 2, before))

        val undone = history.undo(resized)
        assertNotNull(undone)
        assertEquals(2, undone!!.width)
        assertEquals(1, undone.get(0, 0))

        val redone = history.redo(undone)
        assertNotNull(redone)
        assertEquals(3, redone!!.width)
    }

    @Test
    fun colorMathHexParsing() {
        assertEquals("#FF0000", ColorMath.toHex(ColorMath.parseHex("#FF0000")))
        assertEquals("#0A0B0C", ColorMath.toHex(ColorMath.parseHex("rgb(10, 11, 12)")))
        assertEquals("#FFFFFF", ColorMath.toHex(ColorMath.parseHex("#fff")))
    }

    @Test
    fun oklabNearestIsSane() {
        val red = ColorMath.rgbToOklab(255, 0, 0)
        val nearRed = ColorMath.rgbToOklab(240, 10, 10)
        val blue = ColorMath.rgbToOklab(0, 0, 255)
        assertTrue(ColorMath.oklabDistance(red, nearRed) < ColorMath.oklabDistance(red, blue))
    }

    @Test
    fun colorAdjustKeepsRange() {
        val adjusted = ColorMath.adjust(200, 100, 50, 60f, 40f, 30f)
        assertTrue(ColorMath.red(adjusted) in 0..255)
        assertTrue(ColorMath.green(adjusted) in 0..255)
        assertTrue(ColorMath.blue(adjusted) in 0..255)
    }

    @Test
    fun svgExportContainsShapes() {
        val palette = testPalette()
        val grid = BeadGrid(4, 2)
        // 一行连续两格同色，应合并成一个 rect
        grid.set(0, 0, 1)
        grid.set(1, 0, 1)
        grid.set(3, 1, 2)
        val svg = com.pindou.studio.core.SvgExporter.render(
            grid, palette,
            com.pindou.studio.core.SvgOptions(cellPx = 10, showCodes = false, showGrid = false),
        )
        assertTrue(svg.startsWith("<?xml"))
        assertTrue(svg.contains("<svg"))
        assertTrue(svg.trimEnd().endsWith("</svg>"))
        assertTrue(svg.contains("fill=\"#FF0000\""))
        assertTrue(svg.contains("fill=\"#00FF00\""))
        // 合并后同色两格 -> 一个 20 宽的矩形
        assertTrue(svg.contains("width=\"20\""))
    }

    @Test
    fun svgEscapesTitle() {
        val palette = testPalette()
        val grid = BeadGrid(2, 2)
        grid.set(0, 0, 1)
        val svg = com.pindou.studio.core.SvgExporter.render(
            grid, palette,
            com.pindou.studio.core.SvgOptions(cellPx = 10, title = "A & B <x>"),
        )
        assertTrue(svg.contains("A &amp; B &lt;x&gt;"))
    }

    @Test
    fun usageCsvListsCounts() {
        val palette = testPalette()
        val grid = BeadGrid(3, 3)
        grid.set(0, 0, 1)
        grid.set(1, 0, 1)
        grid.set(2, 2, 3)
        val csv = com.pindou.studio.core.SvgExporter.usageCsv(grid, palette)
        assertTrue(csv.startsWith("色号,数量"))
        assertTrue(csv.contains("A1,2"))
        assertTrue(csv.contains("B1,1"))
        assertTrue(csv.contains("合计,3"))
    }

    @Test
    fun pdRoundTripKeepsGridAndPalette() {
        val palette = testPalette()
        val grid = BeadGrid(5, 4)
        grid.set(0, 0, 1)
        grid.set(4, 3, 3)
        grid.set(2, 1, 4)
        val project = com.pindou.studio.core.Project(
            id = "t1",
            name = "PD 测试",
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L,
            paletteId = palette.id,
            paletteName = palette.name,
            grid = grid,
        )
        val dir = java.io.File(System.getProperty("java.io.tmpdir"), "pindou-test")
        dir.mkdirs()
        val file = java.io.File(dir, "roundtrip.pd")
        assertTrue(com.pindou.studio.data.PdFormat.write(project, palette, file))
        assertTrue(file.length() > 0)

        val read = com.pindou.studio.data.PdFormat.read(file, "t2")
        assertNotNull(read)
        val (restored, restoredPalette, hasPalette) = read!!
        assertTrue(hasPalette)
        assertEquals(5, restored.width)
        assertEquals(4, restored.height)
        assertEquals(4, restoredPalette.size)
        assertEquals(1, restored.grid.get(0, 0))
        assertEquals(3, restored.grid.get(4, 3))
        assertEquals(4, restored.grid.get(2, 1))
        assertEquals(0, restored.grid.get(1, 1))
    }

    @Test
    fun pdWithoutPaletteUsesFallback() {
        val dir = java.io.File(System.getProperty("java.io.tmpdir"), "pindou-test")
        dir.mkdirs()
        val file = java.io.File(dir, "no-palette.pd")
        file.writeText(
            """
            {"version":2,"canvas":{"width":3,"height":2},
             "grid":[["A1",null,"B1"],[null,"A2",null]]}
            """.trimIndent()
        )
        val palette = testPalette()
        val read = com.pindou.studio.data.PdFormat.read(file, "t3", palette)
        assertNotNull(read)
        val (restored, restoredPalette, hasPalette) = read!!
        assertTrue(!hasPalette)
        assertEquals(3, restored.width)
        assertEquals(2, restored.height)
        // 回退到传入的色卡（4 色）
        assertEquals(4, restoredPalette.size)
        assertEquals(1, restored.grid.get(0, 0))
        assertEquals(3, restored.grid.get(2, 0))
        assertEquals(2, restored.grid.get(1, 1))
    }

    // ---------------------------------------------------------------- MIX 叠加

    @Test
    fun mixTopCoversBaseWhenOpaque() {
        val base = BeadGrid(4, 4)
        val top = BeadGrid(4, 4)
        for (r in 0 until 4) for (c in 0 until 4) {
            base.set(c, r, 1)
            top.set(c, r, 2)
        }
        val out = com.pindou.studio.core.LayerMixer.compose(base, top, opacity = 1f)
        assertEquals(4, out.width)
        assertEquals(4, out.height)
        for (r in 0 until 4) for (c in 0 until 4) {
            assertEquals(2, out.get(c, r))
        }
    }

    @Test
    fun mixEmptyTopCellsShowBase() {
        val base = BeadGrid(3, 3)
        val top = BeadGrid(3, 3)
        for (r in 0 until 3) for (c in 0 until 3) base.set(c, r, 1)
        top.set(1, 1, 2)
        val out = com.pindou.studio.core.LayerMixer.compose(base, top, opacity = 1f)
        assertEquals(2, out.get(1, 1))
        assertEquals(1, out.get(0, 0))
        assertEquals(1, out.get(2, 2))
    }

    @Test
    fun mixMaskHidesRegion() {
        val base = BeadGrid(4, 1)
        val top = BeadGrid(4, 1)
        for (c in 0 until 4) {
            base.set(c, 0, 1)
            top.set(c, 0, 2)
        }
        val mask = com.pindou.studio.core.LayerMixer.freshMask(4, 1)
        mask[0] = 0f
        mask[1] = 0f
        val out = com.pindou.studio.core.LayerMixer.compose(base, top, mask = mask, opacity = 1f)
        assertEquals(1, out.get(0, 0))
        assertEquals(1, out.get(1, 0))
        assertEquals(2, out.get(2, 0))
        assertEquals(2, out.get(3, 0))
    }

    @Test
    fun mixHalfOpacityDithersAboutHalf() {
        val base = BeadGrid(16, 16)
        val top = BeadGrid(16, 16)
        for (r in 0 until 16) for (c in 0 until 16) {
            base.set(c, r, 1)
            top.set(c, r, 2)
        }
        val out = com.pindou.studio.core.LayerMixer.compose(base, top, opacity = 0.5f, dither = true)
        var topCount = 0
        for (r in 0 until 16) for (c in 0 until 16) if (out.get(c, r) == 2) topCount++
        assertTrue("50% 透明度下上层应占约一半，实际 $topCount/256", topCount in 64..192)
    }

    @Test
    fun mixDifferentSizesCenterAlign() {
        val base = BeadGrid(2, 2)
        val top = BeadGrid(4, 4)
        base.set(0, 0, 1)
        val out = com.pindou.studio.core.LayerMixer.compose(base, top, opacity = 1f)
        assertEquals(4, out.width)
        assertEquals(4, out.height)
        assertEquals(1, out.get(1, 1))
    }

    @Test
    fun mergePalettesKeepsBothColorSets() {
        // 下层只有粉/黄（类似「小花」），上层有紫色/品红（类似「拼豆工坊」）
        val pink = PaletteColor("F25", "粉", "#E56A8A", "F", PaletteColor.TYPE_NORMAL, null)
        val yellow = PaletteColor("F24", "黄", "#F2C43C", "F", PaletteColor.TYPE_NORMAL, null)
        val violet = PaletteColor("P1", "紫", "#6B3FA0", "P", PaletteColor.TYPE_NORMAL, null)
        val magenta = PaletteColor("P2", "品红", "#C2389B", "P", PaletteColor.TYPE_NORMAL, null)
        val lower = Palette("small", "小花", listOf(pink, yellow))
        val upper = Palette("text", "拼豆工坊", listOf(violet, magenta))

        val merged = com.pindou.studio.core.LayerMixer.mergePalettes(lower, upper)
        assertEquals(4, merged.size)
        // 下层的索引不能变
        assertEquals(1, merged.indexOf("F25"))
        assertEquals(2, merged.indexOf("F24"))
        // 上层独有的颜色必须还在
        assertEquals(3, merged.indexOf("P1"))
        assertEquals(4, merged.indexOf("P2"))

        // 上层网格重映射到合并色卡后，颜色仍是紫/品红，不会被并成粉色
        val upperGrid = BeadGrid(2, 1)
        upperGrid.set(0, 0, 1)
        upperGrid.set(1, 0, 2)
        val remappedUpper = com.pindou.studio.core.LayerMixer.remap(upperGrid, upper, merged)
        assertEquals(3, remappedUpper.get(0, 0))
        assertEquals(4, remappedUpper.get(1, 0))

        // 合成结果里两种上层的颜色都出现
        val lowerGrid = BeadGrid(2, 1)
        val out = com.pindou.studio.core.LayerMixer.compose(lowerGrid, remappedUpper, opacity = 1f)
        assertEquals(3, out.get(0, 0))
        assertEquals(4, out.get(1, 0))
    }

    @Test
    fun remapMovesIndexesBetweenPalettes() {
        val red = PaletteColor("R1", "红", "#FF0000", "R", PaletteColor.TYPE_NORMAL, null)
        val blue = PaletteColor("B1", "蓝", "#0000FF", "B", PaletteColor.TYPE_NORMAL, null)
        val from = Palette("p1", "色卡1", listOf(red, blue))
        val to = Palette("p2", "色卡2", listOf(blue, red))
        val src = BeadGrid(2, 1)
        src.set(0, 0, 1)
        src.set(1, 0, 2)
        val out = com.pindou.studio.core.LayerMixer.remap(src, from, to)
        assertEquals(2, out.get(0, 0))
        assertEquals(1, out.get(1, 0))
    }

    @Test
    fun templatesAreWellFormed() {
        val templates = com.pindou.studio.core.PatternTemplates.all
        assertTrue("模板数量应大于 0", templates.isNotEmpty())
        for (t in templates) {
            assertTrue("模板 ${t.id} 宽度应大于 0", t.width > 0)
            assertTrue("模板 ${t.id} 高度应大于 0", t.height > 0)
            // 每一行都补齐到同一宽度
            for (row in t.normalizedRows) {
                assertEquals("模板 ${t.id} 行宽不一致", t.width, row.length)
            }
            assertTrue("模板 ${t.id} 应有豆子", t.beadCount > 0)
            assertTrue("模板 ${t.id} 应有颜色", t.colorCount > 0)
            // 出现的字符必须在 colors 里，且不能是留白
            for (row in t.normalizedRows) {
                for (ch in row) {
                    if (ch == '.') continue
                    assertTrue("模板 ${t.id} 缺少字符 $ch 的颜色", t.colors.containsKey(ch))
                }
            }
        }
    }

    @Test
    fun templateLibraryHasBasicsAndUniqueIds() {
        val all = com.pindou.studio.core.PatternTemplates.all
        // id 必须唯一，否则列表 key 会撞
        assertEquals(all.size, all.map { it.id }.toSet().size)
        // 「基础形状」是新手入口，必须有内容
        assertTrue(com.pindou.studio.core.PatternTemplates.categories().contains("基础形状"))
        for (id in listOf("shape-heart", "shape-star", "shape-smiley")) {
            val t = com.pindou.studio.core.PatternTemplates.byId(id)
            assertNotNull("缺少基础形状模板 $id", t)
            assertEquals("基础形状", t!!.category)
            assertTrue("$id 应该有豆子", t.beadCount > 0)
            assertTrue("$id 不应该有留白行", t.normalizedRows.all { row -> row.any { it != '.' } })
        }
    }

    @Test
    fun templateMapsOntoPalette() {
        val palette = testPalette()
        // 模板库在 v2.2.0 整体换血，这里改成取第一张模板，避免写死某个 id
        val template = com.pindou.studio.core.PatternTemplates.all.first()
        val grid = template.toGrid(palette)
        assertEquals(template.width, grid.width)
        assertEquals(template.height, grid.height)
        assertEquals(template.beadCount, grid.paintedCount())
        // 颜色必须落在调色板范围内
        for (v in grid.cells) {
            val idx = v.toInt()
            assertTrue("索引 $idx 越界", idx == 0 || (idx in 1..palette.size))
        }
    }

    // ---------------------------------------------------------------- 撤销历史压缩

    @Test
    fun snapshotSurvivesCompression() {
        val grid = BeadGrid(200, 200)
        for (r in 0 until 200) {
            for (c in 0 until 200 step 3) grid.set(c, r, (r % 5) + 1)
        }
        val expected = grid.copy()
        val history = GridHistory()
        history.push(GridHistory.snapshotOf(grid))

        grid.flipHorizontal()
        grid.clear()
        assertEquals(0, grid.paintedCount())

        val restored = history.undo(grid)!!
        assertEquals(200, restored.width)
        for (r in 0 until 200) {
            for (c in 0 until 200) {
                assertEquals(expected.get(c, r), restored.get(c, r))
            }
        }
    }

    @Test
    fun snapshotOfDoesNotRetainTheLiveArray() {
        val grid = BeadGrid(32, 32)
        grid.set(1, 1, 3)
        grid.set(5, 6, 7)
        val snapshot = GridHistory.snapshotOf(grid)
        // 快照拍完之后继续改画布，不应该影响快照内容
        grid.set(1, 1, 9)
        grid.set(5, 6, 9)
        val restored = snapshot.restore()
        assertEquals(3, restored[1 * 32 + 1].toInt())
        assertEquals(7, restored[6 * 32 + 5].toInt())
    }

    @Test
    fun snapshotsAreCompressedNotRawCopies() {
        // 稀疏画布：压缩后应该远小于「每格 2 字节」的裸拷贝
        val grid = BeadGrid(512, 512)
        for (i in 0 until 512) grid.set(i, i, 2)
        val snapshot = GridHistory.snapshotOf(grid)
        val rawSize = 512 * 512 * 2
        assertTrue(
            "压缩后 ${snapshot.byteSize} 字节，应远小于裸拷贝 ${rawSize} 字节",
            snapshot.byteSize < rawSize / 10,
        )
    }

    @Test
    fun snapshotBudgetEvictsOldestSnapshots() {
        val grid = BeadGrid(256, 256)
        for (i in 0 until 256) grid.set(i, 0, 1)
        val oneSnapshot = GridHistory.snapshotOf(grid).byteSize.toLong()
        assertTrue(oneSnapshot > 0)
        // 预算只够放 3 张左右
        val history = GridHistory(limit = 80, snapshotBudgetBytes = oneSnapshot * 3 + oneSnapshot / 2)
        repeat(8) { history.push(GridHistory.snapshotOf(grid)) }
        assertTrue("快照字节数应受预算约束", history.snapshotBytes() <= oneSnapshot * 3 + oneSnapshot / 2)
        assertTrue("应保留最近的若干步", history.undoCount in 1..8)
    }

    @Test
    fun patchHistoryIsUnaffectedByBudget() {
        val grid = BeadGrid(16, 8)
        val history = GridHistory(limit = 80, snapshotBudgetBytes = 1)
        repeat(10) { i ->
            val recorder = GridHistory.StrokeRecorder(grid)
            recorder.record(i, 0, 1)
            history.push(recorder.build()!!)
        }
        assertEquals(10, history.undoCount)
        assertEquals(0, history.snapshotBytes())
    }

    @Test
    fun colorCombosAreConsistent() {
        val combos = com.pindou.studio.core.ColorCombos.all
        assertTrue(combos.isNotEmpty())
        val ids = combos.map { it.id }.toSet()
        assertEquals(combos.size, ids.size)
        for (combo in combos) {
            assertEquals("配色 ${combo.id} 应为 4 色", 4, combo.hexes.size)
            for (hex in combo.hexes) {
                assertTrue("配色 ${combo.id} 颜色格式错误: $hex", Regex("^#[0-9A-Fa-f]{6}$").matches(hex))
            }
        }
    }
}
