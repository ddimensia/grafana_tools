package com.digitaldimensia.grafana

enum class DatasourceType(val processor: DatasourceProcessor) {
    INFLUX(InfluxProcessor()),
    KAIROS(KairosProcessor())
}