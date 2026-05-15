# Nextflow for JetBrains IDEs

Nextflow language support powered by the [Nextflow Language Server](https://github.com/nextflow-io/language-server).

## Features

- Syntax highlighting for `.nf`, `.nf.test`, and `nextflow.config`
- Code completion, diagnostics, hover, navigation, rename, formatting
- Workflow DAG preview
- Project structure view

## Requirements

- JetBrains IDE 2024.3+
- Java 17+
- [LSP4IJ](https://plugins.jetbrains.com/plugin/23257-lsp4ij) (installed automatically)

## Building

```bash
./gradlew buildPlugin
```

Output: `build/distributions/jetbrains-language-nextflow-*.zip`

## License

MIT. See [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) for attribution.
