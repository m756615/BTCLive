package com.hermeslux.btclive

enum class AlertType { ABOVE, BELOW }

data class PriceAlert(val target: Double, val type: AlertType)

data class AlertDecision(
    val remaining: List<PriceAlert>,
    val hits: List<PriceAlert>,
)

fun evaluateAlerts(alerts: List<PriceAlert>, price: Double): AlertDecision {
    if (price <= 0.0 || price.isNaN()) return AlertDecision(alerts, emptyList())
    val hits = ArrayList<PriceAlert>()
    val remaining = ArrayList<PriceAlert>()
    for (alert in alerts) {
        if (alert.target <= 0.0) {
            remaining.add(alert)
            continue
        }
        val hit = when (alert.type) {
            AlertType.ABOVE -> price >= alert.target
            AlertType.BELOW -> price <= alert.target
        }
        if (hit) hits.add(alert) else remaining.add(alert)
    }
    return AlertDecision(remaining, hits)
}
