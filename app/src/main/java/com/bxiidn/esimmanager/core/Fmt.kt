package com.bxiidn.esimmanager.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Country(val code: String, val name: String)

object Fmt {
    private val indonesian = Locale("id", "ID")

    fun bytes(value: Long): String {
        if (value < 0) return "—"
        if (value < 1024) return "$value B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = value / 1024.0
        var i = 0
        while (v >= 1024 && i < units.lastIndex) {
            v /= 1024
            i++
        }
        val pattern = when {
            v >= 100 -> "%.0f %s"
            v >= 10 -> "%.1f %s"
            else -> "%.2f %s"
        }
        return String.format(Locale.US, pattern, v, units[i])
    }

    fun dateTime(millis: Long): String =
        if (millis <= 0) "—" else SimpleDateFormat("d MMM yyyy, HH:mm", indonesian).format(Date(millis))

    fun date(millis: Long): String =
        if (millis <= 0) "—" else SimpleDateFormat("d MMM yyyy", indonesian).format(Date(millis))

    fun money(amount: Double, currency: String): String =
        String.format(Locale.US, "%s %.2f", currency.ifBlank { "USD" }, amount)

    fun countryName(code: String?): String {
        if (code.isNullOrBlank()) return ""
        if (code.length != 2) return code
        val name = Locale("", code.uppercase(Locale.US)).getDisplayCountry(indonesian)
        return name.ifBlank { code.uppercase(Locale.US) }
    }

    /** Emoji bendera dari kode negara ISO 3166-1 alpha-2. */
    fun flag(code: String?): String {
        if (code == null || code.length != 2) return "🌐"
        val upper = code.uppercase(Locale.US)
        if (upper.any { it !in 'A'..'Z' }) return "🌐"
        val first = Character.toChars(0x1F1E6 + (upper[0] - 'A'))
        val second = Character.toChars(0x1F1E6 + (upper[1] - 'A'))
        return String(first) + String(second)
    }

    val countries: List<Country> by lazy {
        Locale.getISOCountries()
            .map { Country(it, countryName(it)) }
            .filter { it.name.isNotBlank() && it.name != it.code }
            .sortedBy { it.name }
    }
}
