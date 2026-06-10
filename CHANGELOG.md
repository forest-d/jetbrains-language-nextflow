# Changelog

## 1.1.0

- Fix language-server release selection: versions are now compared numerically,
  so e.g. v26.04.10 is correctly preferred over v26.04.9
- Code completion now suggests named workflows alongside process names
- DAG preview no longer accumulates temporary HTML files across refreshes
- Faster Find Usages on large projects (each file is read and scanned once)
- Reduced deprecated/experimental API usage flagged by the marketplace verifier

## 1.0.2

- Bug fixes for Mermaid diagram generation

## 1.0.1

- Fixes bug encountered when switching Nextflow versions
- Improve stability of Mermaid diagram generation

## 1.0.0

- Initial release
