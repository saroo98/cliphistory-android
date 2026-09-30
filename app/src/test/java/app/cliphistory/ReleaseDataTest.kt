package app.cliphistory

import app.cliphistory.core.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class ReleaseDataTest {
    @Test fun snapshotsRemainByteCompatibleWithVersionOne() {
        val snapshot=Snapshot(42,100,4,true,listOf(Entry(3,12,"مرحبا 🌿\nnotes"),Entry(1,0,"old text")))
        val stream=ByteArrayOutputStream()
        DataOutputStream(stream).use { out ->
            out.writeInt(0x434C4831);out.writeInt(1);out.writeLong(42);out.writeInt(100)
            out.writeLong(4);out.writeBoolean(true);out.writeInt(2)
            snapshot.entries.forEach {
                val bytes=it.text.toByteArray(Charsets.UTF_8)
                out.writeLong(it.id);out.writeLong(it.timestamp);out.writeInt(bytes.size);out.write(bytes)
            }
        }
        val body=stream.toByteArray()
        val legacy=body+MessageDigest.getInstance("SHA-256").digest(body)
        assertArrayEquals(legacy,SnapshotCodec.encode(snapshot))
        assertEquals(snapshot,SnapshotCodec.decode(legacy))
    }
    @Test fun maximumHistorySurvivesRoundTrip() {
        val entries=(500L downTo 1L).map { Entry(it,it,"x".repeat(MAX_TEXT_BYTES)) }
        val snapshot=Snapshot(1,500,501,false,entries)
        assertEquals(snapshot,SnapshotCodec.decode(SnapshotCodec.encode(snapshot)))
    }
    @Test fun malformedUtf8WithAValidChecksumIsRejected() {
        val bytes=SnapshotCodec.encode(Snapshot(1,20,2,false,listOf(Entry(1,0,"a"))))
        bytes[53]=0x80.toByte()
        MessageDigest.getInstance("SHA-256").run { update(bytes,0,bytes.size-32);digest() }.copyInto(bytes,bytes.size-32)
        try { SnapshotCodec.decode(bytes);fail("Malformed UTF-8 accepted") }
        catch(e:StoreException) { assertEquals("MALFORMED_SNAPSHOT",e.reason) }
    }
    @Test fun searchShowsMatchesBeyondTheFirstPreview() {
        val snippet=SearchPreview.snippet("a".repeat(2000)+"Needle"+"z".repeat(400),"needle")
        assertTrue(snippet.contains("Needle"));assertTrue(snippet.startsWith("… "));assertTrue(snippet.length<=180)
    }
    @Test fun previewsDoNotSplitSurrogatePairs() {
        val text="🌿".repeat(1000)+"match"+"🌿".repeat(100)
        for(query in listOf("","match")) {
            val snippet=SearchPreview.snippet(text,query)
            assertTrue(snippet.length<=180)
            assertEquals(snippet,String(snippet.toByteArray(Charsets.UTF_8),Charsets.UTF_8))
        }
    }
    @Test fun shortTextRemainsExact() { assertEquals("one\ntwo",SearchPreview.snippet("one\ntwo","two")) }
}
