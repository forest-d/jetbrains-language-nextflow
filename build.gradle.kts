import org.jetbrains.intellij.platform.gradle.TestFrameworkType

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
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")

    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion").get())

        bundledPlugin("org.jetbrains.plugins.textmate")

        plugin("com.redhat.devtools.lsp4ij", "0.19.3")

        testFramework(TestFrameworkType.Platform)

        pluginVerifier()
        zipSigner()
    }
}

tasks.test {
    useJUnitPlatform()
}

intellijPlatform {
    pluginConfiguration {
        id = "io.nextflow.jetbrains"
        name = "Nextflow"
        version = project.version.toString()
        description = """
            <p>Nextflow language support powered by the
            <a href="https://github.com/nextflow-io/language-server">Nextflow Language Server</a>.
            Requires Java 17+.</p>
        """.trimIndent()

        ideaVersion {
            sinceBuild = providers.gradleProperty("platformSinceBuild").get()
            untilBuild = provider { null }
        }

        vendor {
            name = "Forest Dussault"
            url = "https://github.com/forest-d"
        }

        changeNotes = """
            <p>Bug fixes for Mermaid diagram generation</p>
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
