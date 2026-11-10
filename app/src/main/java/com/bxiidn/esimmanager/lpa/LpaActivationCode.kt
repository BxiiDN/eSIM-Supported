package com.bxiidn.esimmanager.lpa

/**
 * Kode aktivasi eSIM sesuai GSMA SGP.22 (§4.1):
 *
 *   LPA:1$<alamat SM-DP+>$<Matching ID>[$<OID SM-DP+>[$<flag kode konfirmasi>]]
 *
 * Awalan "LPA:" boleh tidak ada saat kode diketik manual.
 */
data class LpaActivationCode(
    val smdpAddress: String,
    val matchingId: String,
    val oid: String = "",
    val confirmationCodeRequired: Boolean = false,
) {
    /** Bentuk kanonik yang dipakai untuk QR dan EuiccManager. */
    fun toLpaString(): String {
        val sb = StringBuilder("LPA:1")
        sb.append(SEP).append(smdpAddress)
        sb.append(SEP).append(matchingId)
        if (oid.isNotEmpty() || confirmationCodeRequired) sb.append(SEP).append(oid)
        if (confirmationCodeRequired) sb.append(SEP).append('1')
        return sb.toString()
    }

    sealed interface ParseResult {
        data class Ok(val code: LpaActivationCode) : ParseResult
        data class Error(val message: String) : ParseResult
    }

    companion object {
        private const val SEP = '$'
        private val HOST = Regex("^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?(:[0-9]{1,5})?$")
        private val MATCHING_ID = Regex("^[A-Za-z0-9-]*$")
        private val OID = Regex("^[0-9]+(\\.[0-9]+)*$")

        fun parse(raw: String): ParseResult {
            var text = raw.trim().replace("\n", "").replace("\r", "").replace(" ", "")
            if (text.isEmpty()) return ParseResult.Error("Kode aktivasi kosong.")

            if (text.startsWith("LPA:", ignoreCase = true)) {
                text = text.substring(4)
            } else if (text.startsWith("http://", true) || text.startsWith("https://", true)) {
                // Tautan "universal link" pembawa carddata=LPA:1$...
                val marker = text.indexOf("LPA:1", ignoreCase = true)
                    .takeIf { it >= 0 }
                    ?: text.indexOf("LPA%3A1", ignoreCase = true).takeIf { it >= 0 }
                    ?: return ParseResult.Error("Tautan ini tidak berisi kode aktivasi eSIM (LPA).")
                val candidate = runCatching {
                    java.net.URLDecoder.decode(text.substring(marker), "UTF-8")
                }.getOrNull() ?: return ParseResult.Error("Tautan aktivasi tidak valid.")
                return parse(candidate.substringBefore('&'))
            }

            val parts = text.split(SEP)
            if (parts.size < 3) {
                return ParseResult.Error(
                    "Format tidak dikenali. Kode aktivasi eSIM berbentuk LPA:1\$alamat-SM-DP+\$kode.",
                )
            }
            if (parts[0] != "1") {
                return ParseResult.Error("Versi format kode aktivasi \"${parts[0]}\" tidak didukung (harus 1).")
            }

            val smdp = parts[1]
            if (smdp.isEmpty() || !smdp.contains('.') || !HOST.matches(smdp)) {
                return ParseResult.Error("Alamat SM-DP+ tidak valid: \"$smdp\".")
            }

            val matchingId = parts[2]
            if (!MATCHING_ID.matches(matchingId)) {
                return ParseResult.Error("Matching ID berisi karakter yang tidak diizinkan.")
            }

            val oid = parts.getOrNull(3).orEmpty()
            if (oid.isNotEmpty() && !OID.matches(oid)) {
                return ParseResult.Error("OID SM-DP+ tidak valid: \"$oid\".")
            }

            val ccFlag = parts.getOrNull(4).orEmpty()
            if (ccFlag.isNotEmpty() && ccFlag != "1") {
                return ParseResult.Error("Flag kode konfirmasi tidak valid: \"$ccFlag\".")
            }

            return ParseResult.Ok(
                LpaActivationCode(
                    smdpAddress = smdp,
                    matchingId = matchingId,
                    oid = oid,
                    confirmationCodeRequired = ccFlag == "1",
                ),
            )
        }
    }
}
