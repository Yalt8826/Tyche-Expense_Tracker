package dev.yashas.expensetracker.data.rules

import dev.yashas.expensetracker.data.db.dao.CatalogDao
import dev.yashas.expensetracker.data.db.entity.CategoryEntity

/**
 * Bundled category seed (00-MASTER §5: suggestions come from a bundled dictionary +
 * user history). One subcategory level per 01-SCREENS S12a drill-down.
 */
object CategorySeed {

    data class Def(
        val key: String,
        val name: String,
        val icon: String,
        val colorToken: String,
        val children: List<Def> = emptyList(),
    )

    val TREE = listOf(
        Def("food", "Food", "restaurant", "series_violet", listOf(
            Def("food.restaurants", "Restaurants", "restaurant", "series_violet"),
            Def("food.groceries", "Groceries", "shopping_basket", "series_lime"),
            Def("food.coffee", "Coffee", "local_cafe", "series_amber"),
            Def("food.snacks", "Snacks", "fastfood", "series_pink"),
        )),
        Def("transport", "Transport", "directions_car", "series_cyan", listOf(
            Def("transport.fuel", "Fuel", "local_gas_station", "series_cyan"),
            Def("transport.cab", "Cab", "local_taxi", "series_blue"),
        )),
        Def("shopping", "Shopping", "shopping_bag", "series_pink"),
        Def("bills", "Bills", "receipt", "series_amber", listOf(
            Def("bills.recharge", "Recharge", "smartphone", "series_violet"),
            Def("bills.electricity", "Electricity", "bolt", "series_amber"),
        )),
        Def("entertainment", "Entertainment", "movie", "series_blue"),
        Def("health", "Health", "medication", "series_lime"),
        Def("education", "Education", "school", "series_violet"),
        Def("other", "Other", "category", "series_blue"),
    )

    /** Two-pass idempotent seed: parents first, children linked by resolved parent id. */
    suspend fun seed(dao: CatalogDao) {
        for (def in TREE) {
            dao.upsertCategory(
                CategoryEntity(key = def.key, name = def.name, icon = def.icon, colorToken = def.colorToken, parentId = null),
            )
            val parent = dao.categoryByKey(def.key) ?: continue
            for (child in def.children) {
                dao.upsertCategory(
                    CategoryEntity(key = child.key, name = child.name, icon = child.icon, colorToken = child.colorToken, parentId = parent.id),
                )
            }
        }
    }
}
