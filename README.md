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

## Release

1. Update `version` in `gradle.properties`.
2. Update `CHANGELOG.md` and `changeNotes` in `build.gradle.kts`.
3. Verify the release build:

```bash
./gradlew test buildPlugin verifyPlugin
```

4. Publish to JetBrains Marketplace with signing and publishing credentials set:

```bash
CERTIFICATE_CHAIN=... \
PRIVATE_KEY=... \
PRIVATE_KEY_PASSWORD=... \
PUBLISH_TOKEN=... \
./gradlew publishPlugin
```

## License

MIT. See [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) for attribution.
