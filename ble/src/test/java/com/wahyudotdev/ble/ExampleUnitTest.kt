package com.wahyudotdev.ble

import com.wahyudotdev.ble.parser.BleParser
import com.wahyudotdev.ble.parser.FitProM5
import org.junit.Test

import java.util.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {

    private fun encodeDateTime(encoder: BleParser) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, 2022)
        calendar.set(Calendar.MONTH, 11)
        calendar.set(Calendar.DATE, 14)
        calendar.set(Calendar.HOUR, 16)
        calendar.set(Calendar.MINUTE, 0)
        val encodedTime = encoder.setDateTime(calendar)
        print(encodedTime)
    }

    @Test
    fun testEncoder() {
        val fitProM5 = FitProM5()
        encodeDateTime(fitProM5)
    }
}