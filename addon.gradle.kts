// Extra build configuration for the GTNH/RFG build.
//
// This file adds the test infrastructure:
//
//  - JaCoCo coverage for the JUnit suite (HTML/XML report + a regression floor)
//  - a separate `gameTest` source set containing a never-shipped @Mod that boots the real
//    client/server and asserts the mod actually works there
//  - `gameTestClient` / `gameTestServer` tasks that put that mod on the run classpath,
//    set a marker system property, and start the modern (Java 17+) run task
//
// The structure mirrors MrFuzzihead/PeripheralsPlusPlus `addon.gradle.kts`, adapted for this
// mod: it is client-first, uses jvmDowngrader (not Jabel), and runs the tests on the Java 25
// toolchain because the production classes are Java 17+.

import java.math.BigDecimal

import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

// ---------------------------------------------------------------------------------------------------------
// Unit tests + coverage
// ---------------------------------------------------------------------------------------------------------

apply(plugin = "jacoco")

project.extensions.configure(JacocoPluginExtension::class.java) {
    toolVersion = "0.8.16"
}

tasks.withType<Test>().configureEach {
    extensions.configure(JacocoTaskExtension::class.java) {
        isEnabled = true
    }
    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}

// Tests need the compileOnly dependencies too: several production classes extend or reference
// RFG-provided/Minecraft types that only exist on the compile classpath. Without this, a test that
// merely names such a class fails to load.
val mainSourceSet = project.extensions.getByType(SourceSetContainer::class.java).getByName("main")
configurations.named("testCompileClasspath") {
    extendsFrom(configurations.named("compileOnly").get())
}
configurations.named("testRuntimeClasspath") {
    extendsFrom(configurations.named("compileOnly").get())
}

// Only the unit test run feeds the coverage report. In a Forge 1.7.10 *dev* launch the
// LaunchClassLoader rewrites every class through ASM, so the class ids in a gameTest.exec file do
// not match the class files on disk; merging it would only produce "Classes in bundle do not match
// with execution data" warnings. The game-test runs are still useful for behaviour, just not for a
// trustworthy coverage number.
val jacocoExecFiles = project.files(project.layout.buildDirectory.file("jacoco/test.exec"))

// The GTNH convention runs the tests against the *downgraded* main classes (see
// JVMDowngraderModule.configureTestTask), so the coverage report must analyse those bytes too.
// Pointing JaCoCo at mainSourceSet.output.classesDirs produces a "Classes in bundle do not match
// with execution data" warning for every class and reports zero coverage.
val jacocoMainClasses = project.files(
    project.provider {
        // Resolved lazily: the GTNH convention registers `downgradeMainClasses` after addon scripts
        // are evaluated, so a configuration-time task lookup would miss it.
        val downgrade = project.tasks.findByName("downgradeMainClasses")
        if (downgrade != null) downgrade.outputs.files else mainSourceSet.output.classesDirs
    }
)

fun jacocoClassDirs() = project.files(jacocoMainClasses).asFileTree.matching {
    // The generated version holder, the self-test harness itself, and mixin plumbing are not
    // meaningfully unit-testable headless; measuring them would only dilute the number.
    exclude(
        "com/mrfuzzihead/fuzzicontrols/Tags.class",
        "com/mrfuzzihead/fuzzicontrols/selftest/**",
        "com/mrfuzzihead/fuzzicontrols/mixins/**",
        // Multi-release variants duplicate com/mrfuzzihead/... classes; JaCoCo rejects a bundle
        // that contains two classes with the same name. The root (Java 17) variant is the one the
        // directory-classpath test JVM loads.
        "META-INF/**"
    )
}

tasks.named<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "Generates HTML and XML JaCoCo coverage reports from the unit test run"
    dependsOn(tasks.named("test"))

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    classDirectories.setFrom(jacocoClassDirs())
    sourceDirectories.setFrom(mainSourceSet.allJava.srcDirs)
    executionData.setFrom(jacocoExecFiles)
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    group = "verification"
    description = "Fails the build when unit test coverage drops below the configured floor"
    dependsOn(tasks.named("test"))

    classDirectories.setFrom(jacocoClassDirs())
    sourceDirectories.setFrom(mainSourceSet.allJava.srcDirs)
    executionData.setFrom(jacocoExecFiles)

    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                // Regression guard at the current baseline, not a target. Raise it as the
                // ControllerTickHandler/driver tests land; see docs/REFACTOR_PLAN.md.
                minimum = BigDecimal("0.45")
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = BigDecimal("0.45")
            }
        }
    }
}

// `check` runs the tests and enforces the floor, so CI catches coverage regressions.
tasks.named("check").configure {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}

// ---------------------------------------------------------------------------------------------------------
// In-game test mod (never shipped)
// ---------------------------------------------------------------------------------------------------------

val gameTestSourceSet = project.extensions.getByType(SourceSetContainer::class.java).create("gameTest")

configurations.getByName(gameTestSourceSet.compileClasspathConfigurationName)
    .extendsFrom(configurations.getByName("compileClasspath"))
configurations.getByName(gameTestSourceSet.runtimeClasspathConfigurationName)
    .extendsFrom(configurations.getByName("runtimeClasspath"))
gameTestSourceSet.compileClasspath += mainSourceSet.output
gameTestSourceSet.compileClasspath += mainSourceSet.compileClasspath
gameTestSourceSet.runtimeClasspath += mainSourceSet.output
gameTestSourceSet.runtimeClasspath += mainSourceSet.runtimeClasspath

// `gameTestServer` and `gameTestClient` are aliases: they start the matching modern run task
// with the game-test mod on the classpath. The mod exits the JVM itself when its checks finish,
// so the task terminates with the assertion result.
val isGameTestRun = project.hasProperty("gameTest") ||
    gradle.startParameter.taskNames.any { it.contains("gameTest") }

if (isGameTestRun) {
    tasks.withType<JavaExec>().configureEach {
        if (name.startsWith("runClient") || name.startsWith("runServer")) {
            classpath += gameTestSourceSet.output
            systemProperty("fuzzicontrols.gameTest", "true")
            // A reused dev world may reference blocks from mods that are no longer installed;
            // the game test is not about that, so answer FML's "continue anyway?" prompt up front.
            systemProperty("fml.queryResult", "confirm")
        }
    }
}

tasks.named("check").configure {
    dependsOn(gameTestSourceSet.classesTaskName)
}

tasks.register("gameTestServer") {
    group = "verification"
    description = "Boots a dedicated server, runs the in-game game test mod, and exits with its result"
    if (tasks.names.contains("runServer25")) dependsOn(tasks.named("runServer25"))
}

tasks.register("gameTestClient") {
    group = "verification"
    description = "Boots a client, runs the in-game game test mod, and exits with its result"
    if (tasks.names.contains("runClient25")) dependsOn(tasks.named("runClient25"))
}
