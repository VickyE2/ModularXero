package com.vicky.modularxero.common

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.vicky.modularxero.common.Response.ResponseStatus
import com.vicky.modularxero.common.ResponseHelper.getValue
import com.vicky.modularxero.common.values.*
import java.lang.reflect.Type
import java.math.BigDecimal

class RuntimeTypeAdapterFactory<T>(
    private val baseType: Class<*>,
    private val typeFieldName: String
) : TypeAdapterFactory {
    private val labelToSubtype: MutableMap<String, Class<out T>> = mutableMapOf()

    fun registerSubtype(type: Class<out T>, label: String = type.simpleName): RuntimeTypeAdapterFactory<T> {
        labelToSubtype[label] = type
        return this
    }

    override fun <R> create(gson: Gson, type: TypeToken<R>): TypeAdapter<R>? {
        val rawType: Class<in R> = type.rawType
        if (!baseType.isAssignableFrom(rawType)) return null

        val jsonElementAdapter = gson.getAdapter(JsonElement::class.java)
        val subtypeToDelegate = labelToSubtype.mapValues { (_, subtype) ->
            gson.getDelegateAdapter(this, TypeToken.get(subtype))
        }

        @Suppress("UNCHECKED_CAST")
        return object : TypeAdapter<R>() {
            override fun write(out: JsonWriter, value: R) {
                val srcType = value!!::class.java
                val label = labelToSubtype.entries.find { it.value == srcType }?.key
                    ?: throw JsonParseException("Unknown subtype: $srcType")

                val delegate = subtypeToDelegate[label] as TypeAdapter<R>
                val jsonObj = delegate.toJsonTree(value).asJsonObject

                jsonObj.addProperty(typeFieldName, label)
                jsonElementAdapter.write(out, jsonObj)
            }

            override fun read(reader: JsonReader): R {
                val jsonObj = jsonElementAdapter.read(reader).asJsonObject
                val label = jsonObj[typeFieldName].asString
                val subtype = labelToSubtype[label]
                    ?: throw JsonParseException("Unknown type label: $label")

                val delegate = subtypeToDelegate[label] as TypeAdapter<R>
                return delegate.fromJsonTree(jsonObj)
            }
        } as TypeAdapter<R>
    }
}

object ResponseHelper {

    fun ok(type: MessageType, payload: MapValue<MessageValue<*>>): Response<MapValue<MessageValue<*>>> =
        Response(type, payload, ResponseStatus.OK)

    fun ok(type: MessageType, vararg pairs: Pair<String, MessageValue<*>>): Response<MapValue<MessageValue<*>>> =
        Response(type, MapValue(pairs.toMap()), ResponseStatus.OK)

    fun okSimple(type: MessageType, vararg pairs: Pair<String, Any?>): Response<MapValue<MessageValue<*>>> =
        Response(type, mapOfAny(*pairs), ResponseStatus.OK)

    fun error(type: MessageType, reason: String): Response<MapValue<MessageValue<*>>> =
        Response(
            type,
            MapValue(mapOf<String, MessageValue<*>>("reason" to StringValue(reason))),
            ResponseStatus.FAILED
        )

    fun pending(type: MessageType): Response<MapValue<MessageValue<*>>> =
        Response(type, MapValue(emptyMap()), ResponseStatus.PENDING)

    fun <T : MessageValue<*>> MapValue<MessageValue<*>>.getValue(key: String, clazz: Class<T>): T? {
        val v = this.get()[key] ?: return null
        return if (clazz.isInstance(v)) clazz.cast(v) else null
    }

    fun MapValue<MessageValue<*>>.getString(key: String): String? =
        getValue(key, StringValue::class.java)?.get()

    fun MapValue<MessageValue<*>>.getInt(key: String): Int? =
        getValue(key, IntegerValue::class.java)?.get()

    fun MapValue<MessageValue<*>>.getBoolean(key: String): Boolean? =
        getValue(key, BooleanValue::class.java)?.get()

    fun MapValue<MessageValue<*>>.getTimeValue(key: String): Long? =
        getValue(key, TimestampValue::class.java)?.get()

    fun <T> MapValue<MessageValue<*>>.getList(key: String): List<T>? =
        getValue(key, ListValue::class.java)?.get() as List<T>?

    fun MapValue<MessageValue<*>>.getFloat(key: String): Float? =
        getValue(key, FloatValue::class.java)?.get()

    fun MapValue<MessageValue<*>>.getDouble(key: String): Double? =
        getValue(key, DoubleValue::class.java)?.get()

