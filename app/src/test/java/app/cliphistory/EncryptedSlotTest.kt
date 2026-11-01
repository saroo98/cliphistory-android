package app.cliphistory

import app.cliphistory.core.*
import org.junit.Assert.*
import org.junit.Test

class EncryptedSlotTest {
    private class Memory(var bytes: ByteArray = byteArrayOf()) : Slot {
        override fun read() = bytes.copyOf()
        override fun writeAndSync(bytes: ByteArray) { this.bytes = bytes.copyOf() }
    }
    private val key = ByteArray(32) { it.toByte() }
    private val identity = ByteArray(16) { (it + 50).toByte() }
    private fun encrypted(memory: Memory, keyBytes: ByteArray = key, index: Int = 0, legacy: Boolean = false) =
        EncryptedSlot(memory, keyBytes, identity, "app.cliphistory", index, legacy)
    @Test fun roundTripAndFreshNonce() {
        val memory = Memory(); val slot = encrypted(memory)
        val text = "  normal and sensitive کوردی 中文 🙂\n\u0000"
        val bytes = SnapshotCodec.encode(Snapshot(1, entries = listOf(Entry(1, 7, text)), nextId = 2))
        slot.writeAndSync(bytes); val first = memory.read()
        assertTrue(EncryptedSlot.isEncrypted(first)); assertFalse(String(first).contains(text))
        assertArrayEquals(bytes, slot.read()); slot.writeAndSync(bytes)
        assertFalse(first.contentEquals(memory.bytes)); assertArrayEquals(bytes, slot.read())
    }
    @Test fun rejectsTamperingWrongKeyAndSlot() {
        val memory = Memory(); encrypted(memory).writeAndSync(SnapshotCodec.encode(Snapshot(1)))
        val original = memory.read()
        for (position in listOf(8, 20, original.lastIndex)) {
            memory.bytes = original.copyOf().apply { this[position] = (this[position].toInt() xor 1).toByte() }
            try { encrypted(memory).read(); fail("Tamper accepted") } catch (_: StoreException) {}
        }
        memory.bytes = original
        for (slot in listOf(encrypted(memory, ByteArray(32)), encrypted(memory, index = 1))) {
            try { slot.read(); fail("Wrong context accepted") } catch (_: StoreException) {}
        }
    }
    @Test fun legacyIsReadOnlyAndExplicitlyGated() {
        val bytes = SnapshotCodec.encode(Snapshot(1)); val memory = Memory(bytes)
        try { encrypted(memory).read(); fail("Plaintext accepted") } catch (_: StoreException) {}
        val pending = encrypted(memory, legacy = true)
        assertArrayEquals(bytes, pending.read()); pending.writeAndSync(bytes)
        assertTrue(EncryptedSlot.isEncrypted(memory.bytes))
    }
    @Test fun rejectsTruncationUnknownVersionAndInvalidSize() {
        val memory = Memory(); encrypted(memory).writeAndSync(SnapshotCodec.encode(Snapshot(1)))
        val original = memory.read()
        for (bytes in listOf(original.copyOf(20), original.copyOf().apply { this[7] = 7 })) {
            memory.bytes = bytes
            try { encrypted(memory).read(); fail("Invalid envelope accepted") } catch (_: StoreException) {}
        }
        try { encrypted(memory).writeAndSync(ByteArray(MAX_SNAPSHOT_BYTES + 1)); fail("Oversize accepted") } catch (_: StoreException) {}
    }
}
