package com.vicky.modularxero.common.values

import kotlin.reflect.KClass

actual object KmpClassResolver {
    private val classes = mutableMapOf<String, KClass<*>>()

    fun register(
        name: String,
        clazz: KClass<*>
    ) {
        classes[name] = clazz
    }

    actual fun resolve(
        className: String
    ): KClass<*> {
        return classes[className]!!
    }

    private val enumRegistry = mutableMapOf<KClass<*>, List<Enum<*>>>()

    fun <T : Enum<T>> registerEnum(kClass: KClass<T>, entries: List<T>) {
        enumRegistry[kClass] = entries
    }

    fun getEntries(kClass: KClass<*>): List<Enum<*>> {
        return enumRegistry[kClass] ?: emptyList()
    }
}

actual fun getEnumConstants(kClass: KClass<*>): List<Enum<*>> {
    return KmpClassResolver.getEntries(kClass)
}