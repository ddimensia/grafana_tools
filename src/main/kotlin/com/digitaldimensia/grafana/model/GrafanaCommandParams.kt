package com.digitaldimensia.grafana.model

data class GrafanaCommandParams(
    var url: String = "http://localhost:8080",
    var dryRun: Boolean = true
)