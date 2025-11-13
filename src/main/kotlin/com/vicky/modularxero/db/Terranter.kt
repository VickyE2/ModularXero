package com.vicky.modularxero.db

import com.vicky.modularxero.AbstractModule
import com.vicky.modularxero.DataFolderName
import com.vicky.modularxero.Module
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.hibernate.SessionFactory
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.cfg.AvailableSettings
import org.hibernate.cfg.Environment
import org.hibernate.context.spi.CurrentTenantIdentifierResolver
import org.hibernate.engine.jdbc.connections.internal.DriverManagerConnectionProviderImpl
import org.hibernate.engine.jdbc.connections.spi.AbstractMultiTenantConnectionProvider
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.SQLException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier
import javax.sql.DataSource
import kotlin.io.path.exists


/**
 * Basic MultiTenantConnectionProvider that uses a single DataSource.
 * It switches the active schema/catalog for the returned connection so the tenant is isolated.
 *
 */
class ModuleDataSourceMultiTenantProvider @JvmOverloads constructor(
    private val defaultTenantId: String = "global"
) : MultiTenantConnectionProvider<String>
{

    // A tiny cache for DataSource instances is okay – ModuleDatabaseManager already caches,
    // but we might forward to it for clarity.
    override fun getAnyConnection(): Connection {
        // Provide a fallback connection (global tenant) for operations that require any connection
        val ds = ModuleDatabaseManager.getOrCreateDataSource(defaultTenantId)
        return ds.connection
    }

    override fun releaseAnyConnection(connection: Connection?) {
        try {
            connection?.close()
        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }

    override fun getConnection(tenantIdentifier: String): Connection {
        // get or create a DataSource for this tenant and return a connection
        val ds = ModuleDatabaseManager.getOrCreateDataSource(tenantIdentifier)
        return ds.connection
    }

    override fun releaseConnection(tenantIdentifier: String?, connection: Connection?) {
        try {
            connection?.close()
        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }

    override fun supportsAggressiveRelease(): Boolean = false

    override fun isUnwrappableAs(clazz: Class<*>): Boolean =
        clazz.isAssignableFrom(this::class.java)

    override fun <T> unwrap(clazz: Class<T>): T? =
        if (clazz.isAssignableFrom(this::class.java)) this as T else null
}
class SchemaMultiTenantConnectionProvider : AbstractMultiTenantConnectionProvider<String>() {
    private val connectionProvider: ConnectionProvider

    init {
        connectionProvider = initConnectionProvider()
    }

    override fun getAnyConnectionProvider(): ConnectionProvider {
        return connectionProvider
    }

    protected override fun selectConnectionProvider(tenantIdentifier: String?): ConnectionProvider {
        return connectionProvider
    }

    @Throws(SQLException::class)
    override fun getConnection(tenantIdentifier: String?): Connection {
        val connection = super.getConnection(tenantIdentifier)
        connection.createStatement()
            .execute(String.format("SET SCHEMA %s;", tenantIdentifier))
        return connection
    }

    @Throws(IOException::class)
    private fun initConnectionProvider(): ConnectionProvider {
        val properties = Properties()
        properties.load(javaClass.getResourceAsStream("/hibernate-schema-multitenancy.properties"))
        val configProperties: MutableMap<String, Any> = HashMap<String, Any>()
        for (key in properties.stringPropertyNames()) {
            val value = properties.getProperty(key)
            configProperties.put(key, value)
        }

        val connectionProvider = DriverManagerConnectionProviderImpl()
        connectionProvider.configure(configProperties)
        return connectionProvider
    }
}
object ModuleDatabaseManager {
    // Base folder where per-module DBs are stored
    private val baseDir: Path = File(DataFolderName).toPath().apply {
        if (!exists()) Files.createDirectories(this)
    }

    // Caches: both maps are thread-safe
    private val dataSourceCache = ConcurrentHashMap<String, HikariDataSource>()
    private val sessionFactoryCache = ConcurrentHashMap<String, SessionFactory>()

    fun getOrCreateDataSource(moduleName: String): HikariDataSource {
        val key = sanitizeModuleName(moduleName)
        val moduleDir = baseDir.resolve(key)
        Files.createDirectories(moduleDir)
        return dataSourceCache.computeIfAbsent(key) { createDataSourceForModule(key, moduleDir) }
    }

    // Called by modules to get a SessionFactory (creates lazily, thread-safe)
    fun getSessionFactory(
        module: AbstractModule
    ): SessionFactory {
        val key = sanitizeModuleName(module.name)
        // atomic init of SessionFactory
        return sessionFactoryCache.computeIfAbsent(key) {
            // ensure dirs exist — Files.createDirectories is safe to call from multiple threads
            val moduleDir = baseDir.resolve(key)
            Files.createDirectories(moduleDir)

            // create or obtain datasource for this module (atomic)
            val ds = dataSourceCache.computeIfAbsent(key) { createDataSourceForModule(key, moduleDir) }

            // build SessionFactory for this module using the module-specific DataSource
            buildSessionFactoryForModule(module, ds)
        }
    }

    // Close and remove resources for a single module
    fun closeModule(moduleName: String) {
        val key = sanitizeModuleName(moduleName)

        sessionFactoryCache.remove(key)?.let { sf ->
            try { sf.close() } catch (ex: Exception) { ex.printStackTrace() }
        }

        dataSourceCache.remove(key)?.let { ds ->
            try { ds.close() } catch (ex: Exception) { ex.printStackTrace() }
        }
    }

    // Shutdown everything (e.g., on app stop)
    fun shutdownAll() {
        // close all session factories
        sessionFactoryCache.values.forEach { safeCloseSessionFactory(it) }
        sessionFactoryCache.clear()

        // close all Hikari data sources
        dataSourceCache.values.forEach { safeCloseDataSource(it) }
        dataSourceCache.clear()
    }

    // Private helpers

    private fun safeCloseSessionFactory(sf: SessionFactory) {
        try { sf.close() } catch (t: Throwable) { t.printStackTrace() }
    }

    private fun safeCloseDataSource(ds: HikariDataSource) {
        try { ds.close() } catch (t: Throwable) { t.printStackTrace() }
    }

    // Create a HikariDataSource backing a module DB (SQLite example)
    private fun createDataSourceForModule(moduleKey: String, moduleDir: Path): HikariDataSource {
        // Here: use SQLite file per module. You can adapt to MySQL/Postgres per module URL.
        val dbFile = moduleDir.resolve("global.db").toFile()
        // create file if missing (safe even if multiple threads call)
        if (!dbFile.exists()) {
            Files.createDirectories(moduleDir) // redundant-safety
            dbFile.createNewFile()
        }

        val jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"

        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = ""      // SQLite doesn't use
            this.password = ""
            this.maximumPoolSize = 5
            this.minimumIdle = 1
            this.isAutoCommit = false
            this.poolName = "mod-$moduleKey-pool-${UUID.randomUUID()}"
            // recommended for sqlite in-memory or single-writer: set transaction isolation appropriately
            // add additional driver-specific properties as needed
        }

        return HikariDataSource(config)
    }

    // Build a module-scoped SessionFactory (annotated classes optionally added)
    private fun buildSessionFactoryForModule(
        module: Module,
        ds: DataSource
    ): SessionFactory {
        // Hibernate settings - per-module
        val settings = Properties().apply {
            put(Environment.DRIVER, "org.sqlite.JDBC")
            put(Environment.DATASOURCE, ds)
            put(Environment.DIALECT, "org.hibernate.community.dialect.SQLiteDialect") // ensure you have a dialect (or use a custom one)
            put(Environment.SHOW_SQL, "false")
            put(Environment.HBM2DDL_AUTO, "update") // or validate/create — choose your strategy
            // Tune others as needed
        }

        val registryBuilder = StandardServiceRegistryBuilder().applySettings(settings as Map<String, Any>)
        val registry = registryBuilder.build()

        val sources = MetadataSources(registry)
        // register annotated entities:
        val moduleClassLoader = module.javaClass.classLoader

        val original = Thread.currentThread().contextClassLoader
        try {
            Thread.currentThread().contextClassLoader = moduleClassLoader
            for (clazz in module.getModuleAnnotatedClasses()) {
                sources.addAnnotatedClass(clazz)
            }
            val metadata = sources.buildMetadata()
            return metadata.buildSessionFactory()
        } finally {
            Thread.currentThread().contextClassLoader = original
        }

        val metadata = sources.buildMetadata()
        return metadata.buildSessionFactory()
    }

    private fun sanitizeModuleName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_-]"), "_").lowercase()
    }
}

class ModuleTenantResolver : CurrentTenantIdentifierResolver<String?> {
    override fun resolveCurrentTenantIdentifier(): String? {
        return currentTenant.get()
    }

    override fun validateExistingCurrentSessions(): Boolean {
        return true
    }

    companion object {
        private val currentTenant: ThreadLocal<String?> = ThreadLocal.withInitial<String?>(Supplier { "global" })

        fun setTenant(tenantId: String?) {
            currentTenant.set(tenantId)
        }
    }
}