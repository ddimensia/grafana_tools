package com.digitaldimensia.grafana.model

data class Dashboard ( val dashboard: DashboardDef )

data class DashboardDef(val rows: List<DashboardRow>?, val panels: List<Panel>?)

data class DashboardRow(val panels: List<Panel>?)

data class Panel(val datasource: String?)
