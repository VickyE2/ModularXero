package com.vicky.modularxero.common.values

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.reflect.KClass
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Wraps a Boolean.  */
@Serializable
@SerialName("BooleanValue")
class BooleanValue(@Serializable val value: Boolean) : MessageValue {
    override fun get(): Boolean = value
}

/** Wraps an empty.  */
@Serializable
@SerialName("EmptyMessageValue")
object EmptyMessageValue : MessageValue {
    override fun get(): Nothing = throw IllegalArgumentException("EmptyMessageValue does not have a value")
}

/**
 * Wraps a monetary amount plus its currency symbol.
 * Under the hood uses BigDecimal for precision.
 */
@Serializable
@SerialName("CurrencyValue")
class CurrencyValue(@Contextual val amount: BigDecimal, @Serializable val symbol: String) : MessageValue {
    override fun get(): BigDecimal = amount

    override fun toString(): String {
        return symbol + " " + get().toPlainString()
    }
}

@Serializable
@SerialName("DoubleValue")
class DoubleValue(val value: Double) : MessageValue {
    override fun get(): Double = value
}

expect object KmpClassResolver {
    fun resolve(className: String): KClass<*>
}
expect fun getEnumConstants(kClass: KClass<*>): List<Enum<*>>

object EnumValueAdapter : KSerializer<EnumValue> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("EnumValue") {
            element<String>("enumClassName")
            element<String>("value")
        }

    override fun serialize(
        encoder: Encoder,
        value: EnumValue
    ) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException(
                "EnumValueAdapter only supports JSON"
            )

        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                put("enumClassName", value.enumClassName)
                put("value", value.value.name)
            }
        )
    }

    override fun deserialize(
        decoder: Decoder
    ): EnumValue {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException(
                "EnumValueAdapter only supports JSON decoding"
            )

        val jsonObject = jsonDecoder.decodeJsonElement()
                as? JsonObject
            ?: throw SerializationException(
                "Expected JsonObject for EnumValue"
            )

        val className = jsonObject["enumClassName"]
            ?.jsonPrimitive
            ?.content
            ?: throw SerializationException(
                "Missing enumClassName for EnumValue"
            )

        val enumName = jsonObject["value"]
            ?.jsonPrimitive
            ?.content
            ?: throw SerializationException(
                "Missing value for EnumValue"
            )

        val enumClass = KmpClassResolver.resolve(className)

        try {
            Enum::class.isInstance(enumClass)
        } catch (_: Exception) {
            throw SerializationException(
                "$className is not an enum"
            )
        }

        val enumConstant = getEnumConstants(enumClass)
            .firstOrNull { it.name == enumName }
            ?: throw SerializationException(
                "Enum constant '$enumName' not found in $className"
            )

        @Suppress("UNCHECKED_CAST")
        return EnumValue(enumConstant)
    }
}

// @Serializable(with = EnumValueAdapter::class)
@SerialName("EnumValue")
class EnumValue(val value: Enum<*>) : MessageValue {
    val enumClassName: String
        get() = value::class.qualifiedName
            ?: error("Enum class has no qualified name")

    override fun get(): Enum<*> = value
}

@Serializable
@SerialName("FloatValue")
class FloatValue(val value: Float) : MessageValue {
    override fun get(): Float = value
}

@Serializable
@SerialName("IntegerValue")
class IntegerValue(val value: Int) : MessageValue {
    override fun get(): Int = value
}

/**
 * Wraps any List of MessageValue (or raw) elements.
 */
@Serializable
@SerialName("ListValue")
class ListValue(val list: List<MessageValue>) : MessageValue {
    override fun get(): List<MessageValue> = list
}

@Serializable
@SerialName("LongValue")
class LongValue(val value: Long) : MessageValue {
    override fun get(): Long = value
}

/**
 * Wraps any Map of MessageValue (or raw) entries.
 */
@Serializable
@SerialName("MapValue")
class MapValue(val map: Map<String, MessageValue>) : MessageValue {
    override fun get(): Map<String, MessageValue> = map
}

/**
 * Base wrapper for any value sent over the wire.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("valueType")
sealed interface MessageValue {
    fun get(): Any
}

/** Wraps a long kclasss.  */
@Serializable
@SerialName("KClassValue")
class KClassValue(@Serializable(with = KClassSerializer::class) val value: KClass<*>) : MessageValue {
    override fun get(): KClass<*> = value
}

