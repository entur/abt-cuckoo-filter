plugins {
    kotlin("jvm") version "1.9.23"
    id("java-library")
    id("maven-publish")
    id("org.jreleaser") version "1.18.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.guava:guava:33.5.0-jre")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}

val sourcesJar by tasks.registering(Jar::class) {
    dependsOn(tasks.classes)
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

val javadocJar by tasks.registering(Jar::class) {
    dependsOn(tasks.javadoc)
    archiveClassifier.set("javadoc")
    from(tasks.javadoc.get().destinationDir)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(sourcesJar)
            artifact(javadocJar)

            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

            pom {
                name.set(project.name)
                description.set("A space-efficient probabilistic data structure for approximate set membership queries in Kotlin")
                packaging = "jar"

                url.set("https://github.com/entur/${project.name}")

                scm {
                    connection.set("scm:git:https://github.com/entur/${project.name}.git")
                    developerConnection.set("scm:git:https://github.com/entur/${project.name}.git")
                    url.set("https://github.com/entur/${project.name}")
                }

                licenses {
                    license {
                        name.set("European Union Public Licence v. 1.2")
                        url.set("https://www.eupl.eu/")
                    }
                }

                developers {
                    developer {
                        id.set("markushauge")
                        name.set("Markus Hauge")
                        email.set("markus.hauge@entur.org")
                    }
                }
            }
        }
    }

    repositories {
        maven {
            url = uri(layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

jreleaser {
    signing {
        active.set(org.jreleaser.model.Active.ALWAYS)
        armored.set(true)
    }
    deploy {
        maven {
            mavenCentral {
                create("release") {
                    active.set(org.jreleaser.model.Active.RELEASE)
                    url.set("https://central.sonatype.com/api/v1/publisher")
                    stagingRepository(
                        layout.buildDirectory
                            .dir("staging-deploy")
                            .get()
                            .asFile.path,
                    )
                }
            }
            nexus2 {
                create("snapshot") {
                    active.set(org.jreleaser.model.Active.SNAPSHOT)
                    applyMavenCentralRules.set(true)
                    snapshotSupported.set(true)
                    closeRepository.set(true)
                    releaseRepository.set(true)
                    url.set("https://ossrh-staging-api.central.sonatype.com/service/local")
                    snapshotUrl.set("https://central.sonatype.com/repository/maven-snapshots/")
                    stagingRepository(
                        layout.buildDirectory
                            .dir("staging-deploy")
                            .get()
                            .asFile.path,
                    )
                }
            }
        }
    }
    release {
        github {
            skipTag.set(false)
            skipRelease.set(true)
        }
    }
}
