package com.digitaldimensia.grafana

import com.digitaldimensia.grafana.model.Dashboard
import com.digitaldimensia.grafana.model.DashboardMetadata
import com.digitaldimensia.grafana.model.GrafanaCommandParams
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.ajalt.clikt.output.TermUi

class DashboardFinder(
    params: GrafanaCommandParams,
    val datasourceName: String,
    val datasourceType: DatasourceType?,
    val metrics: List<String>?): DashboardIterator(params) {

    private val datasourceRegex: Regex = "\"datasource\"\\s*:\\s*\"$datasourceName\"".toRegex()
    private val dashboards = mutableSetOf<DashboardMetadata>()
    private val objectMapper = jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    override fun handleResult(dashboardMeta: DashboardMetadata, dashboardStr: String) {
        if (findDatasource(dashboardStr)) {
            if (!metrics.isNullOrEmpty() && datasourceType != null) {
                val dashboard: Dashboard = objectMapper.readValue(dashboardStr)
                if (datasourceType.processor.hasMetrics(dashboard, metrics)) {
                    dashboards.add(dashboardMeta)
                }
            } else {
                dashboards.add(dashboardMeta)
            }
        }
    }

    override fun afterLast() {
        if (dashboards.isNotEmpty()) {
            TermUi.echo("Dashboards with datasource `$datasourceName`:")
            TermUi.echo(dashboards.joinToString { it.title })
        } else {
            TermUi.echo("No Dashboards found with datasource `$datasourceName`")
        }
    }

    private fun findDatasource(dashboard: String): Boolean {
        return dashboard.contains(datasourceRegex)
    }
}