# VS Code Feature Parity Checklist

Manual test script for verifying the JetBrains Nextflow plugin against the
VS Code Nextflow extension. Run against the fixtures in
`src/test/resources/fixtures/parity/`.

**How to use:** Open the fixture project in both VS Code and the JetBrains IDE.
For each test, perform the action in both editors, compare results, and record
pass/fail. Note any discrepancies in the "Notes" column.

---

## 1. Syntax Highlighting

| # | Test | Expected Result | Pass | Notes |
|---|------|-----------------|------|-------|
| 1.1 | Open `main.nf` | Keywords (`process`, `workflow`, `include`, `from`) are highlighted; strings, comments, and operators are distinct colors | PASS | |
| 1.2 | Open `nextflow.config` | Block names (`params`, `process`, `profiles`, `manifest`) highlighted as keywords; string interpolation (`${params.output_dir}`) highlighted differently from plain strings | PASS | Config block names NOT highlighted — same in VS Code (upstream grammar limitation) |
| 1.3 | Open `modules/sample_module.nf` | Same highlighting rules as `main.nf`; no rendering artifacts from the module structure | PASS | |
| 1.4 | Verify comment styles | Block comments (`/* */`), line comments (`//`), and Javadoc (`/** */`) each render distinctly | PASS | |
| 1.5 | Verify Groovy embedded syntax | Closures, string interpolation, ternary expressions, and regex literals inside script blocks are highlighted | PASS | |

## 2. Diagnostics & Error Reporting

| # | Test | Expected Result | Pass | Notes |
|---|------|-----------------|------|-------|
| 2.1 | Open `main.nf` with no errors | No red/yellow squiggles in a valid file | PASS | Diagnostics appear after initial LS startup (~5s) |
| 2.2 | Add typo: change `FASTQC(reads_ch)` to `FASTQX(reads_ch)` | Red squiggle on `FASTQX`; error in Problems panel | PASS | Real-time diagnostics now appear without Ctrl+S after the `NextflowRealtimeDiagnostics` bridge fix. |
| 2.3 | Set error reporting to "Off" | All squiggles disappear | **FAIL** | Squiggles remain after changing to Off and clicking Apply. **BUG: error reporting toggle broken.** Turning error checking off currently has no visible effect. |
| 2.4 | Set error reporting to "Errors" | Only red (error) squiggles visible | | Not tested — blocked by 2.3 |
| 2.5 | Set error reporting to "Warnings" | Red and yellow squiggles visible | | Not tested — blocked by 2.3 |
| 2.6 | Set error reporting to "Paranoid" | All diagnostics visible including hints/info | | Not tested — blocked by 2.3 |
| 2.7 | Verify diagnostics in `nextflow.config` | Introduce a syntax error in config; verify it's reported | | Not yet tested |

## 3. Hover Information

| # | Test | Expected Result | Pass | Notes |
|---|------|-----------------|------|-------|
| 3.1 | Hover over `FASTQC` in entry workflow | Tooltip shows process definition with Javadoc comment | | |
| 3.2 | Hover over `parseSampleId` call | Tooltip shows function signature and `@param`/`@return` docs | | |
| 3.3 | Hover over `params.genome` | Tooltip shows parameter definition; if schema present, shows schema description | | |
| 3.4 | Hover over `reads_ch` variable | Tooltip shows channel type/origin | | |
| 3.5 | Hover over `ALIGN_READS` (included process) | Tooltip shows definition from `modules/sample_module.nf` | | |
| 3.6 | Hover over `task.cpus` in script block | Tooltip shows built-in task property info | | |

## 4. Code Completion

