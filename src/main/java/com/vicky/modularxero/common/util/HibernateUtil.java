package com.vicky.modularxero.common.util;

import com.vicky.modularxero.AbstractServerModule;
import com.vicky.modularxero.ModularXeroDispatcher;
import com.vicky.modularxero.ModuleClassLoader;
import com.vicky.modularxero.db.ModuleDataSourceMultiTenantProvider;
import com.vicky.modularxero.db.ModuleTenantResolver;
import com.vicky.modularxero.db.SchemaMultiTenantConnectionProvider;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.engine.jdbc.connections.spi.AbstractMultiTenantConnectionProvider;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.hibernate.service.ServiceRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class HibernateUtil {
    private static final Set<Class<?>> registeredEntities = new HashSet<>();
    private static SessionFactory sessionFactory;
    private static final MultiTenantConnectionProvider<String> provider =
            new ModuleDataSourceMultiTenantProvider();

    public static void registerEntity(Class<?> entityClass) {
        if (sessionFactory != null) {
            throw new IllegalStateException("Cannot register new entity after SessionFactory has been initialized.");
        }
        registeredEntities.add(entityClass);
    }

    public static synchronized SessionFactory getSessionFactory() {
        Class<?> callerClass = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        if (callerClass.getClassLoader() instanceof ModuleClassLoader) {
            try {
                throw new IllegalAccessException("Submodules cannot use global session factory.");
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        if (sessionFactory == null) {
            sessionFactory = buildSessionFactory();
        }
        return sessionFactory;
    }

    private static SessionFactory buildSessionFactory() {
        try {
            Configuration configuration = new Configuration()
                    .setProperty(AvailableSettings.DRIVER, "org.sqlite.JDBC")
                    .setProperty(AvailableSettings.URL, "jdbc:sqlite:database.db")
                    .setProperty(AvailableSettings.USER, "dbuser")
                    .setProperty(AvailableSettings.PASS, "dbpassword")
                    .setProperty(AvailableSettings.DIALECT, "org.hibernate.community.dialect.SQLiteDialect")
                    .setProperty(AvailableSettings.SHOW_SQL, "false")
                    .setProperty(AvailableSettings.FORMAT_SQL, "false")
                    .setProperty(AvailableSettings.HBM2DDL_AUTO, "update")
                    .setProperty("hibernate.multiTenancy", "SCHEMA");
            configuration.getProperties().put(
                    AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER,
                    provider
            );
            configuration.getProperties().put(
                    AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER,
                    new ModuleTenantResolver()
            );

            for (Class<?> entity : registeredEntities) {
                configuration.addAnnotatedClass(entity);
            }

            ServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
                    .applySettings(configuration.getProperties())
                    .build();

            return configuration.buildSessionFactory(serviceRegistry);
        } catch (Throwable ex) {
            System.err.println("Initial SessionFactory creation failed: " + ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

    public static void shutdown() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }
}
