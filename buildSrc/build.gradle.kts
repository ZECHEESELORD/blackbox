plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("runHytalePlugin") {
            id = "sh.harold.blackbox.run-hytale"
            implementationClass = "sh.harold.blackbox.gradle.RunHytalePlugin"
        }
    }
}

