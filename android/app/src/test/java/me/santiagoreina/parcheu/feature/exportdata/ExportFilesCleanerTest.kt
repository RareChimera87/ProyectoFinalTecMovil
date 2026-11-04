package me.santiagoreina.parcheu.feature.exportdata

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportFilesCleanerTest {
    @Test
    fun signOutDeletesTheExportedFiles() = runTest {
        val files = FakeExportFileStore()

        exportFilesCleaner(files).clear()

        assertEquals(1, files.cleared)
    }
}
