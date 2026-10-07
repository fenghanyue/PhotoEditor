package io.github.fenghanyue.photoeditor.meta

import java.util.Locale

/** 把 EXIF 里的品牌和机型整理成好认的名字，比如 NIKON CORPORATION + NIKON Z5_2 → Nikon Z5II。 */
object DeviceNames {

    /** EXIF 品牌写法的开头 → 显示名。 */
    private val BRANDS = listOf(
        "NIKON" to "Nikon",
        "CANON" to "Canon",
        "SONY" to "Sony",
        "FUJIFILM" to "Fujifilm",
        "PANASONIC" to "Panasonic",
        "OLYMPUS" to "Olympus",
        "OM DIGITAL" to "OM System",
        "LEICA" to "Leica",
        "RICOH" to "Ricoh",
        "PENTAX" to "Pentax",
        "HASSELBLAD" to "Hasselblad",
        "SIGMA" to "Sigma",
        "DJI" to "DJI",
        "GOPRO" to "GoPro",
        "APPLE" to "Apple",
        "XIAOMI" to "Xiaomi",
        "HUAWEI" to "Huawei",
        "HONOR" to "Honor",
        "OPPO" to "OPPO",
        "ONEPLUS" to "OnePlus",
        "REALME" to "realme",
        "VIVO" to "vivo",
        "SAMSUNG" to "Samsung",
        "GOOGLE" to "Google",
        "MEIZU" to "Meizu",
        "NOTHING" to "Nothing",
        "MOTOROLA" to "Motorola",
    )

    private val ROMAN = mapOf(
        2 to "II", 3 to "III", 4 to "IV", 5 to "V", 6 to "VI", 7 to "VII", 8 to "VIII", 9 to "IX",
    )

    fun brand(make: String?): String? {
        val raw = make?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val upper = raw.uppercase(Locale.ROOT)
        return BRANDS.firstOrNull { upper.startsWith(it.first) }?.second ?: raw
    }

    fun model(make: String?, model: String?): String? {
        var name = model?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val brand = brand(make)
        // 机型里常带重复的品牌名，比如 "NIKON Z5_2"、"Canon EOS R6m2"
        val prefix = listOfNotNull(make?.trim()?.substringBefore(' '), brand)
            .firstOrNull { name.startsWith("$it ", ignoreCase = true) }
        if (prefix != null) name = name.substring(prefix.length + 1).trim()
        return when (brand) {
            // 尼康用 _2、_3 表示二代、三代：Z5_2 → Z5II
            "Nikon" -> name.replace(Regex("_(\\d)$")) { roman(it.groupValues[1]) }
            // 佳能用 m2 表示 Mark II：EOS R6m2 → EOS R6 Mark II
            "Canon" -> name.replace(Regex("m(\\d)$")) { " Mark " + roman(it.groupValues[1]) }
            "Sony" -> sony(name)
            else -> name
        }
    }

    fun displayName(make: String?, model: String?): String? {
        val brand = brand(make)
        val shortModel = model(make, model)
        return when {
            brand == null -> shortModel
            shortModel == null -> brand
            else -> "$brand $shortModel"
        }
    }

    /** 索尼微单的型号是 ILCE-7M4 这种写法，对应 α7 IV。 */
    private fun sony(name: String): String {
        val match = Regex("^ILCE-(\\d+[A-Z]*?)(?:M(\\d))?([A-Z]?)$").find(name) ?: return name
        val (base, generation, suffix) = match.destructured
        return buildString {
            append('α').append(base)
            if (generation.isNotEmpty()) append(' ').append(roman(generation))
            append(suffix)
        }
    }

    private fun roman(digit: String): String = ROMAN[digit.toInt()] ?: digit
}
