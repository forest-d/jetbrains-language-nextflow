plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion").get())

        bundledPlugin("org.jetbrains.plugins.textmate")

        plugin("com.redhat.devtools.lsp4ij", "0.19.3")

        pluginVerifier()
        zipSigner()
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "io.nextflow.intellij"
        name = "Nextflow"
        version = project.version.toString()
        description = """
            Nextflow language support for JetBrains IDEs.
            Provides syntax highlighting, code completion, diagnostics, navigation,
            and more — powered by the Nextflow Language Server.
        """.trimIndent()

        ideaVersion {
            sinceBuild = providers.gradleProperty("platformSinceBuild").get()
            untilBuild = providers.gradleProperty("platformUntilBuild").get()
        }

        vendor {
            name = "Nextflow"
            url = "https://nextflow.io"
        }

        changeNotes = """
            <ul>
                <li>Initial release</li>
            </ul>
        """.trimIndent()
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = listOf("default")
    }
}
