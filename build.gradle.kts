// SPDX-License-Identifier: GPL-3.0-or-later
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
    alias(libs.plugins.shadow)
    alias(libs.plugins.spotless)
    alias(libs.plugins.spotless.changelog)
    alias(libs.plugins.tapmoc)
    application
}

// The changelog is the single source of truth of the version: the plugin
// reads it and derives the version of the release it describes. A release
// is declared by promoting the `[Unreleased]` section to a version heading;
// the build reads the heading, and the tag names it.
//
// The configuration of the extension has to come before its first use, because the
// plugin computes the version lazily and refuses to be reconfigured
// afterwards, so the block sits here, right after the plugins.
spotlessChangelog {
    changelogFile("CHANGELOG.md")
    // Every build but the release one carries the `-SNAPSHOT` suffix: the
    // release build says so with `-Prelease=true`.
    setAppendDashSnapshotUnless_dashPrelease(true)
    // The major bump recognizes the documented phrase and the bold marker
    // of the current changelog convention; the match is a plain, case
    // sensitive contains, so the spellings cover the phrase in prose, at
    // sentence start, and the marker with or without its colon, since the
    // marker alone would miss `**BREAKING:**`, which is the spelling the
    // changelog itself uses.
    ifFoundBumpBreaking(
        "breaking change",
        "Breaking change",
        "**Breaking",
        "**BREAKING",
    )
    tagPrefix("v")
    commitMessage("Release v{{version}}")
    remote("origin")
    branch("main")
}

group = "br.usp.poli.lta.cereda"
version = spotlessChangelog.versionNext

// Tapmoc owns the targets: the JVM target and the Kotlin language level are
// stated here, and nowhere else, because a build that states them twice states them
// twice. There is no JVM toolchain: the JDK that runs the build is the one
// that compiles for it.
//
// The Java target is 11: the interpreter uses no API newer than that, and the
// fat jar therefore runs on a Java 11 runtime. The JDK that *runs the build*
// is a different matter, since Gradle 9 requires Java 17 or later.
tapmoc {
    java(11)
    kotlin("2.4.0")
    checkDependencies()
}

repositories {
    mavenCentral()
}

spotless {
    // The steps run in the order they are declared, and the order matters.
    //
    // The trailing newlines are normalised *before* ktlint runs: a file that
    // ends with an extra blank line makes a ktlint rule fail with an
    // exception rather than with a message, and the step that fixes the file
    // is the one that has to run first.
    kotlinGradle {
        with(LicenseDescriptor) {
            licenseHeader(COMMENT, DELIMITER)
        }
        target("build.gradle.kts", "settings.gradle.kts")
        trimTrailingWhitespace()
        leadingTabsToSpaces()
        endWithNewline()
        replaceRegex(TrailingNewlines.STEP, TrailingNewlines.BLANK_LINES, TrailingNewlines.SINGLE)
        ktlint()
    }
    kotlin {
        target("src/**/*.kt")
        licenseHeader(LicenseDescriptor.COMMENT)
        trimTrailingWhitespace()
        leadingTabsToSpaces()
        endWithNewline()
        replaceRegex(TrailingNewlines.STEP, TrailingNewlines.BLANK_LINES, TrailingNewlines.SINGLE)
        ktlint()
            .editorConfigOverride(
                mapOf(
                    "max_line_length" to 120,
                ),
            )
    }
}

detekt {
    config.from(files("detekt-config.yml"))
    buildUponDefaultConfig = false
}

kover {
    reports {
        verify {
            rule {
                // Line coverage floor (LINE is the default coverage unit).
                // The coverage sweep of oc24 raised the coverage of the suite
                // to 99.5% of the lines; the floor sits just below it, so that
                // a regression fails the build. The lines that are still
                // uncovered are the two entry points, which only forward the
                // exit status to the process, and a defensive branch of the
                // interpreter that the clause machinery cannot reach.
                minBound(99)
            }
            rule {
                // Branch coverage floor: the same sweep raised the coverage of
                // the branches to 94.1%. The floor leaves room for the
                // defensive branches the suite cannot reach, and still fails
                // the build when a branch loses its test.
                bound {
                    coverageUnits.set(CoverageUnit.BRANCH)
                    minValue = 92
                }
            }
        }
    }
}

dokka {
    moduleName.set(project.name)

    dokkaPublications.html {
        suppressInheritedMembers.set(true)
        suppressObviousFunctions.set(true)
        failOnWarning.set(true)
    }

    dokkaSourceSets.all {
        jdkVersion.set(11)
        moduleName.set(project.name)
        skipDeprecated.set(false)
        reportUndocumented.set(true)
        skipEmptyPackages.set(true)

        enableKotlinStdLibDocumentationLink.set(true)
        enableJdkDocumentationLink.set(true)
    }
}

application {
    mainClass = "br.usp.poli.lta.cereda.quiver.QuiverKt"
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks {
    withType<Test>().configureEach {
        useJUnitPlatform()
    }

    named("check") {
        dependsOn("detekt")
        dependsOn("koverVerify")
        dependsOn("dokkaGenerateHtml")
    }

    // The interactive REPL reads stdin: Gradle's JavaExec does not forward
    // the console by default, so the run wires it explicitly.
    register<org.gradle.api.tasks.JavaExec>("runRepl") {
        group = "application"
        description = "Runs the interactive Quiver REPL."
        mainClass = "br.usp.poli.lta.cereda.quiver.QuiverReplKt"
        classpath = sourceSets["main"].runtimeClasspath
        standardInput = System.`in`
    }

    build {
        dependsOn("shadowJar")
    }

    // The shadow jar replaces the thin jar on the same output path: every
    // task that walks that path names an explicit dependency, so the order
    // stays well-defined (the scripts only reference the file name, but the
    // validation requires the declaration).
    named("startScripts") { dependsOn("shadowJar") }
    named("startShadowScripts") { dependsOn("jar") }
    named("distZip") { dependsOn("shadowJar") }
    named("distTar") { dependsOn("shadowJar") }
    named("shadowDistZip") { dependsOn("jar") }
    named("shadowDistTar") { dependsOn("jar") }
    named("installDist") { dependsOn("shadowJar") }
    named("installShadowDist") { dependsOn("jar") }

    // The fat JAR: bundles the interpreter and its runtime into a single
    // executable. Run it with `java -jar quiver.jar -i file.quiver input` to
    // recognize an input, or with `java -jar quiver.jar` to start the
    // interactive REPL.
    named<ShadowJar>("shadowJar").configure {
        archiveClassifier.set("") // replaces the thin jar in build/libs
        manifest {
            attributes("Main-Class" to "br.usp.poli.lta.cereda.quiver.QuiverKt")
        }
        // Kotlin module files need the WARN duplicate strategy, since the default
        // silently drops same-named entries before the merger sees them.
        filesMatching("META-INF/*.kotlin_module") {
            duplicatesStrategy = DuplicatesStrategy.WARN
        }
    }
}

private object LicenseDescriptor {
    const val COMMENT = "// SPDX-License-Identifier: GPL-3.0-or-later"
    const val DELIMITER = "(package|import|open|module|plugins|pluginManagement) "
}

/** The text the trailing blank lines of a file are normalised to. */
private object TrailingNewlines {
    /** The name the step reports itself under. */
    const val STEP = "trailing-blank-lines"

    /** The blank lines at the end of a file, which ktlint cannot digest. */
    const val BLANK_LINES = """\n+\z"""

    /** The single newline every file ends with. */
    const val SINGLE = "\n"
}
