package app.cliphistory

import app.cliphistory.core.*
import java.nio.file.Files
import java.io.File
import java.io.RandomAccessFile

/** Runs real repository/codec code on a JVM. No mocked Android behavior. */
object CoreSuite {
    private class MemorySlot : Slot {
        var bytes = byteArrayOf()
        var fail = false
        var tear = false
        override fun read() = bytes.copyOf()
        override fun writeAndSync(bytes: ByteArray) {
            if (tear) { this.bytes = bytes.copyOf(bytes.size / 2); throw java.io.IOException("Simulated interrupted write") }
            if (fail) throw java.io.IOException("Simulated fsync failure")
            this.bytes = bytes.copyOf()
        }
    }
    private class DiskSlot(private val file: File) : Slot {
        override fun read() = if (file.exists()) file.readBytes() else byteArrayOf()
        override fun writeAndSync(bytes: ByteArray) {
            RandomAccessFile(file, "rw").use { it.seek(0); it.write(bytes); it.setLength(bytes.size.toLong()); it.fd.sync() }
        }
    }
    private fun eq(a: Any?, b: Any?) { check(a == b) { "Expected <$a>; got <$b>" } }
    private inline fun fails(action: () -> Unit) { var caught = false; try { action() } catch (_: java.io.IOException) { caught = true }; check(caught) { "Expected IOException" } }
    private fun fixture(): Triple<HistoryRepository, MemorySlot, MemorySlot> {
        val a = MemorySlot(); val b = MemorySlot(); return Triple(HistoryRepository(a,b), a,b)
    }
    private class MemoryAccess : RandomAccess {
        var bytes=byteArrayOf()
        var synced=false
        var shortIo=false
        override fun size()=bytes.size.toLong()
        override fun read(offset:Long,destination:ByteArray,start:Int,count:Int):Int {
            if(offset>=bytes.size)return -1
            val n=minOf(count,bytes.size-offset.toInt(),if(shortIo)3 else Int.MAX_VALUE)
            bytes.copyInto(destination,start,offset.toInt(),offset.toInt()+n);return n
        }
        override fun write(offset:Long,source:ByteArray,start:Int,count:Int):Int {
            val n=minOf(count,if(shortIo)3 else Int.MAX_VALUE)
            val end=offset.toInt()+n;if(end>bytes.size)bytes=bytes.copyOf(end)
            source.copyInto(bytes,offset.toInt(),start,start+n);return n
        }
        override fun sync(){synced=true}
        override fun close(){}
    }
    val tests: List<Pair<String, () -> Unit>> = listOf(
        "framed storage roundtrip" to {val f=MemoryAccess();val s=FramedSlot(f);s.writeAndSync(byteArrayOf(5,6,7));check(s.read().contentEquals(byteArrayOf(5,6,7)));check(f.synced)},
        "framed storage erases tail without truncate" to {val f=MemoryAccess();val s=FramedSlot(f);s.writeAndSync(ByteArray(100){55});val size=f.size();s.writeAndSync(byteArrayOf(8));eq(size,f.size());check(s.read().contentEquals(byteArrayOf(8)));check(f.bytes.drop(5).all{it==0.toByte()})},
        "framed storage handles short IO" to {val f=MemoryAccess();f.shortIo=true;val s=FramedSlot(f);val b=ByteArray(41){it.toByte()};s.writeAndSync(b);check(s.read().contentEquals(b))},
        "framed invalid header is corruption" to {val f=MemoryAccess();f.bytes=byteArrayOf(127,127,127,127);fails{FramedSlot(f).read()}},
        "Android modern clipboard signature" to {val a=ClipboardArguments.resolve(listOf("java.lang.String","java.lang.String","int","int"),null,0);eq(listOf("com.android.shell",null,0,0),a.toList())},
        "Android listener signature" to {val listener=Any();val a=ClipboardArguments.resolve(listOf(ClipboardArguments.LISTENER,"java.lang.String","java.lang.String","int","int"),listener,0);check(a[0]===listener);eq("com.android.shell",a[1])},
        "unknown clipboard signatures rejected" to {fails{ClipboardArguments.resolve(listOf("java.lang.String","boolean"),null,0)}},
        "older user scoped signature" to {eq(listOf("com.android.shell",10),ClipboardArguments.resolve(listOf("java.lang.String","int"),null,10).toList())},
        "probe starts in waiting state" to {val p=ProbeTracker();eq("NOT_RUN",p.status(0));p.arm("ClipHistory self-test 1234567890",0);eq("WAITING",p.status(1))},
        "probe matches real callback and records pass" to {val p=ProbeTracker();val n="ClipHistory self-test 1234567890";p.arm(n,0);eq(ProbeTracker.Match.CURRENT,p.match(n,9));p.complete(true);eq("PASS",p.status(10));eq(ProbeTracker.Match.IGNORE,p.match(n,11))},
        "late probe remains excluded from real history" to {val p=ProbeTracker();val n="ClipHistory self-test 1234567890";p.arm(n,0);eq("NO_CALLBACK_RECEIVED",p.status(10_000));eq(ProbeTracker.Match.IGNORE,p.match(n,11_000))},
        "previous probe remains excluded after new test" to {val p=ProbeTracker();val a="ClipHistory self-test 1234567890";val b="ClipHistory self-test 1234567891";p.arm(a,0);p.arm(b,20_000);eq(ProbeTracker.Match.IGNORE,p.match(a,20_001));eq(ProbeTracker.Match.NOT_TEST,p.match("normal copy",20_002))},
        "probe storage failure is not a pass" to {val p=ProbeTracker();p.arm("ClipHistory self-test 1234567890",0);p.complete(false);eq("STORAGE_FAILED",p.status(1))},
        "empty store defaults to 100" to { val (r,_,_) = fixture(); eq(100,r.state.limit); eq(0,r.state.entries.size) },
        "capture saves exact text" to { val (r,_,_) = fixture(); eq(CaptureResult.SAVED,r.capture("  hello\nworld  ",5)); eq("  hello\nworld  ",r.state.entries[0].text) },
        "100 item rolling history" to { val (r,_,_) = fixture(); (1..101).forEach { r.capture("clip-$it",it.toLong()) }; eq(100,r.state.entries.size); eq("clip-101",r.state.entries.first().text); eq("clip-2",r.state.entries.last().text) },
        "consecutive duplicates suppressed" to { val (r,_,_) = fixture(); r.capture("A",1); eq(CaptureResult.DUPLICATE,r.capture("A",2)); eq(1,r.state.entries.size) },
        "nonconsecutive duplicates retained" to { val (r,_,_) = fixture(); r.capture("A",1); r.capture("B",2); r.capture("A",3); eq(listOf("A","B","A"),r.state.entries.map { it.text }) },
        "empty text ignored" to { val (r,_,_) = fixture(); eq(CaptureResult.EMPTY,r.capture("",1)); eq(0,r.state.entries.size) },
        "whitespace is not stripped" to { val (r,_,_) = fixture(); r.capture(" \n\t ",1); eq(" \n\t ",r.state.entries.first().text) },
        "sensitive text never written" to { val (r,a,b) = fixture(); eq(CaptureResult.SENSITIVE,r.capture("SECRET-TEST",1,true)); eq(0,r.state.entries.size); check(!String(a.bytes).contains("SECRET-TEST")); check(!String(b.bytes).contains("SECRET-TEST")) },
        "oversize rejected not truncated" to { val (r,_,_) = fixture(); eq(CaptureResult.TOO_LARGE,r.capture("x".repeat(MAX_TEXT_BYTES+1),1)); eq(0,r.state.entries.size) },
        "exact byte boundary accepted" to { val (r,_,_) = fixture(); eq(CaptureResult.SAVED,r.capture("x".repeat(MAX_TEXT_BYTES),1)) },
        "UTF8 byte boundary enforced" to { val (r,_,_) = fixture(); eq(CaptureResult.TOO_LARGE,r.capture("🙂".repeat(16_385),1)) },
        "Unicode persists losslessly" to { val (r,a,b) = fixture(); val text="کوردی — فارسی — 中文 — 👩🏽‍💻\n\u0000"; r.capture(text,1); eq(text,HistoryRepository(a,b).state.entries.first().text) },
        "shrink removes oldest" to { val (r,_,_) = fixture(); (1..60).forEach { r.capture("$it",it.toLong()) }; r.setLimit(20); eq(20,r.state.entries.size); eq("41",r.state.entries.last().text) },
        "invalid low limit rejected" to { val (r,_,_) = fixture(); fails { r.setLimit(19) } },
        "invalid high limit rejected" to { val (r,_,_) = fixture(); fails { r.setLimit(501) } },
        "limit persists" to { val (r,a,b) = fixture(); r.setLimit(250); eq(250,HistoryRepository(a,b).state.limit) },
        "pause persists and ignores copies" to { val (r,a,b) = fixture(); r.setPaused(true); eq(CaptureResult.PAUSED,r.capture("ignored",1)); check(HistoryRepository(a,b).state.paused); r.setPaused(false); eq(CaptureResult.SAVED,r.capture("next",2)) },
        "pause redacts active-state fallback" to { val (r,a,b)=fixture();r.initialize();r.initialize();r.setPaused(true);check(SnapshotCodec.decode(a.bytes).paused);check(SnapshotCodec.decode(b.bytes).paused) },
        "delete exact ID" to { val (r,_,_) = fixture(); r.capture("A",1); r.capture("B",2); check(r.delete(1)); eq(listOf("B"),r.state.entries.map { it.text }) },
        "unknown ID delete is harmless" to { val (r,_,_) = fixture(); check(!r.delete(99)); eq(0L,r.state.generation) },
        "clear keeps IDs monotonic" to { val (r,_,_) = fixture(); r.capture("A",1); r.clear(); r.capture("B",1); eq(2L,r.state.entries.first().id) },
        "clear rewrites both snapshots" to { val (r,a,b) = fixture(); r.capture("DELETE-ME",1); r.clear(); eq(0,SnapshotCodec.decode(a.bytes).entries.size); eq(0,SnapshotCodec.decode(b.bytes).entries.size); check(!String(a.bytes).contains("DELETE-ME")); check(!String(b.bytes).contains("DELETE-ME")) },
        "delete redacts fallback snapshot" to { val (r,a,b) = fixture(); r.capture("DELETE-ME",1); r.capture("KEEP-ME",2); r.delete(1); eq(listOf("KEEP-ME"),SnapshotCodec.decode(a.bytes).entries.map { it.text }); eq(listOf("KEEP-ME"),SnapshotCodec.decode(b.bytes).entries.map { it.text }) },
        "shrink redacts fallback snapshot" to { val (r,a,b) = fixture(); (1..30).forEach { r.capture("$it",it.toLong()) }; r.setLimit(20); eq(20,SnapshotCodec.decode(a.bytes).entries.size); eq(20,SnapshotCodec.decode(b.bytes).entries.size) },
        "single corrupt snapshot recovers older generation" to { val (r,a,b) = fixture(); r.capture("A",1); r.capture("B",2); val latest = if (SnapshotCodec.decode(a.bytes).generation > SnapshotCodec.decode(b.bytes).generation) a else b; latest.bytes[latest.bytes.lastIndex] = (latest.bytes.last().toInt() xor 1).toByte(); eq("A",HistoryRepository(a,b).state.entries.first().text) },
        "two corrupt snapshots report error" to { val a=MemorySlot();val b=MemorySlot();a.bytes=byteArrayOf(1);b.bytes=byteArrayOf(2); fails { HistoryRepository(a,b) } },
        "corrupt plus empty does not erase history" to { val a=MemorySlot(); val b=MemorySlot(); a.bytes=byteArrayOf(1,2); fails { HistoryRepository(a,b) } },
        "truncated new write preserves previous committed state" to { val (r,a,b)=fixture();r.capture("A",1);b.tear=true;fails { r.capture("B",2) };eq("A",r.state.entries.first().text);eq("A",HistoryRepository(a,b).state.entries.first().text) },
        "failed fsync not acknowledged" to { val (r,_,b)=fixture();r.capture("A",1);b.fail=true;fails { r.capture("B",2) };eq(1,r.state.entries.size) },
        "codec detects one-bit corruption" to { val data=SnapshotCodec.encode(Snapshot(1,100,2,false,listOf(Entry(1,1,"hello")))); data[20]=(data[20].toInt() xor 1).toByte();fails { SnapshotCodec.decode(data) } },
        "codec rejects appended data" to { val data=SnapshotCodec.encode(Snapshot(1)); fails { SnapshotCodec.decode(data+byteArrayOf(0)) } },
        "codec rejects every truncated prefix" to { val data=SnapshotCodec.encode(Snapshot(1,100,2,false,listOf(Entry(1,1,"hello")))); for(i in data.indices) fails { SnapshotCodec.decode(data.copyOf(i)) } },
        "disk roundtrip and process-independent reopening" to { val dir=Files.createTempDirectory("cliphistory-test").toFile();try { val a=DiskSlot(File(dir,"a"));val b=DiskSlot(File(dir,"b"));val r=HistoryRepository(a,b);r.capture("on disk",9);eq("on disk",HistoryRepository(DiskSlot(File(dir,"a")),DiskSlot(File(dir,"b"))).state.entries.first().text) } finally { dir.deleteRecursively() } },
        "search is case-insensitive and preserves order" to { val (r,_,_)=fixture();r.capture("Hello World",1);r.capture("else",2);r.capture("WORLD two",3);eq(listOf("WORLD two","Hello World"),r.search("world").map { it.text }) },
        "empty search returns entire history" to { val (r,_,_)=fixture();r.capture("A",1);eq(r.state.entries,r.search("")) },
        "durability verification checks current generation" to { val (r,_,_)=fixture();r.capture("A",1);check(r.verifyDurable()) },
        "initialized empty store is durable" to { val (r,a,b)=fixture();r.initialize();check(a.bytes.isNotEmpty()||b.bytes.isNotEmpty());check(r.verifyDurable()) }
    )
    @JvmStatic fun main(args: Array<String>) {
        var failed=0
        tests.forEach { (name, test) -> try { test(); println("PASS  $name") } catch(t:Throwable) { failed++; println("FAIL  $name: ${t.javaClass.simpleName}: ${t.message}") } }
        println("RESULT ${tests.size-failed}/${tests.size} passed")
        check(failed==0) { "$failed tests failed" }
    }
}
