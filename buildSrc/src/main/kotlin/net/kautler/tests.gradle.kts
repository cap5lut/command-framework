/*
 * Copyright 2019 Bjoern Kautler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.kautler

import net.kautler.Property.Companion.boolean
import net.kautler.Property.Companion.double
import net.kautler.Property.Companion.optionalString
import org.pitest.mutationtest.engine.gregor.config.Mutator
import java.util.concurrent.TimeUnit.SECONDS

plugins {
    idea
    id("info.solidsoft.pitest")
    groovy
    jacoco
}

val versions: Map<String, String> by project
val messageFrameworkVersions: Map<String, List<String>> by project

fun sanitizeVersion(version: String) = version.replace("^|[.-]|$".toRegex(), "_")

fun SourceSetContainer.createForTest(name: String, configuration: SourceSet.() -> Unit = { }) {
    create(name) {
        idea {
            module {
                // allGroovy would be better, but does not work somehow
                // but due to Groovy joint compilation, this works too
                testSourceDirs = testSourceDirs + allJava.sourceDirectories.files
                @Suppress("UnstableApiUsage")
                testResourceDirs = testResourceDirs + resources.sourceDirectories.files
            }
        }
        configuration.invoke(this)
    }
}

val integTestSourceSets = messageFrameworkVersions
        .mapValues { (_, versions) ->
            listOf("") + versions
                    .drop(1)
                    .map(::sanitizeVersion)
        }
        .flatMap { (messageFramework, versions) ->
            versions.map {
                "$messageFramework${it}IntegTest" to "${messageFramework}IntegTest"
            }
        }
        .toTypedArray()
        .let { mapOf(*it) }

sourceSets {
    createForTest("pitest")
    createForTest("spock")
    // work-around for https://youtrack.jetbrains.com/issue/IDEA-229618
    create("integTestCommon")
}

integTestSourceSets.keys.forEach {
    sourceSets.createForTest(it) {
        @Suppress("UnstableApiUsage")
        sourceSets.main.get().output.also {
            compileClasspath += it
            runtimeClasspath += it
        }
    }
}

dependencies {
    testImplementation("org.spockframework:spock-core:${versions["spock"]}")
    testImplementation("org.powermock:powermock-reflect:${versions["powermock"]}")
    testImplementation("org.jboss.weld:weld-junit4:${versions["weld-junit"]}")
    testImplementation("org.apache.logging.log4j:log4j-core:${versions["log4j"]}:tests")
    testImplementation("org.apache.logging.log4j:log4j-core:${versions["log4j"]}")
    testImplementation("org.antlr:antlr4-runtime:${versions["antlr"]}")
    testImplementation("org.javacord:javacord-api:${messageFrameworkVersions.safeGet("javacord").first()}")
    testImplementation("org.javacord:javacord-core:${messageFrameworkVersions.safeGet("javacord").first()}")
    testImplementation("net.dv8tion:JDA:${messageFrameworkVersions.safeGet("jda").first()}") {
        exclude("club.minnced", "opus-java")
        exclude("com.google.code.findbugs", "jsr305")
    }
    val spock by sourceSets
    testImplementation(spock.let { it.output + it.runtimeClasspath })

    testRuntimeOnly("info.solidsoft.spock:spock-global-unroll:${versions["spock-global-unroll"]}")
    testRuntimeOnly("net.bytebuddy:byte-buddy:${versions["byte-buddy"]}")
    testRuntimeOnly("org.objenesis:objenesis:${versions["objenesis"]}")

    val pitestImplementation by configurations
    pitestImplementation("org.pitest:pitest-entry:${versions["pitest"]}")
    pitestImplementation("org.spockframework:spock-core:${versions["spock"]}")

    val spockCompileOnly by configurations
    spockCompileOnly("org.codehaus.groovy:groovy:${versions["groovy"]}")
    spockCompileOnly("org.spockframework:spock-core:${versions["spock"]}")
    val spockImplementation by configurations
    spockImplementation("org.apache.logging.log4j:log4j-core:${versions["log4j"]}:tests")
    spockImplementation("org.apache.logging.log4j:log4j-core:${versions["log4j"]}")

    val integTestCommonImplementation by configurations
    integTestCommonImplementation("org.spockframework:spock-core:${versions["spock"]}")
    integTestCommonImplementation("javax.enterprise:cdi-api:${versions["cdi"]}")
    integTestCommonImplementation("javax.annotation:javax.annotation-api:${versions["javax.annotation-api"]}")
    integTestCommonImplementation("org.apache.logging.log4j:log4j-core:${versions["log4j"]}:tests")
    integTestCommonImplementation("org.apache.logging.log4j:log4j-core:${versions["log4j"]}")

    val integTestCommonRuntimeOnly by configurations
    integTestCommonRuntimeOnly(spock.let { it.output + it.runtimeClasspath })
    integTestCommonRuntimeOnly("info.solidsoft.spock:spock-global-unroll:${versions["spock-global-unroll"]}")
    integTestCommonRuntimeOnly("org.jboss.weld.se:weld-se-core:${versions["weld-se"]}") {
        @Suppress("UnstableApiUsage")
        because("CDI implementation")
    }
    integTestCommonRuntimeOnly("org.jboss:jandex:${versions["jandex"]}") {
        @Suppress("UnstableApiUsage")
        because("faster CDI bean scanning")
    }
    integTestCommonRuntimeOnly("org.fusesource.jansi:jansi:${versions["jansi"]}") {
        @Suppress("UnstableApiUsage")
        because("ANSI colors on Windows")
    }

    val integTestCommon by sourceSets
    integTestSourceSets.keys.forEach {
        "${it}Implementation"(integTestCommon.let { it.output + it.runtimeClasspath })
        "${it}Implementation"("org.spockframework:spock-core:${versions["spock"]}")
        "${it}Implementation"("javax.enterprise:cdi-api:${versions["cdi"]}")

        "${it}CompileOnly"("javax.annotation:javax.annotation-api:${versions["javax.annotation-api"]}")
    }

    val messageFrameworkDependencies = mapOf(
            "javacord" to "org.javacord:javacord",
            "jda" to "net.dv8tion:JDA"
    )

    val additionalMessageFrameworkDependencies = mapOf(
            "javacord" to listOf(
                    "club.minnced:discord-webhooks:${versions["discordWebhooks"]}",
                    "org.apache.logging.log4j:log4j-slf4j-impl:${versions["log4j"]}"
            ),
            "jda" to listOf(
                    "club.minnced:discord-webhooks:${versions["discordWebhooks"]}",
                    "org.apache.logging.log4j:log4j-slf4j-impl:${versions["log4j"]}"
            )
    )

    messageFrameworkVersions.forEach { (messageFramework, frameworkVersions) ->
        var integTestImplementation = configurations.getByName("${messageFramework}IntegTestImplementation")
        integTestImplementation("${messageFrameworkDependencies[messageFramework]}:${frameworkVersions.first()}") {
            exclude("club.minnced", "opus-java")
        }
        additionalMessageFrameworkDependencies[messageFramework]?.forEach { integTestImplementation(it) }

        var integTestRuntimeOnly = configurations.getByName("${messageFramework}IntegTestRuntimeOnly")
        integTestRuntimeOnly("org.antlr:antlr4-runtime:${versions["antlr"]}")

        frameworkVersions.drop(1).forEach {
            integTestImplementation = configurations.getByName("${messageFramework}${sanitizeVersion(it)}IntegTestImplementation")
            integTestImplementation("${messageFrameworkDependencies[messageFramework]}:$it") {
                exclude("club.minnced", "opus-java")
            }
            additionalMessageFrameworkDependencies[messageFramework]?.forEach { integTestImplementation(it) }

            integTestRuntimeOnly = configurations.getByName("${messageFramework}${sanitizeVersion(it)}IntegTestRuntimeOnly")
            integTestRuntimeOnly("org.antlr:antlr4-runtime:${versions["antlr"]}")
        }
    }
}

val testResponseTimeout by double(10.0)
val testManualCommandTimeout by double(10 * 60.0)
val testDiscordToken1 by optionalString()
val testDiscordToken2 by optionalString()
val testDiscordServerId by optionalString()
val includeManualTests by boolean()

val integTest by tasks.registering {
    group = "verification"
}

val integTestReport by tasks.registering(TestReport::class) {
    group = "verification"
    @Suppress("UnstableApiUsage")
    destinationDir = reporting.baseDirectory.dir("tests/integTest").get().asFile

    gradle.taskGraph.whenReady {
        reportOn(allTasks.filter {
            (it is Test) && (it.extra.properties["testType"] == "integration")
        })
    }
}

integTestSourceSets.forEach { (testSourceSetName, referenceSourceSetName) ->
    val testTask = tasks.register<Test>(testSourceSetName) {
        extra["testType"] = "integration"
        description = "Runs the ${testSourceSetName.capitalize()} integration tests."
        group = "verification"
        testClassesDirs = sourceSets.getByName(referenceSourceSetName).output.classesDirs
        classpath = (sourceSets.getByName(testSourceSetName).runtimeClasspath
                + sourceSets.getByName(referenceSourceSetName).output)
                .filter { it.exists() }

        if (includeManualTests.not()) {
            useJUnit {
                excludeCategories("net.kautler.command.integ.test.ManualTests")
            }
        }

        systemProperty("testResponseTimeout", testResponseTimeout)
        systemProperty("testManualCommandTimeout", testManualCommandTimeout)

        listOf("javacord", "jda").forEach {
            if (referenceSourceSetName == "${it}IntegTest") {
                systemProperty("testDiscordToken1", testDiscordToken1 ?: "")
                systemProperty("testDiscordToken2", testDiscordToken2 ?: "")
                systemProperty("testDiscordServerId", testDiscordServerId ?: "")

                doFirst("verify Discord tokens and server id are set") {
                    testDiscordToken1.verifyPropertyIsSet("testDiscordToken1", rootProject.name)
                    testDiscordToken2.verifyPropertyIsSet("testDiscordToken2", rootProject.name)
                    testDiscordServerId.verifyPropertyIsSet("testDiscordServerId", rootProject.name)
                }
            }
        }

        finalizedBy(integTestReport)
        shouldRunAfter(tasks.test)
    }

    integTest {
        dependsOn(testTask)
    }
}

jacoco {
    toolVersion = versions.safeGet("jacoco")
}

val jacocoIntegTestReport by tasks.registering(JacocoReport::class) {
    group = "verification"
    sourceSets(sourceSets.main.get())
}

tasks.withType<Test> {
    if (extra.properties["testType"] == "integration") {
        finalizedBy(jacocoIntegTestReport)
    }
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

val applyJacocoTestReportExcludes by tasks.registering {
    doLast {
        tasks.withType<JacocoReport> {
            classDirectories.setFrom(classDirectories.asFileTree.matching {
                exclude(
                        "net/kautler/command/usage/UsageBaseVisitor.class",
                        "net/kautler/command/usage/UsageLexer.class",
                        "net/kautler/command/usage/UsageParser.class",
                        "net/kautler/command/usage/UsageParser$*.class"
                )
            }.files)
        }
    }
}

tasks.jacocoTestReport {
    dependsOn(applyJacocoTestReportExcludes)
}

jacocoIntegTestReport {
    dependsOn(applyJacocoTestReportExcludes)
}

gradle.taskGraph.whenReady {
    allTasks
            .filter { (it is Test) && (it.extra.properties["testType"] == "integration") }
            .toTypedArray()
            .also {
                jacocoIntegTestReport {
                    executionData(*it)
                }
            }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            element = "CLASS"
            excludes = listOf(
                    "net.kautler.command.usage.UsageBaseVisitor",
                    "net.kautler.command.usage.UsageLexer",
                    "net.kautler.command.usage.UsageParser",
                    "net.kautler.command.usage.UsageParser.*"
            )
            limit {
                counter = "INSTRUCTION"
                value = "MISSEDCOUNT"
                maximum = 0.toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "MISSEDCOUNT"
                maximum = 0.toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

pitest {
    pitestVersion(versions.safeGet("pitest"))
    // work-around for https://github.com/hcoles/pitest/pull/687
    testPlugin("spock")
    mutators(listOf(
            "INVERT_NEGS",
            "MATH",
            "VOID_METHOD_CALLS",
            "NEGATE_CONDITIONALS",
            "CONDITIONALS_BOUNDARY",
            "INCREMENTS",

            "RETURN_VALS",
            "TRUE_RETURNS",
            "FALSE_RETURNS",
            "PRIMITIVE_RETURNS",
            "EMPTY_RETURNS",
            "NULL_RETURNS",

            "REMOVE_CONDITIONALS",
            "CONSTRUCTOR_CALLS",
            "INLINE_CONSTS",
            "REMOVE_INCREMENTS",
            "NON_VOID_METHOD_CALLS",
            "EXPERIMENTAL_MEMBER_VARIABLE",
            "EXPERIMENTAL_ARGUMENT_PROPAGATION",
            "EXPERIMENTAL_NAKED_RECEIVER",
            "EXPERIMENTAL_BIG_INTEGER",
            "EXPERIMENTAL_SWITCH",
            "REMOVE_SWITCH",
            "AOR",
            "AOD",
            "OBBN",
            "UOI3", "UOI4"
    ))
    verbose(logger.isDebugEnabled)
    targetTests(listOf("net.kautler.*Test"))
    outputFormats(listOf(
            "HTML",
            "XML",
            "SURVIVOR_DETECTOR",
            "UNCOVERED_DETECTOR"
    ))
    detectInlinedCode(true)
    timestampedReports(false)
    features(listOf("-FLOGCALL"))
    timeoutFactor(2.toBigDecimal())
    timeoutConstInMillis(SECONDS.toMillis(15).toInt())
    excludedClasses(setOf(
            "net.kautler.command.usage.UsageBaseVisitor",
            "net.kautler.command.usage.UsageLexer",
            "net.kautler.command.usage.UsageParser",
            "net.kautler.command.usage.UsageParser$*"
    ))
    mutationThreshold(100)
    maxSurviving(0)
}

tasks.pitest {
    // work-around for https://github.com/szpak/gradle-pitest-plugin/pull/141
    shouldRunAfter(tasks.test)

    val pitest by sourceSets
    launchClasspath += pitest.let { it.output + it.runtimeClasspath }
    // work-around for https://github.com/hcoles/pitest/pull/682
    additionalClasspath += pitest.let { it.output + it.runtimeClasspath }

    doFirst("validate configured mutators") {
        val notExplicitlyEnabledMutators = setOf(
                "ALL",
                "DEFAULTS",
                "NEW_DEFAULTS",
                "RETURNS",
                "STRONGER",
                "REMOVE_CONDITIONALS_EQ_IF",
                "REMOVE_CONDITIONALS_EQ_ELSE",
                "REMOVE_CONDITIONALS_ORD_IF",
                "REMOVE_CONDITIONALS_ORD_ELSE",
                "ABS",
                "AOR_1", "AOR_2", "AOR_3", "AOR_4",
                "AOD1", "AOD2",
                "CRCR1", "CRCR2", "CRCR3", "CRCR4", "CRCR5", "CRCR6",
                "CRCR",
                "OBBN1", "OBBN2", "OBBN3",
                "ROR1", "ROR2", "ROR3", "ROR4", "ROR5",
                "ROR",
                "UOI",
                "UOI1", "UOI2"
        )

        val availableMutators = Mutator::class.java
                .getDeclaredField("MUTATORS")
                .apply { isAccessible = true }
                .get(null)
                .let {
                    when (it) {
                        is Map<*, *> -> it.keys.map(Any?::toString).toSet()
                        else -> emptySet()
                    }
                }

        val mutators = mutators.get()

        val schroedingersMutatorChamber = mutators.intersect(notExplicitlyEnabledMutators)
        if (schroedingersMutatorChamber.isNotEmpty()) {
            error("There are enabled and at the same time not enabled mutators: ${schroedingersMutatorChamber.sorted()}")
        }

        val newMutators = availableMutators - mutators - notExplicitlyEnabledMutators
        if (newMutators.isNotEmpty()) {
            error("There are new mutators: ${newMutators.sorted()}")
        }
    }
}
