package app.cliphistory

import app.cliphistory.core.*
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class HistoryUndoTest {
    private class MemorySlot : Slot {
        var bytes = byteArrayOf()
        var fail = false
        override fun read() = bytes.copyOf()
        override fun writeAndSync(bytes: ByteArray) {
            if (fail) throw IOException("Synthetic fsync failure")
            this.bytes = bytes.copyOf()
        }
    }
    private fun fixture(): Triple<HistoryRepository, MemorySlot, MemorySlot> {
        val a = MemorySlot();val b = MemorySlot()
        return Triple(HistoryRepository(a, b), a, b)
    }
    private fun expectIssue(reason: String, action: () -> Unit) {
        try { action();fail("Expected $reason") }
        catch (error: StoreException) { assertEquals(reason, error.reason) }
    }
    private fun expectWriteFailure(action: () -> Unit) {
        try { action();fail("Fsync failure acknowledged") } catch (_: IOException) { }
    }
    @Test fun restoresExactUnicodeWhitespaceIdentityTimestampAndOrder() {
        val (repository, a, b) = fixture()
        repository.capture("oldest", 100)
        val text = " \tکوردی\n中文 👩🏽‍💻 e\u0301\u0000 \n"
        repository.capture(text, 27)
        repository.capture("newest", 1)
        val original = repository.state.entries
        val nextId = repository.state.nextId
        assertTrue(repository.delete(2))
        val token = repository.undoToken
        assertTrue(token > 0);assertEquals(2L, repository.undoEntryId)
        assertFalse(String(a.bytes, Charsets.UTF_8).contains(text))
        assertFalse(String(b.bytes, Charsets.UTF_8).contains(text))
        assertEquals(HistoryRepository.UndoResult.RESTORED, repository.undoDelete(token))
        assertEquals(original, repository.state.entries)
        assertEquals(nextId, repository.state.nextId)
        assertEquals(0L, repository.undoToken);assertEquals(-1L, repository.undoEntryId)
        assertEquals(repository.state, SnapshotCodec.decode(a.bytes))
        assertEquals(repository.state, SnapshotCodec.decode(b.bytes))
    }
    @Test fun restoresBetweenOriginalIdsAndKeepsInterveningCaptures() {
        val (repository, _, _) = fixture()
        (1..3).forEach { repository.capture("clip-$it", it.toLong()) }
        repository.delete(2);val token = repository.undoToken
        repository.capture("later-4", 4);repository.capture("later-5", 5)
        repository.undoDelete(token)
        assertEquals(listOf(5L, 4L, 3L, 2L, 1L), repository.state.entries.map { it.id })
        assertEquals(listOf("later-5", "later-4", "clip-3", "clip-2", "clip-1"), repository.state.entries.map { it.text })
        assertEquals(6L, repository.state.nextId)
    }
    @Test fun uniqueRecaptureConsumesUndoWithoutDuplicateOrWrite() {
        val (repository, a, b) = fixture()
        repository.capture("same", 1);repository.capture("other", 2)
        repository.delete(1);val token = repository.undoToken
        repository.capture("same", 3)
        val before = repository.state;val beforeA = a.bytes.copyOf();val beforeB = b.bytes.copyOf()
        assertEquals(HistoryRepository.UndoResult.ALREADY_PRESENT, repository.undoDelete(token))
        assertEquals(before, repository.state)
        assertArrayEquals(beforeA, a.bytes);assertArrayEquals(beforeB, b.bytes)
        assertEquals(listOf(3L, 2L), repository.state.entries.map { it.id })
        assertEquals(0L, repository.undoToken)
        expectIssue("UNDO_NOT_AVAILABLE") { repository.undoDelete(token) }
    }
    @Test fun uniqueConflictUsesExactTextWithoutWhitespaceOrUnicodeNormalization() {
        val (repository, _, _) = fixture()
        repository.capture(" \u00e9 ", 1);repository.delete(1)
        val token = repository.undoToken
        repository.capture("\u00e9", 2);repository.capture(" e\u0301 ", 3)
        assertEquals(HistoryRepository.UndoResult.RESTORED, repository.undoDelete(token))
        assertEquals(listOf(" e\u0301 ", "\u00e9", " \u00e9 "), repository.state.entries.map { it.text })
    }
    @Test fun consecutiveModeMayRestoreRepeatedTextWithOriginalId() {
        val (repository, _, _) = fixture()
        repository.setDuplicateMode(DuplicateMode.CONSECUTIVE_ONLY)
        repository.capture("same", 1);repository.capture("other", 2)
        repository.delete(1);val token = repository.undoToken
        repository.capture("same", 3)
        assertEquals(HistoryRepository.UndoResult.RESTORED, repository.undoDelete(token))
        assertEquals(listOf(3L, 2L, 1L), repository.state.entries.map { it.id })
        assertEquals(listOf("same", "other", "same"), repository.state.entries.map { it.text })
    }
    @Test fun fullHistoryRefusesWithoutEvictionAndKeepsUndoForLaterCapacity() {
        val (repository, a, b) = fixture()
        repository.setLimit(20)
        (1..20).forEach { repository.capture("clip-$it", it.toLong()) }
        repository.delete(10);val token = repository.undoToken
        repository.capture("later", 21)
        val before = repository.state;val beforeA = a.bytes.copyOf();val beforeB = b.bytes.copyOf()
        expectIssue("UNDO_HISTORY_FULL") { repository.undoDelete(token) }
        assertEquals(before, repository.state);assertEquals(token, repository.undoToken)
        assertArrayEquals(beforeA, a.bytes);assertArrayEquals(beforeB, b.bytes)
        repository.setLimit(21);repository.undoDelete(token)
        assertEquals(21, repository.state.entries.size)
        assertEquals(Entry(10, 10, "clip-10"), repository.state.entries.first { it.id == 10L })
        assertEquals("later", repository.state.entries.first().text)
    }
    @Test fun fullUniqueHistoryAlreadyContainingDeletedTextStillConsumesUndo() {
        val (repository, _, _) = fixture()
        repository.setLimit(20)
        (1..20).forEach { repository.capture("clip-$it", it.toLong()) }
        repository.delete(10);val token = repository.undoToken
        repository.capture("clip-10", 21)
        val before = repository.state
        assertEquals(HistoryRepository.UndoResult.ALREADY_PRESENT, repository.undoDelete(token))
        assertEquals(before, repository.state);assertEquals(0L, repository.undoToken)
    }
    @Test fun mismatchedMissingAndDismissedTokensCannotMutateOrConsumeCurrentUndo() {
        val (repository, _, _) = fixture()
        expectIssue("UNDO_NOT_AVAILABLE") { repository.undoDelete(0) }
        repository.capture("clip", 1);repository.delete(1)
        val token = repository.undoToken;val before = repository.state
        expectIssue("UNDO_NOT_AVAILABLE") { repository.undoDelete(token xor 1L) }
        expectIssue("UNDO_NOT_AVAILABLE") { repository.dismissUndo(token xor 1L) }
        assertFalse(repository.delete(99))
        assertEquals(token, repository.undoToken);assertEquals(before, repository.state)
        repository.dismissUndo(token)
        assertEquals(before, repository.state);assertEquals(0L, repository.undoToken)
        expectIssue("UNDO_NOT_AVAILABLE") { repository.undoDelete(token) }
        expectIssue("UNDO_NOT_AVAILABLE") { repository.dismissUndo(token) }
    }
    @Test fun secondSuccessfulDeleteReplacesPreviousPendingEntry() {
        val (repository, _, _) = fixture()
        repository.capture("first", 1);repository.capture("second", 2)
        repository.delete(1);val firstToken = repository.undoToken
        repository.delete(2);val secondToken = repository.undoToken
        assertNotEquals(firstToken, secondToken);assertEquals(2L, repository.undoEntryId)
        expectIssue("UNDO_NOT_AVAILABLE") { repository.undoDelete(firstToken) }
        repository.undoDelete(secondToken)
        assertEquals(listOf(Entry(2, 2, "second")), repository.state.entries)
    }
    @Test fun clearInvalidatesUndoAndPauseSettingsDoNot() {
        val (repository, _, _) = fixture()
        repository.capture("first", 1);repository.capture("second", 2)
        repository.delete(1);val token = repository.undoToken
        repository.setPaused(true);repository.setLimit(20)
        repository.setDuplicateMode(DuplicateMode.CONSECUTIVE_ONLY);repository.initialize()
        assertEquals(token, repository.undoToken)
        repository.clear()
        assertEquals(0L, repository.undoToken);assertEquals(-1L, repository.undoEntryId)
        expectIssue("UNDO_NOT_AVAILABLE") { repository.undoDelete(token) }
        assertTrue(repository.state.entries.isEmpty())
    }
    @Test fun reopeningOrReplacingRepositoryDropsRamOnlyUndo() {
        val (repository, a, b) = fixture()
        repository.capture("first", 1);repository.capture("second", 2)
        repository.delete(1);val token = repository.undoToken
        val reopened = HistoryRepository(a, b)
        assertEquals(repository.state, reopened.state)
        assertEquals(0L, reopened.undoToken);assertEquals(-1L, reopened.undoEntryId)
        expectIssue("UNDO_NOT_AVAILABLE") { reopened.undoDelete(token) }
        val replacement = HistoryRepository(MemorySlot(), MemorySlot())
        expectIssue("UNDO_NOT_AVAILABLE") { replacement.undoDelete(token) }
        assertEquals(0L, replacement.undoToken)
    }
    @Test fun failedFirstDeleteWriteKeepsStateAndPreviousUndo() {
        val (repository, a, b) = fixture()
        (1..3).forEach { repository.capture("clip-$it", it.toLong()) }
        repository.delete(1);val token = repository.undoToken;val before = repository.state
        a.fail = true
        expectWriteFailure { repository.delete(2) }
        assertEquals(before, repository.state);assertEquals(token, repository.undoToken)
        assertEquals(1L, repository.undoEntryId);assertEquals(before, HistoryRepository(a, b).state)
        a.fail = false;repository.undoDelete(token)
        assertEquals(listOf(3L, 2L, 1L), repository.state.entries.map { it.id })
    }
    @Test fun failedDeleteMirrorStillReplacesUndoForActualDurableDeletion() {
        val (repository, a, b) = fixture()
        (1..3).forEach { repository.capture("clip-$it", it.toLong()) }
        repository.delete(1);val firstToken = repository.undoToken
        b.fail = true
        expectIssue("CHANGE_SAVED_BUT_BACKUP_CLEANUP_FAILED") { repository.delete(2) }
        val token = repository.undoToken
        assertNotEquals(firstToken, token);assertEquals(2L, repository.undoEntryId)
        assertEquals(listOf(3L), repository.state.entries.map { it.id })
        assertEquals(repository.state, HistoryRepository(a, b).state)
        expectIssue("UNDO_NOT_AVAILABLE") { repository.undoDelete(firstToken) }
        b.fail = false;repository.undoDelete(token)
        assertEquals(listOf(3L, 2L), repository.state.entries.map { it.id })
    }
    @Test fun failedFirstUndoWriteKeepsPendingAndAllowsSingleSuccessfulRetry() {
        val (repository, a, b) = fixture()
        repository.capture("first", 1);repository.capture("second", 2)
        repository.delete(1);val token = repository.undoToken;val before = repository.state
        b.fail = true
        expectWriteFailure { repository.undoDelete(token) }
        assertEquals(before, repository.state);assertEquals(token, repository.undoToken)
        assertEquals(before, HistoryRepository(a, b).state)
        b.fail = false;repository.undoDelete(token)
        assertEquals(listOf(2L, 1L), repository.state.entries.map { it.id })
        expectIssue("UNDO_NOT_AVAILABLE") { repository.undoDelete(token) }
    }
    @Test fun failedUndoMirrorConsumesTokenAfterDurableRestoreAndCannotRestoreTwice() {
        val (repository, a, b) = fixture()
        repository.capture("first", 1);repository.capture("second", 2)
        repository.delete(1);val token = repository.undoToken
        a.fail = true
        expectIssue("CHANGE_SAVED_BUT_BACKUP_CLEANUP_FAILED") { repository.undoDelete(token) }
        assertEquals(listOf(2L, 1L), repository.state.entries.map { it.id })
        assertEquals(repository.state, HistoryRepository(a, b).state)
        assertEquals(0L, repository.undoToken);assertEquals(-1L, repository.undoEntryId)
        val restored = repository.state
        expectIssue("UNDO_NOT_AVAILABLE") { repository.undoDelete(token) }
        assertEquals(restored, repository.state)
        a.fail = false;repository.initialize()
        assertEquals(restored.entries, repository.state.entries)
    }
    @Test fun failedClearWritesKeepUndoOnlyUntilHistoryIsDurablyCleared() {
        val (repository, a, b) = fixture()
        repository.capture("first", 1);repository.capture("second", 2)
        repository.delete(1);val token = repository.undoToken;val before = repository.state
        b.fail = true
        expectWriteFailure { repository.clear() }
        assertEquals(before, repository.state);assertEquals(token, repository.undoToken)
        b.fail = false;a.fail = true
        expectIssue("CHANGE_SAVED_BUT_BACKUP_CLEANUP_FAILED") { repository.clear() }
        assertTrue(repository.state.entries.isEmpty());assertEquals(0L, repository.undoToken)
        assertEquals(repository.state, HistoryRepository(a, b).state)
        expectIssue("UNDO_NOT_AVAILABLE") { repository.undoDelete(token) }
    }
}
