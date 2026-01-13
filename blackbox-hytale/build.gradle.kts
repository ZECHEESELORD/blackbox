dependencies {
    implementation(project(":blackbox-core"))
    compileOnly(files(rootProject.file("lib/HytaleServer.jar")))
}