object ObjectValueSerializer : KSerializer<ObjectValue> {

    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("ObjectValue") {
            element<String>("className")
            element<JsonElement>("value")
        }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: ObjectValue) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("ObjectValueSerializer only supports JSON encoding")

        val innerObject = value.get()

        val className = value.typeName

        // Dynamically serialize the inner value to a JsonElement
        @Suppress("UNCHECKED_CAST")
        val serializer = jsonEncoder.json.serializersModule.getContextual(innerObject::class)
                as? SerializationStrategy<Any>
            ?: throw SerializationException("No serializer found for class $className")

        val innerJsonElement = jsonEncoder.json.encodeToJsonElement(
            serializer = serializer,
            value = innerObject
        )

        val jsonObject = buildJsonObject {
            put("className", className)
            put("value", innerJsonElement)
        }

        jsonEncoder.encodeJsonElement(jsonObject)
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): ObjectValue {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("ObjectValueSerializer only supports JSON decoding")

        val jsonObject = jsonDecoder.decodeJsonElement() as? JsonObject
            ?: throw SerializationException("Expected JsonObject for ObjectValue")

        val className = jsonObject["className"]?.jsonPrimitive?.content
        val objectData = jsonObject["value"]

        if (className == null || objectData == null) {
            throw SerializationException("Missing className or objectData for ObjectValue")
        }

        // Resolve KClass via KmpClassResolver (works with JVM reflection or KMP registry)
        val kClass: KClass<*> = KmpClassResolver.resolve(className)

        // Find standard serializer for resolved KClass
        val serializer = jsonDecoder.json.serializersModule.getContextual(kClass)
            ?: throw SerializationException("No serializer found for resolved class $className")

        val obj = jsonDecoder.json.decodeFromJsonElement(serializer, objectData)

        return ObjectValue(obj)
    }
}

@Serializable(with = ObjectValueSerializer::class)
@SerialName("ObjectValue")
class ObjectValue(@Serializable val value: Any) : MessageValue {
    @Serializable
    val typeName: String
        get() = value::class.qualifiedName
            ?: throw SerializationException(
                "Cannot determine qualified name for ${value::class}"
            )

    override fun get(): Any = value
}

/**
 * Wraps any Pair of MessageValue (or raw) entries.
 */
@Serializable
@SerialName("PairValue")
class PairValue(@Serializable val map: Pair<String, MessageValue>) : MessageValue {
    constructor(key: String, value: MessageValue) : this(Pair<String, MessageValue>(key, value))

    override fun get(): Pair<String, MessageValue> = map
}

@Serializable
@SerialName("StringValue")
class StringValue(val value: String) : MessageValue {
    override fun get(): String = value
}

/** Wraps a long timestamp (epoch millis).  */
@Serializable
@SerialName("TimestampValue")
class TimestampValue(val value: LocalDateTime) : MessageValue {
    override fun get(): LocalDateTime = value
}

@OptIn(ExperimentalUuidApi::class)
@Serializable
@SerialName("UUIDValue")
class UUIDValue(val value: Uuid) : MessageValue {
    override fun get(): Uuid = value
}

// DEFAULT - SERIALIZERS

object KClassSerializer : KSerializer<KClass<*>> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("KClass", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: KClass<*>) {
        val className = value.qualifiedName ?: value.simpleName
        ?: throw IllegalArgumentException("Cannot serialize anonymous KClass")
        encoder.encodeString(className)
    }

    override fun deserialize(decoder: Decoder): KClass<*> {
        val className = decoder.decodeString()
        return KmpClassResolver.resolve(className)
    }
}
object Base64ByteArraySerializer : KSerializer<ByteArray> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Base64ByteArray", PrimitiveKind.STRING)

    @OptIn(ExperimentalEncodingApi::class)
    override fun serialize(encoder: Encoder, value: ByteArray) {
        // Turns bytes into a clean string: "iVBORw0KGgo..."
        encoder.encodeString(Base64.encode(value))
    }

    @OptIn(ExperimentalEncodingApi::class)
    override fun deserialize(decoder: Decoder): ByteArray {
        return Base64.decode(decoder.decodeString())
    }
}
object CharSerializer : KSerializer<Char> {
    // 1. Tell the framework to treat this as a standard String value
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Char", PrimitiveKind.STRING)

    // 2. Convert Char to a String when saving/sending
    override fun serialize(encoder: Encoder, value: Char) {
        encoder.encodeString(value.toString())
    }

    // 3. Convert String back to a Char when reading
    override fun deserialize(decoder: Decoder): Char {
        val stringValue = decoder.decodeString()
        if (stringValue.length != 1) {
            throw SerializationException("Expected a single Char string, but got a string of length ${stringValue.length}")
        }
        return stringValue[0]
    }
}
object KmpLocalDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        encoder.encodeString(value.toString()) // ISO-8601 string
    }

    override fun deserialize(decoder: Decoder): LocalDateTime {
        return LocalDateTime.parse(decoder.decodeString())
    }
}
object KmpLocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.toString()) // ISO-8601 string
    }

    override fun deserialize(decoder: Decoder): LocalDate {
        return LocalDate.parse(decoder.decodeString())
    }
}
object KmpLocalTimeSerializer : KSerializer<LocalTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalTime) {
        encoder.encodeString(value.toString()) // ISO-8601 string
    }

    override fun deserialize(decoder: Decoder): LocalTime {
        return LocalTime.parse(decoder.decodeString())
    }
}
object KmpBigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        encoder.encodeString(value.toPlainString())
    }

    override fun deserialize(decoder: Decoder): BigDecimal {
        return BigDecimal.parseString(decoder.decodeString())
    }
}
object KmpBigIntegerSerializer : KSerializer<BigInteger> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigInteger) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): BigInteger {
        return BigInteger.parseString(decoder.decodeString())
    }
}