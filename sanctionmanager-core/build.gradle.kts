plugins {
    id("java-library")
    id("idea")
    id("maven-publish")
    id("signing")
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
    compileOnly("org.jetbrains:annotations:26.1.0")
    annotationProcessor("org.jetbrains:annotations:26.1.0")
    compileOnly("org.projectlombok:lombok:1.18.48")
    annotationProcessor("org.projectlombok:lombok:1.18.48")
    implementation("redis.clients:jedis:8.0.1")
    implementation("com.zaxxer:HikariCP:4.0.3")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name = "SanctionManager-Core"
                description = "A standalone, platform-independent sanction management system."

                url.set("https://github.com/floatingpointmc/sanctionmanager")
                inceptionYear.set("2026")

                licenses {
                    license {
                        name.set("LGPL-3.0")
                        url.set("https://opensource.org/license/lgpl-3.0")
                    }
                }

                developers {
                    developer {
                        id.set("floatingpointmc")
                        name.set("FloatingPoint-MC")
                        email.set("vlouyearlinjinhua@outlook.com")
                    }
                }

                scm {
                    url.set("https://github.com/floatingpointmc/fpt")
                    connection.set(
                        "scm:git:git://github.com/floatingpointmc/sanctionmanager.git"
                    )
                    developerConnection.set(
                        "scm:git:ssh://github.com/floatingpointmc/sanctionmanager.git"
                    )
                }
            }
        }

        repositories {
            maven {
                name = "central"

                url = uri(
                    "https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/"
                )

                credentials {
                    username = findProperty("ossrhUsername") as? String
                    password = findProperty("ossrhPassword") as? String
                }
            }
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["mavenJava"])
}

tasks.test {
    useJUnitPlatform()
}