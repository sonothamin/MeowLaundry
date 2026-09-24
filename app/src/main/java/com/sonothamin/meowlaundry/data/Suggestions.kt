package com.sonothamin.meowlaundry.data

/** A garment type preset and the broad category it belongs to. */
data class GarmentPreset(val name: String, val category: ClothingType)

/** Preset lists for the add/edit form, merged with what the user has already entered. */
object Suggestions {

    val garmentTypes: List<GarmentPreset> = listOf(
        GarmentPreset("T-shirt", ClothingType.TOP), GarmentPreset("Shirt", ClothingType.TOP),
        GarmentPreset("Polo shirt", ClothingType.TOP), GarmentPreset("Blouse", ClothingType.TOP),
        GarmentPreset("Tank top", ClothingType.TOP), GarmentPreset("Sweater", ClothingType.TOP),
        GarmentPreset("Hoodie", ClothingType.TOP), GarmentPreset("Sweatshirt", ClothingType.TOP),
        GarmentPreset("Cardigan", ClothingType.TOP), GarmentPreset("Kurta", ClothingType.TOP),
        GarmentPreset("Jeans", ClothingType.BOTTOM), GarmentPreset("Trousers", ClothingType.BOTTOM),
        GarmentPreset("Chinos", ClothingType.BOTTOM), GarmentPreset("Shorts", ClothingType.BOTTOM),
        GarmentPreset("Skirt", ClothingType.BOTTOM), GarmentPreset("Leggings", ClothingType.BOTTOM),
        GarmentPreset("Joggers", ClothingType.BOTTOM), GarmentPreset("Cargo pants", ClothingType.BOTTOM),
        GarmentPreset("Dress", ClothingType.DRESS), GarmentPreset("Maxi dress", ClothingType.DRESS),
        GarmentPreset("Jumpsuit", ClothingType.DRESS), GarmentPreset("Saree", ClothingType.DRESS),
        GarmentPreset("Jacket", ClothingType.OUTERWEAR), GarmentPreset("Coat", ClothingType.OUTERWEAR),
        GarmentPreset("Blazer", ClothingType.OUTERWEAR), GarmentPreset("Puffer jacket", ClothingType.OUTERWEAR),
        GarmentPreset("Denim jacket", ClothingType.OUTERWEAR), GarmentPreset("Raincoat", ClothingType.OUTERWEAR),
        GarmentPreset("Windbreaker", ClothingType.OUTERWEAR), GarmentPreset("Vest", ClothingType.OUTERWEAR),
        GarmentPreset("Underwear", ClothingType.UNDERWEAR), GarmentPreset("Boxers", ClothingType.UNDERWEAR),
        GarmentPreset("Bra", ClothingType.UNDERWEAR), GarmentPreset("Undershirt", ClothingType.UNDERWEAR),
        GarmentPreset("Socks", ClothingType.UNDERWEAR),
        GarmentPreset("Pajamas", ClothingType.SLEEPWEAR), GarmentPreset("Nightgown", ClothingType.SLEEPWEAR),
        GarmentPreset("Robe", ClothingType.SLEEPWEAR),
        GarmentPreset("Scarf", ClothingType.ACCESSORY), GarmentPreset("Hat", ClothingType.ACCESSORY),
        GarmentPreset("Cap", ClothingType.ACCESSORY), GarmentPreset("Beanie", ClothingType.ACCESSORY),
        GarmentPreset("Belt", ClothingType.ACCESSORY), GarmentPreset("Tie", ClothingType.ACCESSORY),
        GarmentPreset("Gloves", ClothingType.ACCESSORY), GarmentPreset("Shawl", ClothingType.ACCESSORY),
        GarmentPreset("Sneakers", ClothingType.FOOTWEAR), GarmentPreset("Boots", ClothingType.FOOTWEAR),
        GarmentPreset("Sandals", ClothingType.FOOTWEAR), GarmentPreset("Loafers", ClothingType.FOOTWEAR),
        GarmentPreset("Slippers", ClothingType.FOOTWEAR),
    )

    val brands: List<String> = listOf(
        "Nike", "Adidas", "Puma", "Reebok", "New Balance", "Under Armour", "Uniqlo", "Zara", "H&M",
        "Mango", "Gap", "Old Navy", "Levi's", "Wrangler", "Lee", "Diesel", "Tommy Hilfiger",
        "Calvin Klein", "Ralph Lauren", "Lacoste", "Fila", "Champion", "Carhartt", "Dickies",
        "Columbia", "The North Face", "Patagonia", "Timberland", "Vans", "Converse", "Skechers",
        "Hugo Boss", "Massimo Dutti", "Marks & Spencer", "Next", "Primark", "Benetton", "Decathlon",
    )

    /** Colour name -> ARGB, so the picker can show a swatch. Names without a swatch (patterns) map to null. */
    val colors: List<Pair<String, Long?>> = listOf(
        "Black" to 0xFF000000, "White" to 0xFFFFFFFF, "Grey" to 0xFF9E9E9E, "Charcoal" to 0xFF36454F,
        "Navy" to 0xFF1F2A44, "Blue" to 0xFF1E5AA8, "Light blue" to 0xFF8EC5FC, "Denim" to 0xFF4A6FA5,
        "Teal" to 0xFF008080, "Green" to 0xFF2E7D32, "Olive" to 0xFF6B6B2F, "Mint" to 0xFF98D8C8,
        "Yellow" to 0xFFF2C94C, "Mustard" to 0xFFD4A017, "Orange" to 0xFFF2994A, "Red" to 0xFFC62828,
        "Maroon" to 0xFF6D1F2F, "Pink" to 0xFFF48FB1, "Purple" to 0xFF7B4FA0, "Lavender" to 0xFFB39DDB,
        "Brown" to 0xFF6D4C41, "Tan" to 0xFFC8A27A, "Beige" to 0xFFE8DCC4, "Cream" to 0xFFFFF5DC,
        "Khaki" to 0xFFBDB76B, "Gold" to 0xFFD4AF37, "Silver" to 0xFFC0C0C0,
        "Striped" to null, "Checked" to null, "Patterned" to null, "Multicolor" to null,
    )

    /** Previously used values first (most used first), then presets not already covered. Case-insensitive. */
    fun merge(history: List<String>, presets: List<String>): List<String> {
        val seen = HashSet<String>()
        return (history + presets).filter { it.isNotBlank() && seen.add(it.trim().lowercase()) }
    }

    fun categoryFor(garmentType: String): ClothingType? =
        garmentTypes.firstOrNull { it.name.equals(garmentType.trim(), ignoreCase = true) }?.category

    fun colorArgb(name: String): Long? =
        colors.firstOrNull { it.first.equals(name.trim(), ignoreCase = true) }?.second

    /** Trims and collapses whitespace; blank becomes null. */
    fun clean(value: String): String? = value.trim().replace(Regex("\\s+"), " ").ifBlank { null }
}

/** Builds an article title from its parts: "Uniqlo Blue Oxford shirt". */
object TitleGenerator {
    fun generate(brand: String, color: String, garmentType: String): String =
        listOfNotNull(
            Suggestions.clean(brand),
            Suggestions.clean(color)?.capFirst(),
            Suggestions.clean(garmentType)?.capFirst(),
        ).joinToString(" ")

    private fun String.capFirst() = replaceFirstChar { it.uppercase() }
}
