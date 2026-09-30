package com.sonothamin.meowlaundry.data

/** A garment type preset and the broad category it belongs to. */
data class GarmentPreset(val name: String, val category: String)

/** Preset lists for the add/edit form, merged with what the user has already entered. */
object Suggestions {

    /**
     * Built-in categories offered as suggestions for the (free-text) category field. Anything the
     * person types is accepted - this is just what's shown before they start typing, plus whatever
     * they've matched by prefix.
     */
    val builtInCategories: List<String> = listOf(
        "Top", "Bottom", "Dress", "Outerwear", "Underwear", "Sleepwear",
        "Accessory", "Footwear", "Bedding", "Other",
    )

    val garmentTypes: List<GarmentPreset> = listOf(
        GarmentPreset("T-shirt", "Top"), GarmentPreset("Shirt", "Top"),
        GarmentPreset("Polo shirt", "Top"), GarmentPreset("Blouse", "Top"),
        GarmentPreset("Tank top", "Top"), GarmentPreset("Sweater", "Top"),
        GarmentPreset("Hoodie", "Top"), GarmentPreset("Sweatshirt", "Top"),
        GarmentPreset("Cardigan", "Top"), GarmentPreset("Kurta", "Top"),
        GarmentPreset("Jeans", "Bottom"), GarmentPreset("Trousers", "Bottom"),
        GarmentPreset("Chinos", "Bottom"), GarmentPreset("Shorts", "Bottom"),
        GarmentPreset("Skirt", "Bottom"), GarmentPreset("Leggings", "Bottom"),
        GarmentPreset("Joggers", "Bottom"), GarmentPreset("Cargo pants", "Bottom"),
        GarmentPreset("Dress", "Dress"), GarmentPreset("Maxi dress", "Dress"),
        GarmentPreset("Jumpsuit", "Dress"), GarmentPreset("Saree", "Dress"),
        GarmentPreset("Jacket", "Outerwear"), GarmentPreset("Coat", "Outerwear"),
        GarmentPreset("Blazer", "Outerwear"), GarmentPreset("Puffer jacket", "Outerwear"),
        GarmentPreset("Denim jacket", "Outerwear"), GarmentPreset("Raincoat", "Outerwear"),
        GarmentPreset("Windbreaker", "Outerwear"), GarmentPreset("Vest", "Outerwear"),
        GarmentPreset("Underwear", "Underwear"), GarmentPreset("Boxers", "Underwear"),
        GarmentPreset("Bra", "Underwear"), GarmentPreset("Undershirt", "Underwear"),
        GarmentPreset("Socks", "Underwear"),
        GarmentPreset("Pajamas", "Sleepwear"), GarmentPreset("Nightgown", "Sleepwear"),
        GarmentPreset("Robe", "Sleepwear"),
        GarmentPreset("Scarf", "Accessory"), GarmentPreset("Hat", "Accessory"),
        GarmentPreset("Cap", "Accessory"), GarmentPreset("Beanie", "Accessory"),
        GarmentPreset("Belt", "Accessory"), GarmentPreset("Tie", "Accessory"),
        GarmentPreset("Gloves", "Accessory"), GarmentPreset("Shawl", "Accessory"),
        GarmentPreset("Sneakers", "Footwear"), GarmentPreset("Boots", "Footwear"),
        GarmentPreset("Sandals", "Footwear"), GarmentPreset("Loafers", "Footwear"),
        GarmentPreset("Slippers", "Footwear"),
        // Bedding and other soft furnishings - not worn, but laundered the same way.
        GarmentPreset("Bedsheet", "Bedding"), GarmentPreset("Fitted sheet", "Bedding"),
        GarmentPreset("Pillowcase", "Bedding"), GarmentPreset("Duvet cover", "Bedding"),
        GarmentPreset("Comforter", "Bedding"), GarmentPreset("Quilt", "Bedding"),
        GarmentPreset("Blanket", "Bedding"), GarmentPreset("Mattress protector", "Bedding"),
        GarmentPreset("Bed skirt", "Bedding"), GarmentPreset("Towel", "Bedding"),
        // Cold-weather pieces (auto-tagged as winter wear, see isWinterWear).
        GarmentPreset("Jumper", "Top"), GarmentPreset("Turtleneck", "Top"),
        GarmentPreset("Thermal top", "Underwear"),
        GarmentPreset("Parka", "Outerwear"), GarmentPreset("Long coat", "Outerwear"),
        GarmentPreset("Trench coat", "Outerwear"), GarmentPreset("Overcoat", "Outerwear"),
        GarmentPreset("Fleece jacket", "Outerwear"),
        GarmentPreset("Muffler", "Accessory"),
    )

    /**
     * Words that mark a garment as winter wear. Matched as whole words (see [isWinterWear]) so
     * "raincoat" or "waistcoat" don't trip on "coat" the way a plain substring match would.
     */
    private val winterKeywords = setOf(
        "muffler", "scarf", "shawl", "beanie", "gloves", "glove", "mittens", "balaclava", "earmuffs",
        "sweater", "jumper", "pullover", "cardigan", "hoodie", "hoody", "sweatshirt", "fleece",
        "turtleneck", "thermal", "thermals", "wool", "woolen", "woollen", "cashmere",
        "jacket", "parka", "anorak", "coat", "overcoat", "trenchcoat", "poncho", "cape", "puffer",
    )

    /** A word that says the garment is the light/summer variant even if it contains a winter keyword ("rain jacket"). */
    private val notWinterKeywords = setOf("rain", "summer", "linen", "sleeveless", "windbreaker")

    /** True when [text] (a garment type or title) names something you'd wear in cold weather. */
    fun isWinterWear(text: String): Boolean {
        val words = text.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        if (words.isEmpty() || words.any { it in notWinterKeywords }) return false
        return words.any { it in winterKeywords }
    }

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

    fun categoryFor(garmentType: String): String? =
        garmentTypes.firstOrNull { it.name.equals(garmentType.trim(), ignoreCase = true) }?.category

    /**
     * Normalizes a category for display. Values typed through the current free-text field keep
     * exactly the casing the person used; values left over from when this was a fixed enum
     * ("TOP", "OTHER"...) are ALL CAPS, so those are Title Cased instead.
     */
    fun displayCategory(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.isEmpty() -> "Other"
            trimmed == trimmed.uppercase() -> trimmed.lowercase().replaceFirstChar { it.uppercase() }
            else -> trimmed
        }
    }

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
