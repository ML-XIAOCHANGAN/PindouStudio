package com.pindou.studio.core

/** 配色灵感：一组 4 个颜色。 */
class ColorCombo(
    val id: String,
    val name: String,
    val note: String,
    /** 4 个十六进制颜色，顺序即展示顺序。 */
    val hexes: List<String>,
)

/** 上手小技巧。 */
class WorkshopTip(
    val index: Int,
    val title: String,
    val body: String,
)

/** 配色灵感库。 */
object ColorCombos {
    val all: List<ColorCombo> = listOf(
        ColorCombo(
            id = "latte",
            name = "奶油拿铁",
            note = "低饱和的暖调，做挂件、杯垫都不挑场景",
            hexes = listOf("#F2E2C9", "#D9B48F", "#A9784F", "#4A3B30"),
        ),
        ColorCombo(
            id = "soda",
            name = "海盐汽水",
            note = "天蓝配薄荷，夏天做钥匙扣很清爽",
            hexes = listOf("#DFF3F7", "#A8DCE7", "#5FA8D3", "#2D6A8F"),
        ),
        ColorCombo(
            id = "sakura",
            name = "樱花牛奶",
            note = "粉白过渡，小挂件和卡贴最合适",
            hexes = listOf("#FFF1F2", "#F8C8D6", "#E58BA6", "#B25A76"),
        ),
        ColorCombo(
            id = "orange",
            name = "橘子汽水",
            note = "暖橙加奶油白，秋冬挂在包上很显眼",
            hexes = listOf("#FFF3D6", "#F7C873", "#EF9440", "#C25E2E"),
        ),
        ColorCombo(
            id = "matcha",
            name = "抹茶奶绿",
            note = "绿色系治愈配方，做植物类图纸很搭",
            hexes = listOf("#F4F7E6", "#C9DE9E", "#8FBF6A", "#5A8348"),
        ),
        ColorCombo(
            id = "morandi",
            name = "莫兰迪灰紫",
            note = "低饱和灰调，几乎不挑主题",
            hexes = listOf("#EDE9EF", "#C9BFD1", "#9C8FA8", "#6B6175"),
        ),
    )
}

/** 上手技巧库。 */
object WorkshopTips {
    val all: List<WorkshopTip> = listOf(
        WorkshopTip(
            index = 1,
            title = "从中心往外拼",
            body = "先拼眼睛、脸这类最在意的部分，再往外扩，试错成本最低。",
        ),
        WorkshopTip(
            index = 2,
            title = "一行一行来",
            body = "拼制工作台是按行打点的，拼完一行再挪板子，比乱着拼快很多。",
        ),
        WorkshopTip(
            index = 3,
            title = "颜色别超过 8 种",
            body = "颜色越多越难凑齐豆子，6～8 色通常是最舒服的区间。",
        ),
        WorkshopTip(
            index = 4,
            title = "先备料再开工",
            body = "用统计里的「缺色检查」生成采购清单，避免拼到一半断了某个颜色。",
        ),
    )
}
