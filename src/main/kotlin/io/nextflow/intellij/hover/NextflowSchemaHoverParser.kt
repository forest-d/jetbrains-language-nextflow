package io.nextflow.intellij.hover

import com.google.gson.JsonElement
import com.google.gson.JsonParser

object NextflowSchemaHoverParser {
    fun findParamNames(json: String): List<String> {
        val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return emptyList()
        val properties = root.getAsJsonObject("properties") ?: return emptyList()
        return properties.entrySet().map { it.key }
    }

    fun findParam(json: String, name: String): SchemaParam? {
        val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return null
        val properties = root.getAsJsonObject("properties") ?: return null
        val param = properties.getAsJsonObject(name) ?: return null

        return SchemaParam(
            type = param.getString("type"),
            description = param.getString("description"),
            defaultValue = param.get("default")?.render(),
            enumValues = param.getAsJsonArray("enum")
                ?.mapNotNull { it.render() }
                .orEmpty(),
        )
    }

    private fun com.google.gson.JsonObject.getString(name: String): String? {
        return get(name)?.takeIf { it.isJsonPrimitive }?.asString
    }

    private fun JsonElement.render(): String? {
        return when {
            isJsonNull -> null
            isJsonPrimitive -> asJsonPrimitive.let { primitive ->
                when {
                    primitive.isString -> primitive.asString
                    primitive.isBoolean -> primitive.asBoolean.toString()
                    primitive.isNumber -> primitive.asNumber.toString()
                    else -> primitive.toString()
                }
            }
            else -> toString()
        }
    }
}
