#!/usr/bin/env nextflow

/*
 * Feature-parity test pipeline.
 *
 * Exercises: processes (typed & untyped I/O), workflows calling processes,
 * channel operations, function definitions, conditional logic, includes,
 * params references, and DSL2 features.
 */

nextflow.enable.dsl = 2

include { ALIGN_READS } from './modules/sample_module'
include { SUMMARIZE_ALIGNMENT } from './modules/sample_module'

// ── Functions ──────────────────────────────────────────────────

/**
 * Parse a sample ID from a FASTQ filename.
 * @param path  The path to a FASTQ file
 * @return The sample ID string
 */
def parseSampleId(path) {
    def name = path.baseName
    return name.replaceAll(/(_R[12])?\.fastq(\.gz)?$/, '')
}

/** Clamp a numeric value between lo and hi. */
def clamp(value, lo, hi) {
    return Math.max(lo, Math.min(hi, value))
}

// ── Processes ──────────────────────────────────────────────────

/**
 * Run FastQC quality control on raw reads.
 */
process FASTQC {
    tag "$sample_id"
    label 'high_memory'
    publishDir "${params.output_dir}/fastqc", mode: 'copy'

    input:
    tuple val(sample_id), path(reads)

    output:
    tuple val(sample_id), path("*.html"), emit: reports
    tuple val(sample_id), path("*.zip"),  emit: zips

    when:
    !params.skip_qc

    script:
    """
    fastqc --threads ${task.cpus} ${reads}
    """
}

/**
 * Trim adapter sequences from reads.
 */
process TRIM_READS {
    tag "$sample_id"
    publishDir "${params.output_dir}/trimmed", mode: 'copy'

    input:
    tuple val(sample_id), path(reads)

    output:
    tuple val(sample_id), path("*_trimmed.fastq.gz"), emit: trimmed_reads
    path "*.log",                                     emit: trim_log

    script:
    def r1 = reads[0]
    def r2 = reads.size() > 1 ? reads[1] : ''
    """
    trim_galore --paired --cores ${task.cpus} ${r1} ${r2}
    """
}

/**
 * Aggregate QC reports with MultiQC.
 */
process MULTIQC {
    publishDir "${params.output_dir}/multiqc", mode: 'copy'

    input:
    path('*')

    output:
    path "multiqc_report.html", emit: report
    path "multiqc_data",        emit: data

    script:
    """
    multiqc . --filename multiqc_report
    """
}

/**
 * Count lines in a file (simple utility process for testing).
 */
process COUNT_LINES {
    tag "$name"

    input:
    tuple val(name), path(input_file)

    output:
    tuple val(name), stdout, emit: counts

    script:
    """
    wc -l < ${input_file} | tr -d ' '
    """
}

// ── Workflows ──────────────────────────────────────────────────

/**
 * Quality control sub-workflow.
 * Runs FastQC on raw reads and aggregates with MultiQC.
 */
workflow QC_PIPELINE {
    take:
    reads_ch  // tuple(sample_id, [read1, read2])

    main:
    FASTQC(reads_ch)

    qc_files = FASTQC.out.zips
        .map { sample_id, zips -> zips }
        .collect()

    MULTIQC(qc_files)

    emit:
    fastqc_reports = FASTQC.out.reports
    multiqc_report = MULTIQC.out.report
}

/**
 * Read processing sub-workflow.
 * Trims reads, aligns them, then summarizes alignment.
 */
workflow PROCESS_READS {
    take:
    reads_ch

    main:
    TRIM_READS(reads_ch)

    ALIGN_READS(TRIM_READS.out.trimmed_reads)

    SUMMARIZE_ALIGNMENT(ALIGN_READS.out.bam)

    emit:
    alignments = ALIGN_READS.out.bam
    summaries  = SUMMARIZE_ALIGNMENT.out.summary
}

/**
 * Entry workflow — orchestrates the full pipeline.
 */
workflow {
    // Build the reads channel from params
    reads_ch = Channel
        .fromFilePairs(params.reads, checkIfExists: false)
        .map { sample_id, files ->
            def id = parseSampleId(files[0])
            return tuple(id, files)
        }
        .filter { sample_id, files ->
            files.size() == 2
        }

    // Run sub-workflows
    QC_PIPELINE(reads_ch)
    PROCESS_READS(reads_ch)

    // Combine results and count lines in summaries
    summary_ch = PROCESS_READS.out.summaries
        .map { sample_id, summary -> tuple(sample_id, summary) }

    COUNT_LINES(summary_ch)

    // Mix QC and alignment outputs for downstream use
    all_reports = QC_PIPELINE.out.fastqc_reports
        .mix(PROCESS_READS.out.summaries)
        .collect()
}
