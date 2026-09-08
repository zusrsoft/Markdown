plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "com.hrm.markdown.parser"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        optimization {
            consumerKeepRules.publish = true
            consumerKeepRules.files.add(project.file("consumer-rules.pro"))
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "MarkdownParser"
            isStatic = true
        }
    }

    jvm()

    js {
        browser()
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

mavenPublishing {
    // 仅在显式设置 RELEASE_TO_CENTRAL 环境变量时启用 Central 发布与签名
    // （签名密钥由 CI 注入的 signingInMemoryKey* 环境变量提供；本地 mavenLocal 发布不签名）
    if (providers.environmentVariable("RELEASE_TO_CENTRAL").isPresent) {
        publishToMavenCentral(true)
        signAllPublications()
    }

    coordinates(
        "io.github.zusrsoft",
        "markdown-parser",
        rootProject.property("VERSION").toString()
    )

    pom {
        name.set("Kotlin Multiplatform Markdown Parser")
        description.set(
            """
            Cross-platform Markdown parsing solution with:
            - Full Markdown syntax support
            - AST (Abstract Syntax Tree) generation
            - Incremental parsing support
            - Multi-platform support (Android/iOS/JVM/JS/WasmJS)
        """.trimIndent()
        )
        inceptionYear.set("2026")
        url.set("https://github.com/zusrsoft/Markdown")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("zusrsoft")
                name.set("Kotlin Multiplatform Specialist")
                url.set("https://github.com/zusrsoft/")
            }
        }
        scm {
            url.set("https://github.com/zusrsoft/Markdown")
            connection.set("scm:git:git://github.com/zusrsoft/Markdown.git")
            developerConnection.set("scm:git:ssh://git@github.com/zusrsoft/Markdown.git")
        }
    }
}
