package com.digitaldimensia.grafana.model

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.JsonNode

data class Dashboard ( val dashboard: DashboardDef )

data class DashboardDef(val rows: List<DashboardRow>?, val panels: List<Panel>?, val templating: Templating?, val tags: List<String>?)

data class DashboardRow(val panels: List<Panel>?)

data class Panel(val datasource: String?, val targets: List<JsonNode>?)

data class Templating(val list: List<Variable>?)

@JsonTypeInfo(property = "type", use = JsonTypeInfo.Id.NAME)
@JsonSubTypes(
    JsonSubTypes.Type(name = "query", value = QueryVariable::class),
    JsonSubTypes.Type(name = "custom", value = CustomVariable::class),
    JsonSubTypes.Type(name = "constant", value = ConstantVariable::class),
    JsonSubTypes.Type(name = "interval", value = IntervalVariable::class)
)
sealed class Variable(val name: String)

class QueryVariable(name: String, val datasource: String, val query : String) : Variable(name)
class CustomVariable(name: String, val options: List<VariableOption>?): Variable(name)
class ConstantVariable(name: String, val query: String): Variable(name)
class IntervalVariable(name: String): Variable(name)
class VariableOption(val value: String)
