# Private Distribution

This plugin is currently intended for internal dogfooding only and should not be published to JetBrains Marketplace yet.

## Build

```bash
JAVA_HOME=/home/forest/.local/jdk PATH=/home/forest/.local/jdk/bin:/usr/bin:/bin ./gradlew buildPlugin
```

The private installable artifact is written to:

```text
build/distributions/jetbrains-language-nextflow-0.1.0.zip
```

## Install

1. Open the target JetBrains IDE.
2. Go to Settings > Plugins.
3. Select Install Plugin from Disk.
4. Choose the zip from `build/distributions`.
5. Restart the IDE.

## Dogfooding Checklist

- Open `.nf`, `.nf.test`, and `nextflow.config` files and verify syntax highlighting.
- Open a Nextflow project and verify language server startup, diagnostics, completion, hover, navigation, rename, and formatting.
- Check Settings > Languages & Frameworks > Nextflow.
- Verify the Nextflow tool window groups project symbols as expected.
- Use Preview Nextflow DAG from a workflow and verify rendering, refresh, and node navigation.
- Watch the Nextflow status bar entry during startup and after failures.
