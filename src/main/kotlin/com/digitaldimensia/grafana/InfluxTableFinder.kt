package com.digitaldimensia.grafana

import com.digitaldimensia.grafana.model.*
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.ajalt.clikt.output.TermUi
import com.github.kittinunf.result.Result
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.defaultMapper
import com.github.kittinunf.fuel.jackson.responseObject
import java.io.File

class InfluxTableFinder(params: GrafanaCommandParams, val datasourceName: String, val tableFile: File) {
    val url: String = params.url
    val regex: Regex = "\"datasource\"\\s*:\\s*\"$datasourceName\"".toRegex()
    fun run() {
        var tableMap: MutableMap<String, String> = defaultMapper.readValue(tableFile)
        var tableCountByPyst: MutableMap<String, Int> = mutableMapOf()
        tableMap.forEach { (key, value) ->
            tableCountByPyst.compute(value) { _, u -> (u ?: 0) + 1 }
        }

        var outdatedDashboards: MutableList<String> = mutableListOf()
        var page = 0
        var resultCount = 1000
        while (resultCount == 1000) {
            page += 1
            val request = Fuel.get(
                "$url/api/search",
                listOf("limit" to "1000", "page" to "$page", "type" to "dash-db")
            )

            val (_, _, result) = request.responseObject<List<DashboardMetadata>>()

            when (result) {
                is Result.Success -> {
                    resultCount = result.value.size
                    TermUi.echo("Found $resultCount dashboards.")
                    for (dashboard in result.value) {
                        val dashboardTables = findTables(dashboard)
                        if (dashboardTables != null) {
                            if (dashboardTables.isEmpty() && dashboard.tags.contains("siphon")) {
                                outdatedDashboards.add(dashboard.title)
                            } else {
                                dashboardTables
                                    .map { table ->
                                        table.substringAfter("/^").substringBefore("$/")
                                    }
                                    .map(String::toRegex)
                                    .forEach { regex ->
                                        val matches = tableMap.filterKeys { key -> regex.matches(key) }
                                        matches.forEach { match ->
                                            tableMap.remove(match.key)
                                        }
                                    }
                            }
                        }
                    }
                }
                is Result.Failure -> {
                    TermUi.echo("Failed to get dashboards $request", err = true)
                    TermUi.echo("Failure: ${result.error}", err = true)
                    return
                }
            }
        }

        val pystMap = mutableMapOf<String, MutableSet<String>>()
        tableMap.forEach { (key, value) ->
            val tableList = pystMap.getOrPut(value, { mutableSetOf() })
            tableList.add(key)
        }

        val deletablePystFiles = pystMap
            .filterKeys { key -> tableCountByPyst[key] == pystMap[key]?.size ?: 0 }
            .keys
            .sorted()

        val pystFilesWithUnusedTables = pystMap
            .filterKeys { key -> tableCountByPyst[key] != pystMap[key]?.size ?: 0 }
            .toSortedMap()

        defaultMapper.enable(SerializationFeature.INDENT_OUTPUT)
        TermUi.echo("Outdated Dashboards: ${defaultMapper.writeValueAsString(outdatedDashboards)}")
        TermUi.echo("Unused tables: ${defaultMapper.writeValueAsString(pystFilesWithUnusedTables)}")
        TermUi.echo("Deletable pyst files: ${defaultMapper.writeValueAsString(deletablePystFiles)}")
        TermUi.echo("Total unused tables: ${tableMap.size}")
    }

    private fun findTables(dashboard: DashboardMetadata): List<String>? {
        val request = Fuel.get("$url/api/dashboards/uid/${dashboard.uid}")

        val (_, _, result) = request.responseString()

        when (result) {
            is Result.Success -> {
                val matched = result.value.contains(regex)
                if (matched) {
                    TermUi.echo("Dashboard: ${dashboard.title}")
                    val dashObj: Dashboard = defaultMapper.readValue(result.value)
                    val variableRegexes = dashObj.dashboard.templating?.list.orEmpty()
                        .sortedByDescending { it.name.length }
                        .associate {
                            val regex = """(?:\[\[${it.name}]]|\$\{${it.name}:[^}]}|\$${it.name})""".toRegex()
                            val value = when (it) {
                                is CustomVariable ->
                                    "(?:${it.options.orEmpty().map { op -> op.value }.joinToString("|")})"
                                is ConstantVariable ->
                                    it.query
                                else ->
                                    ".*"
                            }
                            Pair(regex, value)
                        }

                    val panelTables: Set<String> = dashObj.dashboard.panels.orEmpty().flatMap(this::processPanel)
                        .union(dashObj.dashboard.rows.orEmpty().flatMap { row ->
                            row.panels.orEmpty().flatMap(this::processPanel)
                        })

                    val expandedTables = panelTables.map { table ->
                        if (table.contains("$") || table.contains("[[")) {
                            variableRegexes.entries.fold(table) { acc, entry -> acc.replace(entry.key, entry.value) }
                        } else {
                            table
                        }
                    }

                    TermUi.echo("Found tables: $expandedTables")
                    return expandedTables
                } else {
                    return null
                }
            }
            is Result.Failure -> {
                TermUi.echo("Failed to get dashboard ${dashboard.uid}", err = true)
                TermUi.echo("Failure: ${result.error}", err = true)
                return null
            }
        }
    }

    private fun processPanel(panel: Panel): List<String> {
        val tableRegex = "(?:FROM|from)\\s+\"?autogen\"?\\.\\s*([^\\s]+)\\s+(?:WHERE|where)".toRegex()
        val panelDS = panel.datasource
        return panel.targets?.map { target ->
            if ((target.hasNonNull("datasource") && target["datasource"].textValue() == datasourceName) || panelDS == datasourceName) {
                if (target.has("rawQuery") && target["rawQuery"].isBoolean && target["rawQuery"].booleanValue()) {
                    val query = target["query"].textValue().replace("\n", " ")
                    val matchResult = tableRegex.find(query)
                    if (matchResult != null) {
                        matchResult.groupValues[1].replace("\"", "")
                    } else {
                        null
                    }
                } else {
                    if (target.hasNonNull("measurement") && target.hasNonNull("policy") && target["policy"].textValue() == "autogen") {
                        target["measurement"].textValue()
                    } else {
                        null
                    }
                }
            } else {
                null
            }
        }.orEmpty().filterNotNull()
    }
}
