package app.cliphistory.core

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** Bounded, versioned binary format. Hash is for corruption detection, not encryption. */
object SnapshotCodec {
    private const val MAGIC = 0x434C4831 // CLH1
    private const val VERSION = 3
    private const val HASH_SIZE = 32

    fun encode(s: Snapshot): ByteArray {
        validate(s)
        // Encode each text once and allocate the final frame once.
        val texts=s.entries.map { entry ->
            entry.text.toByteArray(Charsets.UTF_8).also { bytes ->
                if(bytes.size>MAX_TEXT_BYTES || String(bytes,Charsets.UTF_8)!=entry.text)throw StoreException("INVALID_TEXT")
            }
        }
        val bodyLength=40L+texts.mapIndexed { index,text -> 21L+text.size+(if(s.entries[index].expiry==null)0 else 12) }.sum()
        if(bodyLength+HASH_SIZE>MAX_SNAPSHOT_BYTES)throw StoreException("SNAPSHOT_TOO_LARGE")
        val bytes=ByteArray(bodyLength.toInt()+HASH_SIZE)
        val out=ByteBuffer.wrap(bytes)
        out.putInt(MAGIC);out.putInt(VERSION);out.putLong(s.generation);out.putInt(s.limit);out.putLong(s.nextId)
        out.put(if(s.paused)1.toByte() else 0.toByte());out.put(s.duplicateMode.value.toByte());out.putInt(s.entries.size)
        out.put(if(s.captureSensitive)1.toByte() else 0.toByte());out.put(if(s.autoDeleteSensitive)1.toByte() else 0.toByte());out.putInt(s.sensitiveMinutes)
        s.entries.forEachIndexed { index,entry ->
            out.putLong(entry.id);out.putLong(entry.timestamp)
            out.put(((if(entry.sensitive)1 else 0) or (if(entry.expiry!=null)2 else 0)).toByte())
            entry.expiry?.let { out.putInt(it.boot);out.putLong(it.elapsed) }
            out.putInt(texts[index].size);out.put(texts[index])
        }
        val hash=MessageDigest.getInstance("SHA-256").run { update(bytes,0,bodyLength.toInt());digest() }
        hash.copyInto(bytes,bodyLength.toInt());return bytes
    }

    fun decode(bytes: ByteArray): Snapshot {
        if (bytes.size < 33 + HASH_SIZE || bytes.size > MAX_SNAPSHOT_BYTES) throw StoreException("SNAPSHOT_SIZE_INVALID")
        val bodyLength = bytes.size - HASH_SIZE
        val hash = MessageDigest.getInstance("SHA-256").run { update(bytes, 0, bodyLength); digest() }
        if (!MessageDigest.isEqual(hash, bytes.copyOfRange(bodyLength, bytes.size))) throw StoreException("CHECKSUM_MISMATCH")
        try {
            val input=ByteBuffer.wrap(bytes,0,bodyLength)
            if (input.int != MAGIC) throw StoreException("UNSUPPORTED_SNAPSHOT")
            val version = input.int
            if (version !in 1..VERSION) throw StoreException("UNSUPPORTED_SNAPSHOT")
            val generation = input.long; val limit = input.int; val nextId = input.long
            val pausedByte = input.get().toInt()
            if (pausedByte !in 0..1) throw StoreException("INVALID_PAUSE_FLAG")
            val mode = if (version == 1) DuplicateMode.UNIQUE_TEXT else DuplicateMode.fromValue(input.get().toInt())
            val count = input.int
            if (count !in 0..MAX_LIMIT) throw StoreException("INVALID_ENTRY_COUNT")
            val capture=if(version>=3)input.get().toInt() else 0
            val autoDelete=if(version>=3)input.get().toInt() else 1
            val minutes=if(version>=3)input.int else 5
            if(capture !in 0..1 || autoDelete !in 0..1)throw StoreException("INVALID_SENSITIVE_POLICY")
            val entries = ArrayList<Entry>(count)
            val decoder=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            repeat(count) {
                val id = input.long; val timestamp = input.long
                val flags=if(version>=3)input.get().toInt() else 0
                if(flags !in 0..3 || flags==2)throw StoreException("INVALID_SENSITIVE_FLAGS")
                val expiry=if(flags and 2!=0)Expiry(input.int,input.long) else null
                val length = input.int
                if (length !in 1..MAX_TEXT_BYTES || length > input.remaining()) throw StoreException("INVALID_TEXT_LENGTH")
                val text=input.slice().apply { limit(length) }
                val decoded=decoder.reset().decode(text).toString()
                input.position(input.position()+length)
                entries.add(Entry(id, timestamp, decoded,flags and 1!=0,expiry))
            }
            if (input.remaining() != 0) throw StoreException("TRAILING_DATA")
            return Snapshot(generation, limit, nextId, pausedByte == 1, entries.toList(), mode,capture==1,autoDelete==1,minutes).also(::validate)
        } catch (e: StoreException) { throw e
        } catch (_: Exception) { throw StoreException("MALFORMED_SNAPSHOT") }
    }

    private fun validate(s: Snapshot) {
        if (s.generation < 1 || s.limit !in MIN_LIMIT..MAX_LIMIT || s.nextId < 1 || s.entries.size > s.limit) throw StoreException("INVALID_SNAPSHOT_STATE")
        if(s.sensitiveMinutes !in 1..1440)throw StoreException("INVALID_SENSITIVE_POLICY")
        var previousId = s.nextId
        for (entry in s.entries) {
            if (entry.id <= 0 || entry.id >= previousId || entry.timestamp < 0 || entry.text.isEmpty()) throw StoreException("INVALID_ENTRY")
            // decode already checked byte length and strict UTF-8. encode checks
            // byte length and surrogate validity while obtaining its text bytes.
            if (entry.text.length > MAX_TEXT_BYTES) throw StoreException("INVALID_TEXT")
            if(entry.expiry!=null && (!entry.sensitive || entry.expiry.boot<0 || entry.expiry.elapsed<=0))throw StoreException("INVALID_EXPIRY")
            previousId = entry.id
        }
    }
}
