# Nextflow Language Support for JetBrains IDEs

Nextflow language support for IntelliJ IDEA, PyCharm, and other JetBrains IDEs — powered by the [Nextflow Language Server](https://github.com/nextflow-io/language-server).

## Features

- **Syntax highlighting** for `.nf`, `.nf.test`, and `nextflow.config` files (via TextMate grammars from the [VS Code extension](https://github.com/nextflow-io/vscode-language-nextflow))
- **Code completion**, **diagnostics**, **hover**, **go-to-definition**, **references**, **rename**, and **formatting** (via the Nextflow Language Server)
- **Automatic language server management** — downloads and caches the server JAR from GitHub releases
- **Project structure view** for processes, workflows, functions, records, and enums
- **DAG preview** for workflows using the language server's Mermaid output
- Works with **all JetBrains IDEs** (IntelliJ IDEA, PyCharm, WebStorm, etc.)

## Requirements

- JetBrains IDE **2024.3** or later. The plugin descriptor intentionally leaves
  `until-build` unset so private builds can be installed in newer IDE releases.
- **Java 17+** installed and available via `JAVA_HOME` or `PATH`
- [LSP4IJ](https://plugins.jetbrains.com/plugin/23257-lsp4ij) plugin (installed automatically as a dependency)

## Installation

This plugin is currently distributed privately for internal dogfooding. Build the plugin zip and install it from disk via **Settings > Plugins > Install Plugin from Disk**.

See [private distribution notes](docs/distribution/private-distribution.md).

## Local Testing in PyCharm

1. Build the plugin zip:

   ```bash
   JAVA_HOME=/home/forest/.local/jdk PATH=/home/forest/.local/jdk/bin:/usr/bin:/bin ./gradlew buildPlugin
   ```

2. In PyCharm, open **Settings > Plugins**.
3. Click the gear icon, choose **Install Plugin from Disk...**, and select:

   ```text
   build/distributions/jetbrains-language-nextflow-0.1.0.zip
   ```

4. Restart PyCharm when prompted.
5. Open a project with `.nf`, `.nf.test`, or `nextflow.config` files.
6. Check **Settings > Languages & Frameworks > Nextflow** if you need to set Java home or the language server version.
7. Open a Nextflow file and verify highlighting, completion, diagnostics, the **Nextflow** tool window, and **Preview Nextflow DAG** from the editor context menu.

## Building from Source

```bash
./gradlew buildPlugin
```

The plugin zip will be at `build/distributions/jetbrains-language-nextflow-*.zip`. Install it via **Settings > Plugins > Install Plugin from Disk**.

## Architecture

The plugin uses [LSP4IJ](https://github.com/redhat-developer/lsp4ij) to connect to the Nextflow Language Server over stdio. Syntax highlighting is provided by bundled TextMate grammars reused from the VS Code extension.

```
┌─────────────────────────────────────────────┐
│  JetBrains IDE                              │
│  ┌───────────────────────────────────────┐  │
│  │  Nextflow Plugin (Kotlin)             │  │
│  │  ┌─────────┐ ┌──────────┐            │  │
│  │  │ File    │ │ LSP4IJ   │            │  │
│  │  │ Types & │ │ Client   │            │  │
│  │  │ TextMate│ │ Bridge   │            │  │
│  │  └─────────┘ └────┬─────┘            │  │
│  └────────────────────┼──────────────────┘  │
│                       │ stdio (LSP)         │
│  ┌────────────────────┴──────────────────┐  │
│  │  Nextflow Language Server (Java JAR)  │  │
│  └───────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
```

## License

TBD
