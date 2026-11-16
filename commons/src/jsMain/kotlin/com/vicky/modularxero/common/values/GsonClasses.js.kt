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

    actual fun resolve(className: String): KClass<*> {
        return classes[className]
            ?: error("Class not registered: $className")
    }

    private val enumRegistry = mutableMapOf<KClass<*>, List<Enum<*>>>()

    fun registerEnum(
        kClass: KClass<*>,
        entries: List<Enum<*>>
    ) {
        enumRegistry[kClass] = entries
    }

    fun getEntries(kClass: KClass<*>): List<Enum<*>> {
        return enumRegistry[kClass].orEmpty()
    }
}

actual fun getEnumConstants(
    kClass: KClass<*>
): List<Enum<*>> {
    return KmpClassResolver.getEntries(kClass)
}