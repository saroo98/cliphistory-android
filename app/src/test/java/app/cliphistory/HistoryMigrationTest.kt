package app.cliphistory

import app.cliphistory.core.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class HistoryMigrationTest {
    private class MemorySlot(var bytes: ByteArray = byteArrayOf()) : Slot {
        var fail = false
        override fun read() = bytes.copyOf()
        override fun writeAndSync(bytes: ByteArray) {
            if (fail) throw IOException("Synthetic fsync failure")
            this.bytes = bytes.copyOf()
        }
    }
    private val legacy = Snapshot(9, 20, 5, true,
        listOf(Entry(4, 4, "B"), Entry(3, 3, "A"), Entry(2, 2, "B"), Entry(1, 1, "A")))
    private fun v1(snapshot: Snapshot): ByteArray {
        val stream = ByteArrayOutputStream()
        DataOutputStream(stream).use { out ->
            out.writeInt(0x434C4831);out.writeInt(1);out.writeLong(snapshot.generation)
            out.writeInt(snapshot.limit);out.writeLong(snapshot.nextId);out.writeBoolean(snapshot.paused)
            out.writeInt(snapshot.entries.size)
            snapshot.entries.forEach { entry ->
                val text = entry.text.toByteArray(Charsets.UTF_8)
                out.writeLong(entry.id);out.writeLong(entry.timestamp);out.writeInt(text.size);out.write(text)
            }
        }
        val body = stream.toByteArray()
        return body + MessageDigest.getInstance("SHA-256").digest(body)
    }
    @Test fun migrationPreservesNewestIdsPauseAndLimitInBothSlots() {
        val a = MemorySlot(v1(legacy));val b = MemorySlot(v1(legacy.copy(generation = 8)))
        val repository = HistoryRepository(a, b)
        assertEquals(legacy, repository.state)
        repository.initialize()
        assertEquals(listOf(4L, 3L), repository.state.entries.map { it.id })
        assertTrue(repository.state.paused);assertEquals(20, repository.state.limit)
        assertEquals(repository.state, SnapshotCodec.decode(a.bytes))
        assertEquals(repository.state, SnapshotCodec.decode(b.bytes))
        val contents = repository.state.entries
        repository.initialize();assertEquals(contents, repository.state.entries)
    }
    @Test fun offlineProjectionDoesNotWrite() {
        val bytes = v1(legacy);val a = MemorySlot(bytes);val b = MemorySlot()
        assertEquals(listOf("B", "A"), HistoryRepository(a, b).state.uniqueProjection().entries.map { it.text })
        assertArrayEquals(bytes, a.bytes);assertEquals(0, b.bytes.size)
    }
    @Test fun failedFirstMigrationWriteDoesNotExposeCandidate() {
        val a = MemorySlot(v1(legacy));val b = MemorySlot().apply { fail = true }
        val repository = HistoryRepository(a, b)
        try { repository.initialize();fail("Fsync failure acknowledged") } catch (_: IOException) { }
        assertEquals(legacy, repository.state);assertEquals(legacy, HistoryRepository(a, b).state)
    }
    @Test fun failedMirrorReportsPartialSuccessAndRetryFinishesCleanup() {
        val a = MemorySlot(v1(legacy)).apply { fail = true };val b = MemorySlot()
        val repository = HistoryRepository(a, b)
        try { repository.initialize();fail("Cleanup failure hidden") }
        catch (error: StoreException) { assertEquals("CHANGE_SAVED_BUT_BACKUP_CLEANUP_FAILED", error.reason) }
        assertEquals(2, HistoryRepository(a, b).state.entries.size)
        a.fail = false;repository.initialize()
        assertEquals(listOf("B", "A"), SnapshotCodec.decode(a.bytes).entries.map { it.text })
        assertEquals(listOf("B", "A"), SnapshotCodec.decode(b.bytes).entries.map { it.text })
    }
    @Test fun unknownModesPauseFlagsAndVersionsAreRejectedEvenWithValidHashes() {
        for ((offset, value, reason) in listOf(Triple(29, 4, "INVALID_DUPLICATE_MODE"),
            Triple(28, 4, "INVALID_PAUSE_FLAG"), Triple(7, 7, "UNSUPPORTED_SNAPSHOT"))) {
            val bytes = SnapshotCodec.encode(Snapshot(1))
            bytes[offset] = value.toByte()
            MessageDigest.getInstance("SHA-256").digest(bytes.copyOf(bytes.size - 32)).copyInto(bytes, bytes.size - 32)
            try { SnapshotCodec.decode(bytes);fail("Invalid state accepted") }
            catch (error: StoreException) { assertEquals(reason, error.reason) }
        }
    }
    @Test fun samePolicyRetryCleansBackupAfterPartialFailure() {
        val repeated = legacy.copy(paused = false, duplicateMode = DuplicateMode.CONSECUTIVE_ONLY)
        val a = MemorySlot(SnapshotCodec.encode(repeated)).apply { fail = true }
        val b = MemorySlot()
        val repository = HistoryRepository(a, b)
        try { repository.setDuplicateMode(DuplicateMode.UNIQUE_TEXT);fail("Cleanup failure hidden") }
        catch (error: StoreException) { assertEquals("CHANGE_SAVED_BUT_BACKUP_CLEANUP_FAILED", error.reason) }
        assertEquals(DuplicateMode.UNIQUE_TEXT, repository.state.duplicateMode)
        assertEquals(4, SnapshotCodec.decode(a.bytes).entries.size)
        a.fail = false
        repository.setDuplicateMode(DuplicateMode.UNIQUE_TEXT)
        assertEquals(repository.state, SnapshotCodec.decode(a.bytes))
        assertEquals(repository.state, SnapshotCodec.decode(b.bytes))
        assertEquals(listOf("B", "A"), repository.state.entries.map { it.text })
    }
}
