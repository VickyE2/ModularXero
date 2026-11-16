package com.vicky.modularxero.common


import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.vicky.modularxero.common.Response.ResponseStatus
import com.vicky.modularxero.common.values.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlin.reflect.KClass
import kotlin.reflect.cast
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object ResponseHelper {

    @OptIn(ExperimentalUuidApi::class)
    fun ok(type: HandleableServerMessage, payload: MapValue, id: Uuid): Response<MapValue> {
        val payloaded = payload.get().toMutableMap()
        payloaded["id"] = UUIDValue(id)
        return Response(type, MapValue(payloaded), ResponseStatus.OK)
    }

    @OptIn(ExperimentalUuidApi::class)
    fun ok(type: HandleableServerMessage, vararg pairs: Pair<String, MessageValue>, id: Uuid): Response<MapValue> {
        val mutableMap = mutableMapOf<String, MessageValue>()
        mutableMap["id"] = UUIDValue(id)
        mutableMap.putAll(pairs.toMap())
        return Response(type, MapValue(mutableMap.toMap()), ResponseStatus.OK)
    }

    @OptIn(ExperimentalUuidApi::class)
    fun okSimple(type: HandleableServerMessage, vararg pairs: Pair<String, Any?>, id: Uuid): Response<MapValue> {
        val fPairs = mutableListOf<Pair<String, Any?>>()
        fPairs.addAll(pairs)
        fPairs.add("id" to id)
        return Response(type, mapOfAny(*fPairs.toTypedArray()), ResponseStatus.OK)
    }

    @OptIn(ExperimentalUuidApi::class)
    fun error(type: HandleableServerMessage, reason: String, id: Uuid): Response<MapValue> =
        Response(
            type,
            MapValue(mapOf<String, MessageValue>(
                "reason" to StringValue(reason),
                "id" to UUIDValue(id),
            )),
            ResponseStatus.FAILED
        )

    @OptIn(ExperimentalUuidApi::class)
    fun unauthorised(type: HandleableServerMessage, id: Uuid, reason: String = "server refused message. unauthorized."): Response<MapValue> =
        Response(
            type,
            MapValue(mapOf<String, MessageValue>(
                "reason" to StringValue(reason),
                "id" to UUIDValue(id),
            )),
            ResponseStatus.UNAUTHORIZED
        )

    @OptIn(ExperimentalUuidApi::class)
    fun error(type: HandleableServerMessage, reason: String, id: Uuid, vararg pairs: Pair<String, Any?>): Response<MapValue> =
        Response(
            type,
            MapValue(stdMapOfAny(*pairs)
                    + mapOf<String, MessageValue>(
                "reason" to StringValue(reason),
                "id" to UUIDValue(id),
            )),
            ResponseStatus.FAILED
        )

    @OptIn(ExperimentalUuidApi::class)
    fun pending(type: HandleableServerMessage, id: Uuid): Response<MapValue> =
        Response(type, MapValue(mapOf<String, MessageValue>(
            "id" to UUIDValue(id)
        )), ResponseStatus.PENDING)

    fun MapValue.hasKey(key: String): Boolean {
        return this.get().containsKey(key)
    }

    fun <T : MessageValue> MapValue.getValue(key: String, clazz: KClass<T>): T? {
        val v = this.get()[key] ?: return null
        return if (clazz.isInstance(v)) clazz.cast(v) else null
    }

    fun <T : Any> MapValue.getValue(key: String, clazz: KClass<T>): T? {
        val v = this.get()[key] ?: return null
        return if (clazz.isInstance(v)) clazz.cast(v) else null
    }

    inline fun <reified T : Any> MapValue.getObject(key: String): T? =
        (getValue(key, ObjectValue::class)?.get() as? T)

    fun <T : Any> MapValue.getObject(
        key: String,
        clazz: KClass<T>
    ): T? {
        val wrapper = getValue(key, ObjectValue::class) ?: return null

        val obj = wrapper.get()
        return clazz.cast(obj)
    }

    fun MapValue.getString(key: String, default: String): String =
        getValue(key, StringValue::class)?.get() ?: default

    fun MapValue.getInt(key: String, default: Int): Int =
        getValue(key, IntegerValue::class)?.get() ?: default

    fun MapValue.getBoolean(key: String, default: Boolean): Boolean =
        getValue(key, BooleanValue::class)?.get() ?: default

    fun MapValue.getTimeValue(key: String, default: LocalDateTime): LocalDateTime =
        getValue(key, TimestampValue::class)?.get() ?: default

    fun <T> MapValue.getList(key: String, default: List<T>): List<T> {
        val list = (getValue(key, ListValue::class)?.get() ?: return default) as List<Any>

        return list.map { value ->
            when (value) {
                is ObjectValue ->
                    value.get() as T

                is MessageValue ->
                    value.getWrapped() as T

                else ->
                    error("Unexpected list element: ${value::class}")
            }
        }
    }

    fun MapValue.getFloat(key: String, default: Float): Float =
        getValue(key, FloatValue::class)?.get() ?: default

    fun MapValue.getDouble(key: String, default: Double): Double =
        getValue(key, DoubleValue::class)?.get() ?: default

    fun MapValue.getLong(key: String, default: Long): Long =
        getValue(key, LongValue::class)?.get() ?: default

    @OptIn(ExperimentalUuidApi::class)
    fun MapValue.getUUID(key: String, default: Uuid): Uuid =
        getValue(key, UUIDValue::class)?.get() ?: default

    fun MapValue.getPair(key: String, default: Pair<String, *>): Pair<String, *> =
        getValue(key, PairValue::class)?.get() ?: default

    fun MapValue.getCurrency(key: String, default: Pair<BigDecimal, String>): Pair<BigDecimal, String> {
        val curr = getValue(key, CurrencyValue::class)
        return if (curr != null) (curr.get() to curr.symbol) else default
    }

    fun <T : Enum<T>> MapValue.getEnum(key: String, enumClass: KClass<T>, default: T): T {
        val curr = getValue(key, EnumValue::class)
        return curr?.get()?.let { enumClass.cast(it) } ?: default
    }

    fun <V> MapValue.getMap(key: String, default: Map<String, V>): Map<String, V> =
        getValue(key, MapValue::class)?.toFullMap() as? Map<String, V> ?: default

    fun MapValue.getMapValue(key: String, default: Map<String, *>): MapValue =
        getValue(key, MessageValue::class) as? MapValue? ?: default.toMessageValue()

    fun MapValue.getMapValue(key: String, default: MapValue): MapValue =
        getValue(key, MessageValue::class) as? MapValue? ?: default




    fun MapValue.getString(key: String): String? =
        getValue(key, StringValue::class)?.get()

    fun MapValue.getInt(key: String): Int? =
        getValue(key, IntegerValue::class)?.get()

    fun MapValue.getKClass(key: String): KClass<*>? =
        getValue(key, KClassValue::class)?.get()

    fun MapValue.getBoolean(key: String): Boolean? =
        getValue(key, BooleanValue::class)?.get()

    fun MapValue.getTimeValue(key: String): LocalDateTime? =
        getValue(key, TimestampValue::class)?.get()

    fun <T> MapValue.getList(key: String): List<T>? {
        val list = (getValue(key, ListValue::class)?.get() ?: return null) as List<Any>

        return list.map { value ->
            when (value) {
                is ObjectValue ->
                    value.get() as T

                is MessageValue ->
                    value.getWrapped() as T

                else ->
                    error("Unexpected list element: ${value::class}")
            }
        }
    }

    fun <V> MapValue.getMap(key: String): Map<String, V>? =
        getValue(key, MapValue::class)?.toFullMap() as? Map<String, V>

    fun MapValue.getFloat(key: String): Float? =
        getValue(key, FloatValue::class)?.get()

    fun MapValue.getDouble(key: String): Double? =
        getValue(key, DoubleValue::class)?.get()

    fun MapValue.getLong(key: String): Long? =
        getValue(key, LongValue::class)?.get()

    @OptIn(ExperimentalUuidApi::class)
    fun MapValue.getUUID(key: String): Uuid? =
        getValue(key, UUIDValue::class)?.get()

    fun MapValue.getPair(key: String): Pair<String, *>? =
        getValue(key, PairValue::class)?.get()

    fun MapValue.getCurrency(key: String): Pair<BigDecimal, String>? {
        val curr = getValue(key, CurrencyValue::class)
        return if (curr != null) (curr.get() to curr.symbol) else null
    }

    fun <T : Enum<T>> MapValue.getEnum(key: String, enumClass: KClass<T>): T? {
        val curr = getValue(key, EnumValue::class)
        return curr?.get()?.let { enumClass.cast(it) }
    }

    fun MapValue.getMapValue(key: String): MapValue? =
        getValue(key, MessageValue::class) as? MapValue?

    fun MapValue.getStrings(vararg keys: String): Map<String, String?> =
        keys.associateWith { getString(it) }

    fun MapValue.getInts(vararg keys: String): Map<String, Int?> =
        keys.associateWith { getInt(it) }

    // --- BUILD MapValue easily ---
    fun mapOf(vararg pairs: Pair<String, MessageValue>) = MapValue(pairs.toMap())

    fun mapOfAny(vararg pairs: Pair<String, Any?>): MapValue =
        MapValue(pairs.associate { (k, v) ->
            k to getValueWrapper(v)
        })

    fun stdMapOfAny(vararg pairs: Pair<String, Any?>): Map<String, MessageValue> =
        pairs.associate { (k, v) ->
            k to getValueWrapper(v)
        }
}

