package io.nextflow.intellij.dag

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.eclipse.lsp4j.Command
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

class NextflowDagCodeLensTest : BasePlatformTestCase() {

    // ── findDagCommand — lens selection ────────────────────────────

    fun testFindDagCommandSelectsClosestLensToCaret() {
        val file = myFixture.addFileToProject("main.nf", "workflow { }").virtualFile
        val mock = MockLanguageServer(
            codeLensResponses = listOf(
                listOf(
                    previewDagLens(line = 0, args = listOf("lens-0")),
                    previewDagLens(line = 10, args = listOf("lens-10")),
                    previewDagLens(line = 20, args = listOf("lens-20")),
                ),
            ),
        )

        val dagCommand = NextflowDagPreviewService.findDagCommand(mock.server, file, caretLine = 8)
            .get(5, TimeUnit.SECONDS)

        assertEquals(listOf("lens-10"), dagCommand.command.arguments)
    }

    fun testFindDagCommandIgnoresNonPreviewDagLenses() {
        val file = myFixture.addFileToProject("main.nf", "workflow { }").virtualFile
        val mock = MockLanguageServer(
            codeLensResponses = listOf(
                listOf(
                    previewDagLens(line = 5, command = "other.command", title = "Run Test"),
                    previewDagLens(line = 15, args = listOf("dag-lens")),
                ),
            ),
        )

        val dagCommand = NextflowDagPreviewService.findDagCommand(mock.server, file, caretLine = 5)
            .get(5, TimeUnit.SECONDS)

        // Should select the Preview DAG lens at line 15, not the closer non-DAG lens at line 5
        assertEquals(listOf("dag-lens"), dagCommand.command.arguments)
    }

    // ── findDagCommand — retry ─────────────────────────────────────

    fun testFindDagCommandRetriesWhenNoPreviewDagLens() {
        val file = myFixture.addFileToProject("main.nf", "workflow { }").virtualFile
        val mock = MockLanguageServer(
            codeLensResponses = listOf(
                emptyList(),
                emptyList(),
                listOf(previewDagLens(line = 5)),
            ),
        )

        val dagCommand = NextflowDagPreviewService.findDagCommand(mock.server, file, caretLine = 5)
            .get(10, TimeUnit.SECONDS)

        assertEquals(3, mock.codeLensCallCount.get())
        assertEquals("nextflow.previewDag", dagCommand.command.command)
    }

    fun testFindDagCommandFailsAfterMaxAttempts() {
        val file = myFixture.addFileToProject("main.nf", "workflow { }").virtualFile
        val mock = MockLanguageServer(codeLensResponses = listOf(emptyList()))

        try {
            NextflowDagPreviewService.findDagCommand(mock.server, file, caretLine = 0)
                .get(30, TimeUnit.SECONDS)
            fail("Expected execution exception after exhausting retries")
        } catch (e: ExecutionException) {
            assertTrue(e.cause is IllegalStateException)
            assertTrue(e.cause!!.message!!.contains("no Preview DAG code lens"))
        }

        assertEquals(8, mock.codeLensCallCount.get())
    }

    fun testFindDagCommandUsesOriginalCommandWhenResolveFails() {
        val file = myFixture.addFileToProject("main.nf", "workflow { }").virtualFile
        val mock = MockLanguageServer(
            codeLensResponses = listOf(listOf(previewDagLens(line = 0, args = listOf("original")))),
            resolveCodeLensFails = true,
        )

        val dagCommand = NextflowDagPreviewService.findDagCommand(mock.server, file, caretLine = 0)
            .get(5, TimeUnit.SECONDS)

        assertEquals(listOf("original"), dagCommand.command.arguments)
    }

    // ── executeDagCommand ──────────────────────────────────────────

    fun testExecuteDagCommandMapsCodeLensCommandToServerCommand() {
        val mock = MockLanguageServer(executeCommandResult = "graph LR; A-->B")
        val command = Command("Preview DAG", "nextflow.previewDag", listOf("file:///main.nf"))

        val result = NextflowDagPreviewService.executeDagCommand(mock.server, command)
            .get(5, TimeUnit.SECONDS)

        assertEquals("nextflow.server.previewDag", result.command)
        assertEquals("nextflow.server.previewDag", mock.lastExecuteCommandParams.get().command)
    }

    fun testExecuteDagCommandPassesArgumentsThrough() {
        val args = listOf("file:///main.nf", "workflow_name")
        val mock = MockLanguageServer(executeCommandResult = "graph LR")
        val command = Command("Preview DAG", "nextflow.previewDag", args)

        NextflowDagPreviewService.executeDagCommand(mock.server, command)
            .get(5, TimeUnit.SECONDS)

        assertEquals(args, mock.lastExecuteCommandParams.get().arguments)
    }

    fun testExecuteDagCommandPreservesCustomCommandId() {
        val mock = MockLanguageServer(executeCommandResult = "graph LR")
        val command = Command("Preview DAG", "custom.previewDag", emptyList())

        val result = NextflowDagPreviewService.executeDagCommand(mock.server, command)
            .get(5, TimeUnit.SECONDS)

        assertEquals("custom.previewDag", result.command)
    }

    fun testExecuteDagCommandReturnsDagPreviewResult() {
        val mock = MockLanguageServer(executeCommandResult = "graph LR; A-->B")
        val command = Command("Preview DAG", "nextflow.server.previewDag", listOf("arg1"))

        val result = NextflowDagPreviewService.executeDagCommand(mock.server, command)
            .get(5, TimeUnit.SECONDS)

        assertEquals("graph LR; A-->B", result.toMermaid())
        assertEquals(listOf("arg1"), result.arguments)
    }
}
