import utils.Vers
import utils.signAndPublish

plugins {
    id("java.library")
}

dependencies {
    api(project(":libs:commons"))
    compileOnly("javax.servlet:javax.servlet-api:${Vers.servletApi}")
    compileOnly("org.springframework:spring-messaging:${Vers.springFramework}")
    compileOnly("org.springframework:spring-webflux:${Vers.springFramework}")
    compileOnly("org.springframework:spring-webmvc:${Vers.springFramework}")
    compileOnly(files(org.gradle.internal.jvm.Jvm.current().toolsJar))
    implementation("com.fasterxml.jackson.core:jackson-databind:${Vers.jackson}")
    testImplementation("org.springframework:spring-webflux:${Vers.springFramework}")
}

signAndPublish("webpb-runtime") {
    from(components["java"])
    pom {
        description.set("The webpb runtime library for JAVA")
    }
}

tasks.javadoc {
    enabled = false
}
