package com.bxiidn.esimmanager.lpa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LpaActivationCodeTest {

    private fun ok(raw: String): LpaActivationCode {
        val result = LpaActivationCode.parse(raw)
        assertTrue("seharusnya valid: $raw -> $result", result is LpaActivationCode.ParseResult.Ok)
        return (result as LpaActivationCode.ParseResult.Ok).code
    }

    private fun isError(raw: String): Boolean =
        LpaActivationCode.parse(raw) is LpaActivationCode.ParseResult.Error

    @Test
    fun parsesStandardCode() {
        val code = ok("LPA:1\$smdp.example.com\$ABC-123")
        assertEquals("smdp.example.com", code.smdpAddress)
        assertEquals("ABC-123", code.matchingId)
        assertFalse(code.confirmationCodeRequired)
        assertEquals("LPA:1\$smdp.example.com\$ABC-123", code.toLpaString())
    }

    @Test
    fun parsesWithoutPrefixAndWithWhitespace() {
        val code = ok("  1\$rsp.example.net\$XYZ9 \n")
        assertEquals("rsp.example.net", code.smdpAddress)
        assertEquals("XYZ9", code.matchingId)
    }

    @Test
    fun parsesOidAndConfirmationFlag() {
        val code = ok("LPA:1\$smdp.example.com\$ABC\$1.3.6.1.4.1.31746\$1")
        assertEquals("1.3.6.1.4.1.31746", code.oid)
        assertTrue(code.confirmationCodeRequired)
        assertEquals("LPA:1\$smdp.example.com\$ABC\$1.3.6.1.4.1.31746\$1", code.toLpaString())
    }

    @Test
    fun parsesConfirmationFlagWithEmptyOid() {
        val code = ok("LPA:1\$smdp.example.com\$ABC\$\$1")
        assertEquals("", code.oid)
        assertTrue(code.confirmationCodeRequired)
        assertEquals("LPA:1\$smdp.example.com\$ABC\$\$1", code.toLpaString())
    }

    @Test
    fun parsesUniversalLink() {
        val code = ok("https://esimsetup.android.com/esim_qrcode_provisioning?carddata=LPA:1\$smdp.example.com\$ABC-1")
        assertEquals("smdp.example.com", code.smdpAddress)
        assertEquals("ABC-1", code.matchingId)
    }

    @Test
    fun rejectsInvalidInput() {
        assertTrue(isError(""))
        assertTrue(isError("hello world"))
        assertTrue(isError("LPA:2\$smdp.example.com\$ABC"))
        assertTrue(isError("LPA:1\$not a host\$ABC"))
        assertTrue(isError("LPA:1\$localhost\$ABC"))
        assertTrue(isError("LPA:1\$smdp.example.com\$AB_C!"))
        assertTrue(isError("https://example.com/no-code-here"))
    }
}
