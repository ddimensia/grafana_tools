package com.digitaldimensia.grafana

import com.digitaldimensia.grafana.model.Dashboard
import com.digitaldimensia.grafana.model.DashboardMetadata
import com.digitaldimensia.grafana.model.GrafanaCommandParams
import com.github.ajalt.clikt.output.TermUi
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.result.Result
import com.github.kittinunf.fuel.jackson.responseObject

class DashboardFinder (params: GrafanaCommandParams, val datasourceName: String) {
    val url: String = params.url
    val dryRun: Boolean = params.dryRun

    fun run() {
        var page = 0
        var resultCount = 1000
        while( resultCount == 1000 ) {
            page += 1
            val request = Fuel.get(
                "$url/api/search",
                listOf("limit" to "1000", "page" to "$page", "type" to "dash-db")
            )

            val (_, _, result) = request.responseObject<List<DashboardMetadata>>()

            when (result) {
                is Result.Success -> {
                    resultCount = result.value.size
                    TermUi.echo("Found ${resultCount} dashboards.")
                    for (dashboard in result.value) {
                        val datasources = findDatasources(dashboard)
                        if (datasources.contains(datasourceName)) {
                            TermUi.echo("Dashboard ${dashboard.title}, ${datasources}")
                        }
                    }
                }
                is Result.Failure -> {
                    TermUi.echo("Failed to get dashboards ${request}", err = true)
                    TermUi.echo("Failure: ${result.error}", err = true)
                    return
                }
            }
        }
    }

    fun findDatasources(dashboard: DashboardMetadata): Set<String> {
        val request = Fuel.get("$url/api/dashboards/uid/${dashboard.uid}")

        val (_, _, result) = request.responseObject<Dashboard>()

        return when(result) {
            is Result.Success -> {
                (result.value.dashboard.rows?.flatMap { row ->
                    row.panels?.map { panel ->
                        panel.datasource ?: "default"
                    } ?: emptyList()
                } ?: emptyList()).union(result.value.dashboard.panels?.map { panel ->
                    panel.datasource ?: "default"
                } ?: emptyList())
            }
            is Result.Failure -> {
                TermUi.echo("Failed to get dashboard ${dashboard.uid}", err = true)
                TermUi.echo("Failure: ${result.error}", err = true)
                emptySet()
            }
        }
    }
}