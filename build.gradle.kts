import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.1.10"
    `java-library`
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "8.1.1"
}
kotlin {
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
    testImplementation (kotlin("test"))
    implementation     (kotlin("stdlib-jdk8"))
    implementation     (kotlin("reflect"))
    implementation     ("org.reflections:reflections:0.10.2")
    implementation     ("org.jline:jline:3.25.1")
    implementation     ("com.fasterxml.jackson.core:jackson-databind:2.19.0")
    implementation     ("com.fasterxml.jackson.core:jackson-core:2.18.3")
    implementation     ("org.hibernate.orm:hibernate-core:6.6.15.Final")
    implementation     ("org.hibernate.orm:hibernate-community-dialects:6.6.15.Final")
    implementation     ("com.h2database:h2:2.1.214")
    runtimeOnly        ("org.glassfish.jaxb:jaxb-runtime:4.0.2")
    implementation     ("org.xerial:sqlite-jdbc:3.46.0.0")
    implementation     ("org.java-websocket:Java-WebSocket:1.5.2")
    implementation     ("info.picocli:picocli:4.7.6")
    annotationProcessor("info.picocli:picocli-codegen:4.7.6")
    implementation     ("info.picocli:picocli:4.7.1")
    implementation     ("info.picocli:picocli-shell-jline3:4.7.1")
    implementation     ("org.mindrot:jbcrypt:0.4")
    implementation     ("org.slf4j:slf4j-api:2.0.15")
    implementation     ("ch.qos.logback:logback-classic:1.5.13")
    implementation     ("org.ow2.asm:asm:9.7.1")
    implementation     ("org.ow2.asm:asm-commons:9.7.1")
    implementation     ("org.openjdk.jol:jol-core:0.17")
    implementation     ("com.zaxxer:HikariCP:5.0.1")
    implementation     ("com.google.code.gson:gson:2.11.0")
}

tasks.withType<JavaExec> {
    jvmArgs("-javaagent:libs/jol-core-0.17.jar")
}
tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
publishing {
    publications {
        create<MavenPublication>("shadow") {
            groupId = "com.vicky"
            artifactId = "modularxero"
            version = "1.0.0-VANGUARD"

            // Publish the output of the ShadowJar task
            artifact(tasks.shadowJar.get()) {
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
        "-Djdk.attach.allowAttachSelf=true"
    )

    standardInput = System.`in` // so JLine works for your console
}
tasks.named<ShadowJar>("shadowJar") {
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