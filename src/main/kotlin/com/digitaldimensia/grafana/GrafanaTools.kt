package com.digitaldimensia.grafana

import com.digitaldimensia.grafana.model.GrafanaCommandParams
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.findObject
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.int

fun main(args: Array<String>) {
    GrafanaTools().subcommands(FindDashboards()).main(args)
}

class GrafanaTools : CliktCommand() {
    private val hostname: String by option(
        "--host",
        help = "Grafana hostname (default: localhost)",
        metavar = "<hostname>"
    ).default("localhost")
    private val port: Int by option(help = "Grafana port (default: 8080)", metavar = "<port>").int().default(8080)
    private val dryRun: Boolean by option(help = "Don't actually do anything").flag()
    private val config by findObject { GrafanaCommandParams() }

    override fun run() {
        config.url = "http://$hostname:$port"
        config.dryRun = dryRun
    }
}

class FindDashboards : CliktCommand(name = "findDashboards", help = "Find dashboards using a specified datasource") {
    private val datasourceName: String by option("-d", "--datasource", help = "Datasource name").required()
    private val config by requireObject<GrafanaCommandParams>()
    override fun run() {
        DashboardFinder(config, datasourceName).run()
    }
}