| # | Test | Expected Result | Pass | Notes |
|---|------|-----------------|------|-------|
| 4.1 | Type `FA` inside entry workflow body | `FASTQC` appears in completion list | | |
| 4.2 | Type `params.` inside a process script | Completion offers `input_dir`, `output_dir`, `genome`, etc. | | |
| 4.3 | Type `Channel.` | Completion offers `of`, `fromPath`, `fromFilePairs`, `empty`, etc. | | |
| 4.4 | Type `FASTQC.out.` after FASTQC call | Completion offers `reports`, `zips` (named outputs) | | |
| 4.5 | Enable "extended completion"; type `ALI` in main.nf | `ALIGN_READS` from module appears in completions | | |
| 4.6 | Disable "extended completion"; type `ALI` in main.nf | `ALIGN_READS` does NOT appear (only included symbols) | | |
| 4.7 | Change max items to 5; trigger completion | No more than 5 items shown | | |

## 5. Code Navigation

| # | Test | Expected Result | Pass | Notes |
|---|------|-----------------|------|-------|
| 5.1 | Ctrl+click `FASTQC` in entry workflow | Navigates to `process FASTQC` definition in main.nf | | |
| 5.2 | Ctrl+click `ALIGN_READS` in PROCESS_READS workflow | Navigates to `process ALIGN_READS` in modules/sample_module.nf | | |
| 5.3 | Ctrl+click `parseSampleId` call | Navigates to function definition | | |
| 5.4 | Ctrl+click `'./modules/sample_module'` in include statement | Navigates to the module file | | |
| 5.5 | Find Usages on `FASTQC` process | Shows usage in entry workflow and QC_PIPELINE workflow | | |
| 5.6 | Find Usages on `sample_id` in FASTQC process | Shows all references within the process | | |
| 5.7 | Verify Outline/Structure panel | Lists all processes, workflows, and functions from current file | | |

## 6. Symbol Renaming

| # | Test | Expected Result | Pass | Notes |
|---|------|-----------------|------|-------|
| 6.1 | Rename process `FASTQC` to `QUALITY_CHECK` | All call sites in main.nf update; include statements unaffected | | |
| 6.2 | Rename function `parseSampleId` to `extractSampleId` | Call site in entry workflow updates | | |
| 6.3 | Rename variable `reads_ch` in entry workflow | All references within the workflow update | | |
| 6.4 | Undo all renames | File returns to original state | | |

## 7. Formatting

| # | Test | Expected Result | Pass | Notes |
|---|------|-----------------|------|-------|
| 7.1 | Reformat `main.nf` with defaults | File reformats with consistent indentation; compare output to VS Code | | |
| 7.2 | Enable "Harshil alignment"; reformat | Input/output declarations align vertically | | |
| 7.3 | Enable "Mahesh form"; reformat | Process output blocks move to end of process body | | |
| 7.4 | Enable "Sort declarations"; reformat | Processes/workflows sorted alphabetically | | |
| 7.5 | Reformat `nextflow.config` | Config blocks format consistently; compare to VS Code | | |

## 8. DAG Preview

| # | Test | Expected Result | Pass | Notes |
|---|------|-----------------|------|-------|
| 8.1 | Right-click `main.nf` > Preview Nextflow DAG | DAG opens in a tab showing workflow structure | | |
| 8.2 | Verify node content | DAG shows FASTQC, TRIM_READS, ALIGN_READS, SUMMARIZE_ALIGNMENT, MULTIQC, COUNT_LINES and sub-workflows | | |
| 8.3 | Click a DAG node (e.g., FASTQC) | Editor navigates to the process definition | | |
| 8.4 | Edit main.nf (add/remove a process call) | DAG refreshes automatically to reflect change | | |
| 8.5 | Preview DAG for modules/sample_module.nf | DAG shows ALIGN_AND_SUMMARIZE workflow structure | | |
| 8.6 | JCEF fallback: if JCEF unavailable | Mermaid source text is displayed instead | | |

## 9. Project View (Nextflow Tool Window)

