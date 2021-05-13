/*
 * Copyright (c) 2020 linqu.tech, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
plugins {
    `java-library`
    `maven-publish`
    signing
}

val lombokVersion: String by rootProject.extra
val springVersion by extra { "5.3.6" }

dependencies {
    compileOnly("javax.servlet:javax.servlet-api:4.0.1")
    compileOnly("org.springframework:spring-messaging:${springVersion}")
    compileOnly("org.springframework:spring-webflux:${springVersion}")
    compileOnly("org.springframework:spring-webmvc:${springVersion}")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.12.3")
    implementation("org.eclipse.jdt:core:3.3.0-v_771")
    implementation(project(":libs:commons"))
    compileOnly(files(org.gradle.internal.jvm.Jvm.current().toolsJar))
}

tasks.jar {
    enabled = true
}

java {
    withJavadocJar()
    withSourcesJar()
}

val filename = "webpb-runtime"

publishing {
    publications {
        create<MavenPublication>("webpbRuntime") {
            artifactId = filename
            artifact(tasks.jar.get())
            versionMapping {
                usage("java-api") {
                    fromResolutionOf("runtimeClasspath")
                }
                usage("java-runtime") {
                    fromResolutionResult()
                }
            }
        }
    }
    repositories {
        maven {
            name = "oss"
            val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            credentials(PasswordCredentials::class)
        }
    }
}

val updatePublishing: (publishing: PublishingExtension, publication: String, filename: String, desc: String) -> Void by rootProject.extra
updatePublishing(publishing, "webpbRuntime", filename, "The webpb runtime library for java")

signing {
    sign(publishing.publications["webpbRuntime"])
}

tasks.javadoc {
    if (JavaVersion.current().isJava9Compatible) {
        (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }
    exclude("tech/linqu/webpb/runtime/mvc/WebpbRequestMappingProcessor.java")
    exclude("tech/linqu/webpb/runtime/utils/**")
}