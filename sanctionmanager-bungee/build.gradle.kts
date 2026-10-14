plugins {
    id("java")
    id("idea")
    id("com.gradleup.shadow") version "9.6.1"
}

group = "io.github.floatingpointmc"
version = "1.0-SNAPSHOT"


java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://hub.spigotmc.org/nexus/repository/public/") }
    maven { url = uri("https://repo.glaremasters.me/repository/public/") }
}

dependencies {
    implementation(project(":sanctionmanager-minecraft"))
    compileOnly("net.md-5:bungeecord-api:26.1-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:26.1.0")
    annotationProcessor("org.jetbrains:annotations:26.1.0")
    compileOnly("org.projectlombok:lombok:1.18.48")
    annotationProcessor("org.projectlombok:lombok:1.18.48")
    implementation("org.bstats:bstats-bungeecord:3.2.1")
    implementation("org.incendo:cloud-bungee:2.0.0-beta.10")
}

tasks.shadowJar {
    archiveClassifier.set("")
    filesMatching("META-INF/services/**") { duplicatesStrategy = DuplicatesStrategy.INCLUDE }
    mergeServiceFiles()
    relocate("redis.clients.jedis", "io.github.floatingpointmc.sanctionmanager.libs.jedis")
    relocate("com.zaxxer.hikari", "io.github.floatingpointmc.sanctionmanager.libs.hikari")
    relocate("org.bstats", "io.github.floatingpointmc.sanctionmanager.libs.bstats")
    minimize {
        // Native registry implementations and JDBC drivers are loaded reflectively.
        exclude(dependency("io.github.vlouboos:standaloneevent-common:.*"))
        exclude(dependency("com.h2database:h2:.*"))
    }
}

tasks.test {
    useJUnitPlatform()
}