private fun <K, V> Map<K, V>.toMessageValue(): MapValue {
    return MapValueBuilder().doing {
        forEach { (key, value) ->
            put(key.toString(), getValueWrapper(value))
        }
    }.build()
}

@OptIn(ExperimentalUuidApi::class)
fun getValueWrapper(v: Any?) : MessageValue {
    return when (v) {
        is Boolean -> BooleanValue(v)
        is BigDecimal -> CurrencyValue(v, "NGN")
        is Double -> DoubleValue(v)
        is Enum<*> -> EnumValue(v)
        is Float -> FloatValue(v)
        is Int -> IntegerValue(v)
        is KClass<*> -> KClassValue(v)
        is List<*> -> ListValue(v.map { getValueWrapper(it) })
        is Map<*, *> -> MapValue(v.mapKeys { it.key.toString() }.mapValues { getValueWrapper(it.value) })
        is Long -> LongValue(v)
        is String -> StringValue(v)
        is Uuid -> UUIDValue(v)
        is Pair<*, *> -> PairValue(v.first.toString() to getValueWrapper(v.second))
        is LocalDateTime -> TimestampValue(v)
        null -> StringValue("null")
        else -> ObjectValue(v)
    }
}

@OptIn(ExperimentalUuidApi::class)
fun MessageValue.getWrapped() : Any {
    return when (this) {
        is MapValue -> this.toFullMap()
        else -> this.get()
    }
}

