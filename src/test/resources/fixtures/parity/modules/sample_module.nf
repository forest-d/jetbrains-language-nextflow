#!/usr/bin/env nextflow

/*
 * Sample module for cross-file navigation and extended completion testing.
 *
 * Exercises: include targets, module-level processes and workflows,
 * cross-file go-to-definition, and extended completion from outside
 * the current script.
 */

nextflow.enable.dsl = 2

/**
 * Align trimmed reads to a reference genome.
 */
process ALIGN_READS {
    tag "$sample_id"
    label 'high_memory'
    publishDir "${params.output_dir}/aligned", mode: 'copy'

    input:
    tuple val(sample_id), path(reads)

    output:
    tuple val(sample_id), path("${sample_id}.sorted.bam"),     emit: bam
    tuple val(sample_id), path("${sample_id}.sorted.bam.bai"), emit: bai

    script:
    def ref = params.genome ?: 'GRCh38'
    """
    bwa mem -t ${task.cpus} ${ref} ${reads} \\
        | samtools sort -@ ${task.cpus} -o ${sample_id}.sorted.bam
    samtools index ${sample_id}.sorted.bam
    """
}

/**
 * Produce a summary of alignment statistics.
 */
process SUMMARIZE_ALIGNMENT {
    tag "$sample_id"
    publishDir "${params.output_dir}/stats", mode: 'copy'

    input:
    tuple val(sample_id), path(bam)

    output:
    tuple val(sample_id), path("${sample_id}_stats.txt"), emit: summary

    script:
    """
    samtools flagstat ${bam} > ${sample_id}_stats.txt
    """
}

workflow ALIGN_AND_SUMMARIZE {
    take:
    reads_ch  // tuple(sample_id, reads)

    main:
    ALIGN_READS(reads_ch)
    SUMMARIZE_ALIGNMENT(ALIGN_READS.out.bam)

    emit:
    bam     = ALIGN_READS.out.bam
    summary = SUMMARIZE_ALIGNMENT.out.summary
}
