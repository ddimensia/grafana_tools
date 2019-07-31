package com.digitaldimensia.grafana

import com.digitaldimensia.grafana.model.ConstantVariable
import com.digitaldimensia.grafana.model.CustomVariable
import com.digitaldimensia.grafana.model.Dashboard
import com.digitaldimensia.grafana.model.Panel
import com.github.ajalt.clikt.output.TermUi

abstract class DatasourceProcessor {
    abstract fun hasMetrics(dashboard: Dashboard, metrics: List<String>): Boolean
    abstract fun getMetrics(dashboard: Dashboard): List<String>
}

class InfluxProcessor: DatasourceProcessor() {
    override fun hasMetrics(dashboard: Dashboard, metrics: List<String>): Boolean {
        val tablePatterns = getMetrics(dashboard)
            .map {"^$it$"}
            .map(String::toRegex)
        val tablesToFind = metrics.map { metric ->
            if (!metric.contains('.')) {
                "default.$metric"
            } else {
                metric
            }
        }

        return tablePatterns.any { pattern ->
            tablesToFind.any { metric -> metric.matches(pattern) }
        }
    }

    override fun getMetrics(dashboard: Dashboard): List<String> {
        val variableRegexes = dashboard.dashboard.templating?.list.orEmpty()
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

        val panelTables: Set<String> = dashboard.dashboard.panels.orEmpty().flatMap(this::processPanel)
            .union(dashboard.dashboard.rows.orEmpty().flatMap { row ->
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
    }

    private fun processPanel(panel: Panel): List<String> {
        val tableRegex = "(?:FROM|from)\\s+(\"?[^\"]+\"?\\.)?\\s*([^\\s]+)\\s+(?:WHERE|where)".toRegex()
        return panel.targets?.map { target ->
            if (target.has("rawQuery") && target["rawQuery"].isBoolean && target["rawQuery"].booleanValue()) {
                val query = target["query"].textValue().replace("\n", " ")
                val matchResult = tableRegex.find(query)
                if (matchResult != null) {
                    val policy = matchResult.groupValues[1].replace("\"", "").ifEmpty { "default" }
                    val table = matchResult.groupValues[2].replace("\"", "")
                    "$policy\\.$table"
                } else {
                    null
                }
            } else {
                if (target.hasNonNull("measurement")) {
                    val policy = (target["policy"]?.textValue() ?: "default").ifEmpty { "default" }
                    val table = target["measurement"].textValue()
                    "$policy\\.$table"
                } else {
                    null
                }
            }
        }.orEmpty().filterNotNull()
    }
}

class KairosProcessor: DatasourceProcessor() {
    override fun hasMetrics(dashboard: Dashboard, metrics: List<String>): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getMetrics(dashboard: Dashboard): List<String> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}