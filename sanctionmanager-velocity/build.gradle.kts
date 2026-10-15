plugins {
    id("java")
    id("com.gradleup.shadow") version "9.6.1"
}

group = "io.github.floatingpointmc"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
}

dependencies {
    implementation(project(":sanctionmanager-minecraft"))
    compileOnly("com.velocitypowered:velocity-api:4.2.0")
    compileOnly("org.jetbrains:annotations:26.1.0")
    annotationProcessor("org.jetbrains:annotations:26.1.0")
    compileOnly("org.projectlombok:lombok:1.18.48")
    annotationProcessor("org.projectlombok:lombok:1.18.48")
    implementation("org.bstats:bstats-velocity:3.2.1")
    implementation("org.incendo:cloud-velocity:2.0.0-beta.10")
    implementation("org.yaml:snakeyaml:2.4")
}

tasks.processResources {
    filesMatching("velocity-plugin.json") {
        expand("projectVersion" to project.version)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    filesMatching("META-INF/services/**") { duplicatesStrategy = DuplicatesStrategy.INCLUDE }
    mergeServiceFiles()
    relocate("redis.clients.jedis", "io.github.floatingpointmc.sanctionmanager.libs.jedis")
    relocate("com.zaxxer.hikari", "io.github.floatingpointmc.sanctionmanager.libs.hikari")
    relocate("org.bstats", "io.github.floatingpointmc.sanctionmanager.libs.bstats")
    relocate("org.yaml.snakeyaml", "io.github.floatingpointmc.sanctionmanager.libs.snakeyaml")
    minimize {
        // Native registry implementations and JDBC drivers are loaded reflectively.
        exclude(dependency("io.github.vlouboos:standaloneevent-common:.*"))
        exclude(dependency("com.h2database:h2:.*"))
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.test {
    useJUnitPlatform()
}