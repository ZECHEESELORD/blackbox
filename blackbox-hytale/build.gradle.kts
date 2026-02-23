import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.the

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(files(rootProject.file("lib/HytaleServer.jar")))
    implementation(project(":blackbox-core"))
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly(files(rootProject.file("lib/HytaleServer.jar")))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(project(":blackbox-core").the<JavaPluginExtension>().sourceSets.getByName("main").output)
}
