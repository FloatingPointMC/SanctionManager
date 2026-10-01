plugins {
    id("java-library")
    id("idea")
}

group = "io.github.floatingpointmc"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":sanctionmanager-api"))
    implementation(project(":sanctionmanager-core"))
    implementation("org.incendo:cloud-core:2.0.0")
    implementation("com.zaxxer:HikariCP:4.0.3")
    implementation("redis.clients:jedis:4.3.1")
    compileOnly("org.jetbrains:annotations:26.1.0")
    annotationProcessor("org.jetbrains:annotations:26.1.0")
    compileOnly("org.projectlombok:lombok:1.18.48")
    annotationProcessor("org.projectlombok:lombok:1.18.48")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}