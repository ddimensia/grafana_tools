package com.digitaldimensia.grafana.model

data class DashboardMetadata(
    val id: Int,
    val uid: String,
    val title: String,
    val url: String,
    val type: String,
    val tags: List<String>,
    val isStarred: Boolean,
    val uri: String
)
