// composeApp/build.gradle.kts
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.dokka)
    alias(libs.plugins.sqldelight)
}

group = "com.neojou.stockviewer"
version = "1.0-SNAPSHOT"


kotlin {
    jvm("desktop")
    jvmToolchain(25)

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("StockViewer")
        browser { }
        binaries.executable()
    }

    // Suppress Beta warning for expect/actual classes (DatabaseDriverFactory).
    // See: https://youtrack.jetbrains.com/issue/KT-61573
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)

                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)

                // Ktor — core + plugins only in commonMain (engines go to platform source sets)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.client.logging)

                // SQLDelight — runtime in commonMain (drivers go to platform source sets)
                implementation(libs.sqldelight.runtime)
                implementation(libs.sqldelight.coroutines)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                // Ktor JVM engine
                implementation(libs.ktor.client.cio)
                // SQLDelight JVM driver (JdbcSqliteDriver)
                implementation(libs.sqldelight.sqlite.driver)
                // Excel .xlsx export (JVM only)
                implementation(libs.apache.poi.ooxml)
            }
        }
        val wasmJsMain by getting {
            dependencies {
                // Ktor JS/Wasm engine
                implementation(libs.ktor.client.js)
                // SQLDelight web worker driver (Phase 2 persistence; Phase 1 may stub driver)
                implementation(libs.sqldelight.web.worker.driver)
            }
        }

    }
}

// SQLDelight: type-safe SQLite API generated from .sq files under
// src/commonMain/sqldelight/
sqldelight {
    databases {
        create("StockViewerDatabase") {
            packageName.set("com.neojou.stockviewer.database")
        }
    }
}

// Dokka 文件生成配置
dokka {
    dokkaPublications {
        html {
            outputDirectory.set(layout.projectDirectory.dir("docs/api"))
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.neojou.stockviewer.MainKt"
    }
}
