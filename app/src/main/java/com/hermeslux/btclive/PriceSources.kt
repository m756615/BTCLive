package com.hermeslux.btclive

import java.io.IOException
import java.util.Locale

data class PriceSource(
    val name: String,
    val url: String,
    val parse: (String) -> Double?,
)

class BtcPriceClient(
    private val httpGet: (String) -> String,
    private val sources: List<PriceSource> = DEFAULT_SOURCES,
) {
    fun fetchUsd(): Pair<Double, String> {
        var last: Exception? = null
        for (source in sources) {
            try {
                val body = httpGet(source.url)
                val usd = source.parse(body)
                if (usd != null && usd in 100.0..100_000_000.0) return usd to source.name
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("No Bitcoin price source responded")
    }

    companion object {
        val DEFAULT_SOURCES: List<PriceSource> = listOf(
            PriceSource(
                "Binance",
                "https://api.binance.com/api/v3/ticker/price?symbol=BTCUSDT",
                ::parseBinance,
            ),
            PriceSource(
                "Coinbase",
                "https://api.coinbase.com/v2/prices/BTC-USD/spot",
                ::parseCoinbase,
            ),
            PriceSource(
                "Kraken",
                "https://api.kraken.com/0/public/Ticker?pair=XBTUSD",
                ::parseKraken,
            ),
            PriceSource(
                "CryptoCompare",
                "https://min-api.cryptocompare.com/data/price?fsym=BTC&tsyms=USD",
                ::parseCryptoCompare,
            ),
            PriceSource(
                "CoinGecko",
                "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd",
                ::parseCoinGecko,
            ),
        )
    }
}

fun formatUsdPrice(usd: Double): String {
    return "$" + String.format(Locale.US, "%,.0f", usd)
}

fun parseUsdPrice(display: String): Double? {
    return display.replace("$", "").replace(",", "").trim().toDoubleOrNull()
}

fun jsonNumber(json: String, key: String): Double? {
    val match = Regex(""""${Regex.escape(key)}"\s*:\s*"?(-?\d+(?:\.\d+)?)""")
        .find(json) ?: return null
    return match.groupValues[1].toDoubleOrNull()
}

fun parseCryptoCompare(json: String): Double? = jsonNumber(json, "USD")

fun parseCoinGecko(json: String): Double? = jsonNumber(json, "usd")

fun parseBinance(json: String): Double? = jsonNumber(json, "price")

fun parseCoinbase(json: String): Double? = jsonNumber(json, "amount")

fun parseKraken(json: String): Double? {
    val match = Regex("\"c\"\\s*:\\s*\\[\\s*\"(-?\\d+(?:\\.\\d+)?)\"")
        .find(json) ?: return null
    return match.groupValues[1].toDoubleOrNull()
}
