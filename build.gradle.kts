buildscript {
    // Force patched versions of vulnerable transitive dependencies pulled in by
    // AGP / Kotlin / gRPC build toolchain classpath.
    configurations.all {
        resolutionStrategy {
            force("io.netty:netty-common:4.2.10.Final")
            force("io.netty:netty-handler:4.2.10.Final")
            force("io.netty:netty-handler-proxy:4.2.10.Final")
            force("io.netty:netty-codec:4.2.10.Final")
            force("io.netty:netty-codec-http:4.2.10.Final")
            force("io.netty:netty-codec-http2:4.2.10.Final")
            force("io.netty:netty-buffer:4.2.10.Final")
            force("io.netty:netty-transport:4.2.10.Final")
            force("io.netty:netty-resolver:4.2.10.Final")
            force("com.google.protobuf:protobuf-java:4.34.1")
            force("com.google.protobuf:protobuf-kotlin:4.34.1")
            force("com.google.protobuf:protobuf-java-util:4.34.1")
            force("org.apache.commons:commons-compress:1.28.0")
            force("org.apache.commons:commons-lang3:3.18.0")
            force("org.apache.httpcomponents:httpclient:4.5.14")
            force("org.bitbucket.b_c:jose4j:0.9.6")
            force("org.jdom:jdom2:2.0.6.1")
        }
    }
}

plugins {
    id("com.android.application") version "9.1.0" apply false
    id("org.jetbrains.kotlin.android") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
    id("com.google.devtools.ksp") version "2.3.6" apply false
}

dependencyLocking {
    lockAllConfigurations()
}
