package app.cliphistory.core

import java.io.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** Bounded, versioned binary format. Hash is for corruption detection, not encryption. */
object SnapshotCodec {
    private const val MAGIC = 0x434C4831 // CLH1
    private const val VERSION = 1
    private const val HASH_SIZE = 32

    fun encode(s: Snapshot): ByteArray {
        validate(s)
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeInt(MAGIC); out.writeInt(VERSION)
            out.writeLong(s.generation); out.writeInt(s.limit); out.writeLong(s.nextId)
            out.writeBoolean(s.paused); out.writeInt(s.entries.size)
            for (entry in s.entries) {
                val text = entry.text.toByteArray(Charsets.UTF_8)
                out.writeLong(entry.id); out.writeLong(entry.timestamp)
                out.writeInt(text.size); out.write(text)
            }
        }
        val body = buffer.toByteArray()
        if (body.size + HASH_SIZE > MAX_SNAPSHOT_BYTES) throw StoreException("SNAPSHOT_TOO_LARGE")
        return body + MessageDigest.getInstance("SHA-256").digest(body)
    }

    fun decode(bytes: ByteArray): Snapshot {
        if (bytes.size < 33 + HASH_SIZE || bytes.size > MAX_SNAPSHOT_BYTES) throw StoreException("SNAPSHOT_SIZE_INVALID")
        val bodyLength = bytes.size - HASH_SIZE
        val hash = MessageDigest.getInstance("SHA-256").run { update(bytes, 0, bodyLength); digest() }
        if (!MessageDigest.isEqual(hash, bytes.copyOfRange(bodyLength, bytes.size))) throw StoreException("CHECKSUM_MISMATCH")
        try {
            DataInputStream(ByteArrayInputStream(bytes, 0, bodyLength)).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != VERSION) throw StoreException("UNSUPPORTED_SNAPSHOT")
                val generation = input.readLong(); val limit = input.readInt(); val nextId = input.readLong()
                val pausedByte = input.readUnsignedByte()
                if (pausedByte !in 0..1) throw StoreException("INVALID_PAUSE_FLAG")
                val count = input.readInt()
                if (count !in 0..MAX_LIMIT) throw StoreException("INVALID_ENTRY_COUNT")
                val entries = ArrayList<Entry>(count)
                repeat(count) {
                    val id = input.readLong(); val timestamp = input.readLong(); val length = input.readInt()
                    if (length !in 1..MAX_TEXT_BYTES || length > input.available()) throw StoreException("INVALID_TEXT_LENGTH")
                    val text = ByteArray(length); input.readFully(text)
                    val decoded = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(text)).toString()
                    entries.add(Entry(id, timestamp, decoded))
                }
                if (input.available() != 0) throw StoreException("TRAILING_DATA")
                return Snapshot(generation, limit, nextId, pausedByte == 1, entries.toList()).also(::validate)
            }
        } catch (e: StoreException) { throw e
        } catch (_: Exception) { throw StoreException("MALFORMED_SNAPSHOT") }
    }

    private fun validate(s: Snapshot) {
        if (s.generation < 1 || s.limit !in MIN_LIMIT..MAX_LIMIT || s.nextId < 1 || s.entries.size > s.limit) throw StoreException("INVALID_SNAPSHOT_STATE")
        var previousId = s.nextId
        for (entry in s.entries) {
            if (entry.id <= 0 || entry.id >= previousId || entry.timestamp < 0 || entry.text.isEmpty()) throw StoreException("INVALID_ENTRY")
            val bytes = entry.text.toByteArray(Charsets.UTF_8)
            if (entry.text.length > MAX_TEXT_BYTES || bytes.size > MAX_TEXT_BYTES || String(bytes, Charsets.UTF_8) != entry.text) throw StoreException("INVALID_TEXT")
            previousId = entry.id
        }
    }
}
