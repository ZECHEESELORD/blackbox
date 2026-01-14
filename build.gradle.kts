import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import sh.harold.blackbox.gradle.RunHytaleExtension

plugins {
    base
    id("sh.harold.blackbox.run-hytale")
}

allprojects {
    group = "sh.harold"
    version = "0.1"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

extensions.configure<RunHytaleExtension>("runHytale") {
    jarUrl.set(
        providers.gradleProperty("hytale.jarUrl")
            .orElse(providers.environmentVariable("HYTALE_JAR_URL"))
            .orElse(rootProject.file("lib/HytaleServer.jar").toURI().toString())
    )

    assetsPath.set(
        providers.gradleProperty("hytale.assetsPath")
            .orElse(providers.environmentVariable("HYTALE_ASSETS_PATH"))
            .orElse(rootProject.file("lib/Assets.zip").path)
    )
}
