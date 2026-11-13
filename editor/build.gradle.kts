plugins { id("org.jetbrains.kotlin.jvm") }
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":renderer"))
    testImplementation(kotlin("test"))
}
tasks.test { useJUnit() }
