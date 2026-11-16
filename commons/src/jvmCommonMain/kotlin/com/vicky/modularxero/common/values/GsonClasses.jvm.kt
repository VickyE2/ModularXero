package com.vicky.modularxero.common.values

import kotlin.reflect.KClass

actual fun getEnumConstants(kClass: KClass<*>): List<Enum<*>> {
    val javaClass = kClass.java
    return javaClass.enumConstants?.toList() as? List<Enum<*>> ?: emptyList()
}

actual object KmpClassResolver {
    private val extraLoaders = mutableListOf<ClassLoader>()

    fun registerExtraLoader(loader: ClassLoader) {
        extraLoaders += loader
        println("ClassLoader added: $loader")
    }

    private val primitiveMappings = mapOf<String, KClass<*>>(
        "kotlin.Char" to Char::class,
        "kotlin.String" to String::class,
        "kotlin.Int" to Int::class,
        "kotlin.Long" to Long::class,
        "kotlin.Boolean" to Boolean::class,
        "kotlin.Float" to Float::class,
        "kotlin.Double" to Double::class,
        "kotlin.Byte" to Byte::class,
        "kotlin.Short" to Short::class,

        "kotlin.ByteArray" to ByteArray::class,
        "kotlin.CharArray" to CharArray::class,
        "kotlin.IntArray" to IntArray::class,
        "kotlin.LongArray" to LongArray::class,
        "kotlin.BooleanArray" to BooleanArray::class,
        "kotlin.FloatArray" to FloatArray::class,
        "kotlin.DoubleArray" to DoubleArray::class
    )

    actual fun resolve(className: String): KClass<*> {
        // 2. Check primitives first
        primitiveMappings[className]?.let { return it }

        val candidates = sequence<ClassLoader?> {
            yield(Thread.currentThread().contextClassLoader)
            yield(KmpClassResolver::class.java.classLoader)
            yield(ClassLoader.getSystemClassLoader())
            extraLoaders.forEach { yield(it) }
        }

        for (loader in candidates) {
            try {
                if (loader != null) {
                    return Class.forName(className, true, loader).kotlin
                }
            } catch (_: Throwable) {
                // try next
            }
        }

        // 4. Fallback for unexpected reflection targets
        return Class.forName(className).kotlin
    }
}