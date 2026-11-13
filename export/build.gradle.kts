plugins { id("org.jetbrains.kotlin.jvm") }
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":parser"))
    api(project(":renderer"))
    api(project(":editor"))
    testImplementation(kotlin("test"))
}
tasks.test { useJUnit() }
