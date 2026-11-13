package com.pindou.studio.core

import com.pindou.studio.data.InventoryRepository

/** 采购清单：把图纸用量与豆仓库存的差额整理成可复制的文本 / CSV。 */
object ShoppingList {

    /** 只列出还缺的色号。 */
    fun missing(needs: List<InventoryRepository.Need>): List<InventoryRepository.Need> =
        needs.filter { it.missing > 0 }

    /** 复制到聊天/购物车用的纯文本。 */
    fun asText(needs: List<InventoryRepository.Need>, projectName: String): String {
        val miss = missing(needs)
        val sb = StringBuilder()
        sb.append("拼豆采购清单 · ").append(projectName).append('\n')
        if (miss.isEmpty()) {
            sb.append("库存充足，不需要补豆。\n")
            sb.append("合计 ").append(needs.sumOf { it.required }).append(" 颗 / ")
                .append(needs.size).append(" 色")
            return sb.toString()
        }
        var total = 0
        for (n in miss) {
            sb.append(n.code).append(" × ").append(n.missing).append('\n')
            total += n.missing
        }
        sb.append("——\n")
        sb.append("共缺 ").append(total).append(" 颗 / ").append(miss.size).append(" 色")
        return sb.toString()
    }

    /** 导出成 CSV。 */
    fun asCsv(needs: List<InventoryRepository.Need>): String {
        val sb = StringBuilder()
        sb.append("色号,图纸需要,手头库存,需要采购\n")
        needs.forEach { n ->
            sb.append(n.code).append(',')
                .append(n.required).append(',')
                .append(n.owned).append(',')
                .append(n.missing).append('\n')
        }
        sb.append("合计,")
            .append(needs.sumOf { it.required }).append(',')
            .append(needs.sumOf { it.owned }).append(',')
            .append(needs.sumOf { it.missing }).append('\n')
        return sb.toString()
    }
}