@OptIn(ExperimentalUuidApi::class)
fun MessageValue.getAnyWrapped() : Any = this.getWrapped()

fun <T> MapValue.toMap(transformer: (String) -> T) : Map<String, T> {
    val map = mutableMapOf<String, T>();
    for ((entry, value) in this.get()) {
        map.put(entry, transformer(value.get().toString()))
    }
    return map
}

fun MapValue.toMap() : Map<String, String> {
    val map = mutableMapOf<String, String>();
    for ((entry, value) in this.get()) {
        map.put(entry, value.toString())
    }
    return map
}

fun MapValue.toFullMap() : Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    for ((entry, value) in this.get()) {
        map[entry] = value.getWrapped()
    }
    return map
}

class MapValueBuilder {
    private val map = mutableMapOf<String, MessageValue>()

    /** Add a single key/value pair */
    fun put(key: String, value: MessageValue): MapValueBuilder {
        map[key] = value
        return this
    }

    fun put(key: String, v: Any): MapValueBuilder {
        val possibleTyped = getValueWrapper(v)

        map[key] = possibleTyped
        return this
    }

    /** Add multiple pairs at once */
    fun putAll(vararg pairs: Pair<String, MessageValue>): MapValueBuilder {
        map.putAll(pairs)
        return this
    }

    /** Append another MapValue's entries */
    fun append(other: MapValue): MapValueBuilder {
        map.putAll(other.get())
        return this
    }

    /** Convert builder to immutable MapValue */
    fun build(): MapValue = MapValue(map.toMap())
}

fun MapValueBuilder.doing(function: MapValueBuilder.() -> Unit): MapValueBuilder {
    this.function()
    return this
}

/**
 * Deserializes a JSON string into a Response<T> wrapper.
 */
inline fun <reified T : MessageValue> String.getAsResponse(): Response<T> =
    gson.decodeFromString<Response<T>>(this)

/**
 * Deserializes a JSON string directly into a MessageValue sub-type.
 */
inline fun <reified T : MessageValue> String.getAsMessage(): T =
    gson.decodeFromString<T>(this)

fun <T : MessageValue> Response<T>.toJson(): String =
    gson.encodeToString(this)

inline fun <reified T : MessageValue> T.toJson(): String =
    gson.encodeToString(this)

fun JsonElement.toMessageValue(): MessageValue =
    when (this) {
        is JsonNull -> StringValue("null")
        is JsonPrimitive -> {
            when {
                // Check if boolean
                isString.not() && booleanOrNull != null -> {
                    BooleanValue(boolean)
                }
                // Check if number (Long vs Double)
                isString.not() -> {
                    val contentString = content
                    if (contentString.contains('.') || contentString.contains('e') || contentString.contains('E')) {
                        DoubleValue(doubleOrNull ?: 0.0)
                    } else {
                        LongValue(longOrNull ?: 0L)
                    }
                }
                // String primitive
                else -> StringValue(content)
            }
        }

        is JsonArray -> ListValue(
            map { it.toMessageValue() }
        )

        is JsonObject -> MapValue(
            mapValues { (_, value) -> value.toMessageValue() }
        )
    }

fun String.parseJson(): JsonElement = gson.decodeFromString(this)

val jsonModule = SerializersModule {
    contextual(KmpLocalDateTimeSerializer)
    contextual(KmpLocalDateSerializer)
    contextual(KmpLocalTimeSerializer)
    contextual(KmpBigDecimalSerializer)
    contextual(KmpBigIntegerSerializer)
    contextual(KClassSerializer)
    contextual(Base64ByteArraySerializer)
    contextual(CharSerializer)

    polymorphic(MessageValue::class) {
        subclass(MapValue::class)
        subclass(EnumValue::class, EnumValueAdapter)
        subclass(ObjectValue::class, ObjectValueSerializer)
    }
}

val gson = Json {
    // Sets the property name used to distinguish polymorphic types
    classDiscriminator = "valueType"
    prettyPrint = true

    // Optional: ignore unknown fields if payload has extra keys
    ignoreUnknownKeys = true
    encodeDefaults = true

    serializersModule = jsonModule
}