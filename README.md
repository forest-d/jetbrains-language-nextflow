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

Release commands are defined in `justfile`.

```bash
just --list
```

Create a local `.env` file for the JetBrains Marketplace publishing token:

```bash
just env-init
```

Then set the token in `.env`:

```dotenv
PUBLISH_TOKEN=perm:...
```

Before releasing, update `CHANGELOG.md` and `changeNotes` in `build.gradle.kts`.

To bump the semantic version in `gradle.properties`:

```bash
just bump-patch
just bump-minor
just bump-major
```

You can also use the parameterized form:

```bash
just bump patch
just bump minor
just bump major
```

To publish the current version to JetBrains Marketplace:

```bash
just publish
```

This checks `PUBLISH_TOKEN`, runs `verifyPlugin`, builds the plugin ZIP, and runs `publishPlugin`.

To bump, test, verify, build, and publish in one command:

```bash
just release patch
```

## License

MIT. See [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) for attribution.
