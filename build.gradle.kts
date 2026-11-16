import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.kotlinJvm)
    `java-library`
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "8.1.1"

    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

group = "org.vicky.hackaton.lmao"
version = "1.0-VANGUARD"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation(project(":commons"))
    implementation("org.reflections:reflections:0.10.2")

    implementation("org.jline:jline:4.4.1:jdk11")
    implementation("org.jline:jline-terminal-jni:4.4.1")
    implementation("info.picocli:picocli:4.7.6")
    annotationProcessor("info.picocli:picocli-codegen:4.7.6")
    implementation("info.picocli:picocli-shell-jline3:4.7.1")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.0")
    implementation("com.fasterxml.jackson.core:jackson-core:2.18.3")
    implementation("org.hibernate.orm:hibernate-core:6.6.15.Final")
    implementation("org.hibernate.orm:hibernate-community-dialects:6.6.15.Final")
    implementation("com.h2database:h2:2.1.214")
    runtimeOnly("org.glassfish.jaxb:jaxb-runtime:4.0.2")
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    implementation("org.java-websocket:Java-WebSocket:1.5.2")
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("org.slf4j:slf4j-api:2.0.15")
    implementation("ch.qos.logback:logback-classic:1.5.13")
    implementation("org.ow2.asm:asm:9.7.1")
    implementation("org.ow2.asm:asm-commons:9.7.1")
    implementation("org.openjdk.jol:jol-core:0.17")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation(libs.kotlinx.coroutinesSwing)
    implementation("com.sun.mail:jakarta.mail:2.0.2")
    implementation("com.sun.activation:jakarta.activation:2.0.1")
}

tasks.withType<JavaExec> {
    // jvmArgs("-javaagent:libs/jol-core-0.17.jar")
}
tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("shadow") {
            groupId = "com.vicky"
            artifactId = "modularxero"
            version = "1.0.0-VANGUARD"

            // Publish the output of the ShadowJar task
            artifact(tasks.jar.get()) {
                classifier = null // no "-all" suffix
            }

            pom {
                name.set("ModularXero")
                description.set("A modular Kotlin server framework by VickyE.")
                url.set("https://github.com/vickye/modularxero") // optional
            }
        }
    }

    repositories {
        mavenLocal()
    }
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "com.vicky.modularxero.Startup" // 🚀 entry point
        )
    }
}
tasks.register<Copy>("exportRuntimeLibs") {
    group = "distribution"
    description = "Copies all runtime libraries to the libs/ folder"

    // Create the folder if it doesn't exist
    val outputDir = layout.projectDirectory.dir("libs")
    into(outputDir)

    // Copy all jars from runtime classpath
    from(configurations.runtimeClasspath)

    // Only JARs
    include("**/*.jar")

    doLast {
        println("✅ Runtime libraries copied to: ${outputDir.asFile.absolutePath}")
    }
}
tasks.register("cp") {
    doLast {
        val combinedClasspath = sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
        println(combinedClasspath.asPath)
    }
}
tasks.register<JavaExec>("runModularXero") {
    group = "application"
    description = "Run ModularXero server with agent and metrics tracking"

    mainClass.set("com.vicky.modularxero.Startup") // adjust this to your actual entrypoint
    classpath = sourceSets["main"].runtimeClasspath

    // Optional: pass arguments to the program
    args = listOf()

    // Optional: JVM args (for memory tracking or agents)
    jvmArgs = listOf(
        "-Xmx1G", // max memory
        "-Djdk.attach.allowAttachSelf=true",
        // Forces JLine to look for the JNI native provider instead of skipping it
        "-Dorg.jline.terminal.provider=jni"
    )

    standardInput = System.`in` // so JLine works for your console
}
tasks.named<ShadowJar>("shadowJar") {
    mergeServiceFiles()
    archiveClassifier.set("") // replaces normal JAR output
    val runtimeJars = sourceSets.main.get().compileClasspath.filter { it.name.endsWith(".jar") }
    from(runtimeJars) {
        into("libs")
    }

    manifest {
        attributes(
            "Main-Class" to "com.vicky.modularxero.Startup" // 🚀 entry point
        )
    }
}