| # | Test | Expected Result | Pass | Notes |
|---|------|-----------------|------|-------|
| 9.1 | Open tool window | Categories shown: Processes, Workflows, Functions | | |
| 9.2 | Verify processes listed | FASTQC, TRIM_READS, MULTIQC, COUNT_LINES, ALIGN_READS, SUMMARIZE_ALIGNMENT | | |
| 9.3 | Verify workflows listed | QC_PIPELINE, PROCESS_READS, entry workflow, ALIGN_AND_SUMMARIZE | | |
| 9.4 | Verify functions listed | parseSampleId, clamp | | |
| 9.5 | Double-click a process | Navigates to the process definition in the correct file | | |
| 9.6 | Edit a file (add a new process) | Tool window refreshes and shows the new process | | |
| 9.7 | Speed search: type "FAST" | Filters to FASTQC | | |

## 10. Status Bar Widget

| # | Test | Expected Result | Pass | Notes |
|---|------|-----------------|------|-------|
| 10.1 | Open a .nf file | Status bar shows "Nextflow: running" or "Nextflow: ready" | | |
| 10.2 | Close all .nf files | Status bar shows idle/stopped state | | |
| 10.3 | Change LS version in settings | Status bar reflects restart; returns to running | | |

## 11. Settings

| # | Test | Expected Result | Pass | Notes |
|---|------|-----------------|------|-------|
| 11.1 | Open Settings > Languages > Nextflow | All settings visible: LS version, Java home, completion max items, error reporting, excludes, extended completion, debug, harshil, mahesh, sort | | |
| 11.2 | Change each setting and click Apply | Settings persist; no errors | | |
| 11.3 | Restart IDE | All changed settings retain their values | | |
| 11.4 | Change LS version | Server restarts with new version | | |
| 11.5 | Set custom Java home | Server restarts using specified Java | | |

## 12. Language Server Management

| # | Test | Expected Result | Pass | Notes |
|---|------|-----------------|------|-------|
| 12.1 | First launch (no cached LS) | LS downloads automatically; notification shown | | |
| 12.2 | Subsequent launch (cached LS) | LS starts immediately from cache; no download | | |
| 12.3 | Disconnect network; change LS version | Falls back to newest cached version; warning shown | | |
| 12.4 | No Java installed | Error notification: "Java 17+ required" | | |

## 13. Parameter Schema Support

| # | Test | Expected Result | Pass | Notes |
|---|------|-----------------|------|-------|
| 13.1 | Hover over `params.genome` with schema present | Hover shows schema description and enum values | | |
| 13.2 | Complete `params.` with schema present | Schema-defined params appear in completion | | |
| 13.3 | Remove `nextflow_schema.json`; hover `params.genome` | Hover shows definition from config only (no schema enrichment) | | |

---

## Summary

| Category | Total | Pass | Fail | Blocked | Not tested |
|----------|-------|------|------|---------|------------|
| Syntax Highlighting | 5 | 5 | 0 | 0 | 0 |
| Diagnostics | 7 | 2 | 1 | 3 | 1 |
| Hover | 6 | 0 | 0 | 0 | 6 |
| Completion | 7 | 0 | 0 | 0 | 7 |
| Navigation | 7 | 0 | 0 | 0 | 7 |
| Renaming | 4 | 0 | 0 | 0 | 4 |
| Formatting | 5 | 0 | 0 | 0 | 5 |
| DAG Preview | 6 | 0 | 0 | 0 | 6 |
| Project View | 7 | 0 | 0 | 0 | 7 |
| Status Bar | 3 | 0 | 0 | 0 | 3 |
| Settings | 5 | 0 | 0 | 0 | 5 |
| LS Management | 4 | 0 | 0 | 0 | 4 |
| Param Schema | 3 | 0 | 0 | 0 | 3 |
| **Total** | **69** | **7** | **1** | **3** | **58** |

### Open Bugs

| Bug | Severity | Status | Description |
|-----|----------|--------|-------------|
| Error reporting toggle broken | Medium | Investigating | Changing error reporting mode to Off has no visible effect; existing squiggles remain. Next step: inspect/fix the settings notification and diagnostic refresh path. |

### Resolved Bugs

| Bug | Resolution |
|-----|------------|
| Diagnostics not real-time | Fixed and manually verified. `NextflowRealtimeDiagnostics` sends debounced full-text `textDocument/didChange` for real Nextflow files without saving. |
