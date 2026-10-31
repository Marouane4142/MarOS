package io.github.marouane4142.maros.tv

/** Pure policy shared by the TV and companion. Unknown/duplicate rows never enter the UI. */
object HomeLayout {
    val rows = listOf("favorites", "continue", "recent", "library", "shortcuts")
    val labels = mapOf("favorites" to "Vos essentiels", "continue" to "Dernières lectures",
        "recent" to "Ouvertes récemment", "library" to "Votre vidéothèque", "shortcuts" to "À portée de main")
    fun normalize(order: List<String>): List<String> = (order.filter { it in rows } + rows).distinct()
    fun move(order: List<String>, key: String, delta: Int): List<String> {
        val result = normalize(order).toMutableList()
        val index = result.indexOf(key)
        if (index < 0) return result
        val target = (index + delta).coerceIn(result.indices)
        result.removeAt(index); result.add(target, key)
        return result
    }
}
