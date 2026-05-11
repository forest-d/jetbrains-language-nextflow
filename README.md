# Nextflow Language Support for JetBrains IDEs

Nextflow language support for IntelliJ IDEA, PyCharm, and other JetBrains IDEs, powered by the [Nextflow Language Server](https://github.com/nextflow-io/language-server).

## Features

- **Syntax highlighting** for `.nf`, `.nf.test`, and `nextflow.config` files (via TextMate grammars from the [VS Code extension](https://github.com/nextflow-io/vscode-language-nextflow))
- **Code completion**, **diagnostics**, **hover**, **go-to-definition**, **references**, **rename**, and **formatting** (via the Nextflow Language Server)
- **Automatic language server management**: downloads and caches the server JAR from GitHub releases
- **Project structure view** for processes, workflows, functions, records, and enums
- **DAG preview** for workflows using the language server's Mermaid output
- Works with **all JetBrains IDEs** (IntelliJ IDEA, PyCharm, WebStorm, etc.)

## Requirements

- JetBrains IDE **2024.3** or later
- **Java 17+** installed and available via `JAVA_HOME` or `PATH`
- [LSP4IJ](https://plugins.jetbrains.com/plugin/23257-lsp4ij) plugin (installed automatically as a dependency)

## Installation

Install from the [JetBrains Marketplace](https://plugins.jetbrains.com/) or build from source and install via **Settings > Plugins > Install Plugin from Disk**.

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

MIT. See [LICENSE](LICENSE) for details.

TextMate grammars are from the [VS Code Nextflow extension](https://github.com/nextflow-io/vscode-language-nextflow) (MIT).
The [Nextflow Language Server](https://github.com/nextflow-io/language-server) (Apache 2.0) is downloaded at runtime.
See [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) for full attribution.
