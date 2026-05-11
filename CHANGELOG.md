# Changelog

## 0.1.0

- Add Nextflow file type registration for `.nf`, `.nf.test`, and `nextflow.config`.
- Bundle TextMate grammars for Nextflow script and config syntax highlighting.
- Integrate the Nextflow Language Server through LSP4IJ for completion, diagnostics, navigation, rename, hover, and formatting.
- Download and cache the language server JAR under `~/.nextflow/lsp`.
- Add configurable settings for Java home, language server version, completion limits, diagnostics, formatter options, and excluded folders.
- Add a Nextflow project tool window backed by language-server workspace symbols.
- Add DAG preview support through the language server's Mermaid preview command.
- Add private distribution metadata, status bar status, failure notifications, and focused unit tests.
