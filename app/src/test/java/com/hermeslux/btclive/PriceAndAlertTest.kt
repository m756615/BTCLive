package com.hermeslux.btclive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceAndAlertTest {
    @Test
    fun formatsWholeDollars() {
        assertEquals("$65,123", formatUsdPrice(65123.2))
        assertEquals(65123.0, parseUsdPrice("$65,123"))
    }

    @Test
    fun parsesEachPublicSource() {
        assertEquals(65123.45, parseBinance("""{"symbol":"BTCUSDT","price":"65123.45"}""")!!, 0.001)
        assertEquals(65100.0, parseCoinbase("""{"data":{"amount":"65100.00","currency":"USD"}}""")!!, 0.001)
        assertEquals(65010.5, parseKraken("""{"error":[],"result":{"XXBTZUSD":{"c":["65010.5","0.1"]}}}""")!!, 0.001)
        assertEquals(64000.2, parseCryptoCompare("""{"USD":64000.2}""")!!, 0.001)
        assertEquals(63000.0, parseCoinGecko("""{"bitcoin":{"usd":63000}}""")!!, 0.001)
    }

    @Test
    fun clientSkipsABadSource() {
        val client = BtcPriceClient(
            httpGet = { url ->
                if (url.contains("binance")) error("down")
                """{"data":{"amount":"70000"}}"""
            },
            sources = listOf(
                PriceSource("Binance", "https://binance.example", ::parseBinance),
                PriceSource("Coinbase", "https://coinbase.example", ::parseCoinbase),
            ),
        )
        val (usd, source) = client.fetchUsd()
        assertEquals(70000.0, usd, 0.001)
        assertEquals("Coinbase", source)
    }

    @Test
    fun rejectsNonsensePrices() {
        val client = BtcPriceClient(
            httpGet = { """{"USD":1}""" },
            sources = listOf(PriceSource("CryptoCompare", "https://example", ::parseCryptoCompare)),
        )
        val error = runCatching { client.fetchUsd() }.exceptionOrNull()
        assertTrue(error != null)
        assertNull(parseBinance("""{"symbol":"BTCUSDT"}"""))
    }

    @Test
    fun alertsFireOnceAndKeepTheRest() {
        val alerts = listOf(
            PriceAlert(60_000.0, AlertType.ABOVE),
            PriceAlert(90_000.0, AlertType.ABOVE),
            PriceAlert(50_000.0, AlertType.BELOW),
        )
        val decision = evaluateAlerts(alerts, 65_000.0)
        assertEquals(listOf(PriceAlert(60_000.0, AlertType.ABOVE)), decision.hits)
        assertEquals(
            listOf(PriceAlert(90_000.0, AlertType.ABOVE), PriceAlert(50_000.0, AlertType.BELOW)),
            decision.remaining,
        )
        assertTrue(evaluateAlerts(alerts, 0.0).hits.isEmpty())
    }
}
