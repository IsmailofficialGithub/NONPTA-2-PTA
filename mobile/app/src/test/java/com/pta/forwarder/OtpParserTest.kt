package com.pta.forwarder

import com.pta.forwarder.util.OtpParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OtpParserTest {

    @Test
    fun testUblDigitalOtp() {
        val sms = "Your UBL Digital OTP is 839201. Valid for 5 minutes. Do not share with anyone."
        val result = OtpParser.extractOtp(sms)
        assertNotNull(result)
        assertEquals("839201", result?.code)
        assertEquals("UBL", result?.serviceName)
    }

    @Test
    fun testJazzCashOtp() {
        val sms = "JazzCash: 9182 is your one time password. Never share this with anyone."
        val result = OtpParser.extractOtp(sms)
        assertNotNull(result)
        assertEquals("9182", result?.code)
        assertEquals("JazzCash", result?.serviceName)
    }

    @Test
    fun testEasypaisaOtp() {
        val sms = "492018 is your Easypaisa activation code. Please do not share it."
        val result = OtpParser.extractOtp(sms)
        assertNotNull(result)
        assertEquals("492018", result?.code)
        assertEquals("Easypaisa", result?.serviceName)
    }

    @Test
    fun testSadaPayOtp() {
        val sms = "SadaPay verification code: 5821. Welcome to SadaPay!"
        val result = OtpParser.extractOtp(sms)
        assertNotNull(result)
        assertEquals("5821", result?.code)
        assertEquals("SadaPay", result?.serviceName)
    }

    @Test
    fun testNayaPayOtp() {
        val sms = "Use 482019 to login to NayaPay. Valid for 10 minutes."
        val result = OtpParser.extractOtp(sms)
        assertNotNull(result)
        assertEquals("482019", result?.code)
        assertEquals("NayaPay", result?.serviceName)
    }

    @Test
    fun testMeezanBankOtp() {
        val sms = "Meezan Bank: Your OTP for internet banking transaction is 109284. Generated at 18:20."
        val result = OtpParser.extractOtp(sms)
        assertNotNull(result)
        assertEquals("109284", result?.code)
        assertEquals("Meezan", result?.serviceName)
    }

    @Test
    fun testNormalMessageWithoutOtp() {
        val sms = "Hey Ismail, let's meet at 5 PM at the cafe near the office."
        val result = OtpParser.extractOtp(sms)
        assertNull(result)
    }
}
