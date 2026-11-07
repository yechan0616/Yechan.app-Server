package app.yechan.server.domain.content.entity

import app.yechan.server.global.common.json.Json
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.readValue

@Converter
class ObjectNodeConverter : AttributeConverter<ObjectNode?, String?> {
    override fun convertToDatabaseColumn(attribute: ObjectNode?): String? =
        attribute?.let(Json.mapper::writeValueAsString)

    override fun convertToEntityAttribute(dbData: String?): ObjectNode? =
        dbData?.let {
            Json.mapper.readTree(it) as ObjectNode
        }
}

@Converter
class FieldDefinitionsConverter : AttributeConverter<List<FieldDefinition>, String> {
    override fun convertToDatabaseColumn(attribute: List<FieldDefinition>): String =
        Json.mapper.writeValueAsString(attribute)

    override fun convertToEntityAttribute(dbData: String): List<FieldDefinition> = Json.mapper.readValue(dbData)
}