    fun MapValue<MessageValue<*>>.getCurrency(key: String): Pair<BigDecimal, String>? {
        val curr = getValue(key, CurrencyValue::class.java)
        return if (curr != null) (curr.get() to curr.symbol) else null
    }

    fun <T : Enum<T>> MapValue<MessageValue<*>>.getEnum(key: String, enumClass: Class<T>): T? {
        val curr = getValue(key, EnumValue::class.java)
        return curr?.get()?.let { enumClass.cast(it) }
    }

    fun MapValue<MessageValue<*>>.getMap(key: String): MapValue<MessageValue<*>>? =
        getValue(key, MessageValue::class.java) as? MapValue<MessageValue<*>>?

    fun MapValue<MessageValue<*>>.getStrings(vararg keys: String): Map<String, String?> =
        keys.associateWith { getString(it) }

    fun MapValue<MessageValue<*>>.getInts(vararg keys: String): Map<String, Int?> =
        keys.associateWith { getInt(it) }

    // --- BUILD MapValue easily ---
    fun mapOf(vararg pairs: Pair<String, MessageValue<*>>) = MapValue(pairs.toMap())

    fun mapOfAny(vararg pairs: Pair<String, Any?>): MapValue<MessageValue<*>> =
        MapValue(pairs.associate { (k, v) ->
            k to when (v) {
                is String -> StringValue(v)
                is Int -> IntegerValue(v)
                is Boolean -> BooleanValue(v)
                is Float -> FloatValue(v)
                is Double -> DoubleValue(v)
                is BigDecimal -> CurrencyValue(v, "NGN") // default currency
                is Enum<*> -> EnumValue(v)
                is List<*> -> ListValue(v.map { StringValue(it.toString()) }) // safe fallback
                null -> StringValue("null")
                else -> StringValue(v.toString())
            }
        })
}

class MapValueBuilder {
    private val map = mutableMapOf<String, MessageValue<*>>()

    /** Add a single key/value pair */
    fun put(key: String, value: MessageValue<*>): MapValueBuilder {
        map[key] = value
        return this
    }

    fun put(key: String, v: Any): MapValueBuilder {
        val possibleTyped = when (v) {
            is String -> StringValue(v)
            is Int -> IntegerValue(v)
            is Boolean -> BooleanValue(v)
            is Float -> FloatValue(v)
            is Double -> DoubleValue(v)
            is BigDecimal -> CurrencyValue(v, "NGN")
            is Enum<*> -> EnumValue(v)
            is List<*> -> ListValue(v.map { StringValue(it.toString()) })
            null -> StringValue("null")
            else -> StringValue(v.toString())
        }

        map[key] = possibleTyped
        return this
    }

    /** Add multiple pairs at once */
    fun putAll(vararg pairs: Pair<String, MessageValue<*>>): MapValueBuilder {
        map.putAll(pairs)
        return this
    }

    /** Append another MapValue's entries */
    fun append(other: MapValue<MessageValue<*>>): MapValueBuilder {
        map.putAll(other.get())
        return this
    }

    /** Convert builder to immutable MapValue */
    fun build(): MapValue<MessageValue<*>> = MapValue(map.toMap())
}

val messageValueAdapterFactory = RuntimeTypeAdapterFactory<MessageValue<*>>(
    MessageValue::class.java, "valueType"
)
    .registerSubtype(StringValue::class.java, "StringValue")
    .registerSubtype(BooleanValue::class.java, "BooleanValue")
    .registerSubtype(IntegerValue::class.java, "IntegerValue")
    .registerSubtype(FloatValue::class.java, "FloatValue")
    .registerSubtype(DoubleValue::class.java, "DoubleValue")
    .registerSubtype(MapValue::class.java, "MapValue")
    .registerSubtype(ListValue::class.java, "ListValue")
    .registerSubtype(TimestampValue::class.java, "TimestampValue")
    .registerSubtype(CurrencyValue::class.java, "CurrencyValue")
    .registerSubtype(EnumValue::class.java, "EnumValue")

inline fun <reified T : MessageValue<*>> String.getAsResponse(): Response<T> =
    gson.fromJson(this, object : TypeToken<Response<T>>() {}.type)

inline fun <reified T : MessageValue<*>> String.getAsMessage(): T =
    gson.fromJson(this, object : TypeToken<T>() {}.type)

fun <T : MessageValue<*>> Response<T>.toJson(): String =
    gson.toJson(this)

fun <T : MessageValue<*>> T.toJson(): String =
    gson.toJson(this)

val gson = GsonBuilder()
    .registerTypeAdapterFactory(messageValueAdapterFactory)
    .setPrettyPrinting()
    .create()