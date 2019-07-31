package com.digitaldimensia.grafana

import com.digitaldimensia.grafana.model.DashboardMetadata
import com.digitaldimensia.grafana.model.GrafanaCommandParams
import com.github.ajalt.clikt.output.TermUi
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.result.Result
import com.github.kittinunf.fuel.jackson.responseObject

abstract class DashboardIterator(params: GrafanaCommandParams) {
    private val url = params.url

    fun run() {
        var page = 0
        var resultCount = 1000
        while( resultCount == 1000) {
            page += 1
            val request = Fuel.get(
                "$url/api/search",
                listOf("limit" to "1000", "page" to "$page", "type" to "dash-db")
            )

            val (_, _, result) = request.responseObject<List<DashboardMetadata>>()

            when(result) {
                is Result.Success -> {
                    resultCount = result.value.size
                    TermUi.echo("Found $resultCount dashboards.")
                    result.value.map { processDashboard(it) }
                }
                is Result.Failure -> {
                    TermUi.echo("Failed to get dashboards $request", err = true)
                    TermUi.echo("Failure: ${result.error}", err = true)
                }
            }
        }

        afterLast()
    }

    private fun processDashboard(dashboard: DashboardMetadata) {
        val request = Fuel.get("$url/api/dashboards/uid/${dashboard.uid}")

        val (_, _, result) = request.responseString()

        return when(result) {
            is Result.Success -> {
                handleResult(dashboard, result.value)
            }
            is Result.Failure -> {
                TermUi.echo("Failed to get dashboard ${dashboard.uid}", err = true)
                TermUi.echo("Failure: ${result.error}", err = true)
            }
        }
    }
    abstract fun handleResult(dashboardMeta: DashboardMetadata, dashboardStr: String)
    abstract fun afterLast()
}