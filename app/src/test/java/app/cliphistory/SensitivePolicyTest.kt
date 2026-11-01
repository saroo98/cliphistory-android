package app.cliphistory

import app.cliphistory.core.*
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class SensitivePolicyTest {
    private class MemorySlot:Slot {
        var bytes=byteArrayOf()
        var fail=false
        var beforeWrite:(()->Unit)?=null
        override fun read()=bytes.copyOf()
        override fun writeAndSync(bytes:ByteArray) { beforeWrite?.invoke();if(fail)throw IOException("Synthetic write failure");this.bytes=bytes.copyOf() }
    }
    private class Clock(var time:HistoryTime=HistoryTime(7,1000)):HistoryClock { override fun now()=time }
    private class Fixture {
        val clock=Clock();val a=MemorySlot();val b=MemorySlot();val repo=HistoryRepository(a,b,clock)
        fun enable(minutes:Int=5)=repo.setSensitivePolicy(true,true,minutes)
    }
    @Test fun defaultsExcludeSensitiveAndKeepNormalWorkflow() {
        val f=Fixture()
        assertEquals(CaptureResult.SENSITIVE,f.repo.capture("private",1,true))
        assertEquals(CaptureResult.SAVED,f.repo.capture("ordinary",2))
        assertEquals(listOf("ordinary"),f.repo.available.entries.map { it.text })
        assertFalse(f.repo.state.captureSensitive);assertTrue(f.repo.state.autoDeleteSensitive);assertEquals(5,f.repo.state.sensitiveMinutes)
    }
    @Test fun exactDeadlineHidesAllReadPathsAndCleansBothSlots() {
        val f=Fixture();f.enable(1);f.repo.capture("private",10,true);f.repo.capture("normal",11)
        f.clock.time=HistoryTime(7,60999);assertEquals("private",f.repo.entry(1)?.text)
        f.clock.time=HistoryTime(7,61000)
        assertNull(f.repo.entry(1));assertTrue(f.repo.search("private").isEmpty())
        assertEquals(1,f.repo.available.entries.size);assertTrue(f.repo.expire())
        assertTrue(f.repo.verifyBothDurable());assertEquals(listOf("normal"),f.repo.state.entries.map { it.text })
    }
    @Test fun restartPreservesDeadlineRebootAndUnknownBootWithholdTimedData() {
        val f=Fixture();f.enable();f.repo.capture("private",1,true)
        val restored=HistoryRepository(f.a,f.b,f.clock)
        assertEquals(f.repo.state,restored.state)
        f.clock.time=HistoryTime(8,1);assertNull(restored.entry(1));restored.initialize(true)
        assertTrue(restored.verifyBothDurable());assertTrue(restored.available.entries.isEmpty())
        f.clock.time=HistoryTime(-1,1)
        try { f.repo.capture("another",2,true);fail("Unknown boot accepted") } catch(e:StoreException) { assertEquals("BOOT_IDENTITY_UNAVAILABLE",e.reason) }
    }
    @Test fun wallClockChangesDoNotExtendDeadline() {
        val f=Fixture();f.enable(1);f.repo.capture("private",Long.MAX_VALUE,true)
        f.clock.time=HistoryTime(7,61000);assertNull(f.repo.entry(1))
    }
    @Test fun queuedCaptureKeepsItsOriginalMonotonicDeadline() {
        val f=Fixture();f.enable(1);f.clock.time=HistoryTime(7,40000)
        assertEquals(CaptureResult.SAVED,f.repo.capture("queued",1,true,HistoryTime(7,1000)))
        assertEquals(Expiry(7,61000),f.repo.entry(1)!!.expiry)
        f.clock.time=HistoryTime(7,61000);assertNull(f.repo.entry(1))
    }
    @Test fun queueDelayCannotSaveAnAlreadyExpiredSensitiveEvent() {
        val f=Fixture();f.enable(1);f.clock.time=HistoryTime(7,61000)
        val committed=f.repo.state
        assertEquals(CaptureResult.SENSITIVE,f.repo.capture("expired in queue",1,true,HistoryTime(7,1000)))
        assertEquals(committed,f.repo.state);assertTrue(f.repo.verifyBothDurable())
    }
    @Test fun invalidQueuedCaptureClockIsRejectedWithoutWriting() {
        val f=Fixture();f.enable(1)
        val committed=f.repo.state
        for(time in listOf(HistoryTime(8,1),HistoryTime(-1,1),HistoryTime(7,-1),HistoryTime(7,1001))) {
            try { f.repo.capture("invalid clock",1,true,time);fail("Invalid capture clock accepted") }
            catch(_:StoreException) { }
            assertEquals(committed,f.repo.state)
        }
    }
    @Test fun recopyAndUnmarkedCopyKeepEarliestDeadlineAndClassification() {
        val f=Fixture();f.enable(1);f.repo.capture("private",1,true)
        val original=f.repo.entry(1)!!.expiry
        f.repo.capture("other",2);f.clock.time=HistoryTime(7,40000);f.repo.capture("private",3)
        assertEquals(2,f.repo.available.entries.size)
        assertTrue(f.repo.available.entries.first().sensitive);assertEquals(original,f.repo.available.entries.first().expiry)
        f.clock.time=HistoryTime(7,61000);assertTrue(f.repo.search("private").isEmpty())
    }
    @Test fun delayedUnmarkedRecopyCannotDowngradeAnExpiredSensitiveMatch() {
        val f=Fixture();f.enable(1);f.repo.capture("private",1,true)
        f.repo.capture("ordinary",2)
        f.clock.time=HistoryTime(7,62000)
        assertEquals(CaptureResult.SENSITIVE,f.repo.capture("private",3,false,HistoryTime(7,60000)))
        assertEquals(listOf("ordinary"),f.repo.available.entries.map { it.text })
        assertTrue(f.repo.verifyBothDurable())
    }
    @Test fun delayedUnmarkedRecopyCannotDowngradeExpiredSensitiveUndo() {
        val f=Fixture();f.enable(1);f.repo.capture("private",1,true);f.repo.delete(1)
        f.clock.time=HistoryTime(7,62000)
        assertEquals(CaptureResult.SENSITIVE,f.repo.capture("private",2,false,HistoryTime(7,60000)))
        assertTrue(f.repo.available.entries.isEmpty());assertEquals(0L,f.repo.undoToken)
        assertTrue(f.repo.verifyBothDurable())
    }
    @Test fun queuedObservationKeepsSensitivityAfterAnotherCaptureExpiresTheMatch() {
        val f=Fixture();f.enable(1);f.repo.capture("private",1,true)
        val observed=HistoryTime(7,60000)
        val retained=f.repo.sensitiveEntries
        f.clock.time=HistoryTime(7,62000)
        f.repo.capture("ordinary",2)
        assertTrue(f.repo.state.entries.none { it.sensitive })
        val inherited=sensitiveMatch(retained,"private",observed)!!
        assertEquals(CaptureResult.SENSITIVE,f.repo.capture("private",3,true,observed,inherited.expiry))
        assertEquals(listOf("ordinary"),f.repo.available.entries.map { it.text })
        assertTrue(listOf(f.a,f.b).all { slot -> SnapshotCodec.decode(slot.bytes).entries.none { it.text=="private" } })
    }
    @Test fun observationAfterExpiryDoesNotInheritAnExpiredClassification() {
        val f=Fixture();f.enable(1);f.repo.capture("private",1,true)
        assertNull(sensitiveMatch(f.repo.sensitiveEntries,"private",HistoryTime(7,61000)))
        assertNull(sensitiveMatch(f.repo.sensitiveEntries,"other",HistoryTime(7,1000)))
        assertNull(sensitiveMatch(f.repo.sensitiveEntries,"private",HistoryTime(8,1000)))
    }
    @Test fun confirmedIndefinitePolicyDoesNotReuseAQueuedDeadline() {
        val f=Fixture();f.enable(1);f.repo.capture("private",1,true)
        val deadline=f.repo.entry(1)!!.expiry
        f.repo.setSensitivePolicy(true,false,1)
        assertEquals(CaptureResult.DUPLICATE,f.repo.capture("private",2,true,HistoryTime(7,1000),deadline))
        assertNull(f.repo.entry(1)!!.expiry)
    }
    @Test fun committedSensitivityIsVisibleWhileItsRecoveryMirrorIsWriting() {
        val a=MemorySlot();val b=MemorySlot();val clock=Clock()
        var published=emptyList<Entry>()
        val repo=HistoryRepository(a,b,clock) { published=it }
        repo.setSensitivePolicy(true,true,1)
        var writes=0
        var observed:Entry?=null
        val duringWrite={ if(++writes==2)observed=sensitiveMatch(published,"private",clock.now());Unit }
        a.beforeWrite=duringWrite;b.beforeWrite=duringWrite
        repo.capture("private",1,true)
        assertEquals(Expiry(7,61000),observed?.expiry)
    }
    @Test fun shortenedDeadlineIsVisibleWhileItsRecoveryMirrorIsWriting() {
        val a=MemorySlot();val b=MemorySlot();val clock=Clock()
        var published=emptyList<Entry>()
        val repo=HistoryRepository(a,b,clock) { published=it }
        repo.setSensitivePolicy(true,true,5);repo.capture("private",1,true)
        published=repo.sensitiveEntries
        var writes=0
        var observed:Entry?=null
        val duringWrite={ if(++writes==2)observed=sensitiveMatch(published,"private",clock.now());Unit }
        a.beforeWrite=duringWrite;b.beforeWrite=duringWrite
        repo.setSensitivePolicy(true,true,1)
        assertEquals(Expiry(7,61000),observed?.expiry)
    }
    @Test fun newlyMarkedConsecutiveDuplicatesAreAllPromoted() {
        val f=Fixture();f.enable();f.repo.setDuplicateMode(DuplicateMode.CONSECUTIVE_ONLY)
        f.repo.capture("same",1);f.repo.capture("different",2);f.repo.capture("same",3)
        assertEquals(CaptureResult.DUPLICATE,f.repo.capture("same",4,true))
        assertTrue(f.repo.state.entries.filter { it.text=="same" }.all { it.sensitive && it.expiry!=null })
        assertTrue(f.repo.verifyBothDurable())
    }
    @Test fun shorterPolicyCanShortenButLongerPolicyCannotExtendActiveDeadline() {
        val f=Fixture();f.enable(5);f.repo.capture("private",1,true)
        f.clock.time=HistoryTime(7,61000);f.repo.setSensitivePolicy(true,true,15)
        assertEquals(301000L,f.repo.entry(1)!!.expiry!!.elapsed)
        f.repo.setSensitivePolicy(true,true,1);assertEquals(121000L,f.repo.entry(1)!!.expiry!!.elapsed)
    }
    @Test fun autoDeleteTransitionsDoNotResurrectExpiredEntries() {
        val f=Fixture();f.enable(1);f.repo.capture("private",1,true)
        f.clock.time=HistoryTime(7,61000);f.repo.setSensitivePolicy(true,false,5)
        assertTrue(f.repo.available.entries.isEmpty())
        f.repo.capture("indefinite",2,true);assertNull(f.repo.entry(2)!!.expiry)
        f.clock.time=HistoryTime(8,1);assertEquals("indefinite",f.repo.entry(2)!!.text)
        f.repo.setSensitivePolicy(true,true,5);assertEquals(300001L,f.repo.entry(2)!!.expiry!!.elapsed)
    }
    @Test fun disableCapturePurgesSensitiveEntriesAndUndoWithoutTouchingNormal() {
        val f=Fixture();f.enable();f.repo.capture("normal",1);f.repo.capture("private",2,true)
        f.repo.delete(2);val token=f.repo.undoToken;f.repo.setSensitivePolicy(false,true,5)
        assertEquals(0L,f.repo.undoToken);assertEquals(listOf("normal"),f.repo.available.entries.map { it.text })
        try { f.repo.undoDelete(token);fail("Purged Undo restored") } catch(e:StoreException) { assertEquals("UNDO_NOT_AVAILABLE",e.reason) }
        assertTrue(f.repo.verifyBothDurable())
    }
    @Test fun expiryPurgesUndoAndHasNoRestoration() {
        val f=Fixture();f.enable(1);f.repo.capture("private",1,true);f.repo.delete(1);val token=f.repo.undoToken
        assertEquals(60000L,f.repo.expiryDelay())
        f.clock.time=HistoryTime(7,61000);assertEquals(0L,f.repo.undoToken);assertTrue(f.repo.expire());assertNull(f.repo.expiryDelay())
        try { f.repo.undoDelete(token);fail("Expired Undo restored") } catch(e:StoreException) { assertEquals("UNDO_NOT_AVAILABLE",e.reason) }
    }
    @Test fun failedMirrorHidesExpiredDataAndRetriesBothDurableCopies() {
        val f=Fixture();f.enable(1);f.repo.capture("private",1,true)
        f.clock.time=HistoryTime(7,61000);f.b.fail=true
        try { f.repo.expire();fail("Failed mirror acknowledged") } catch(_:IOException) { }
        assertNull(f.repo.entry(1));assertFalse(f.repo.verifyBothDurable())
        f.b.fail=false;assertTrue(f.repo.expire(true));assertTrue(f.repo.verifyBothDurable())
        assertNull(f.repo.expiryDelay())
    }
    @Test fun metadataRoundTripAndTimerBounds() {
        val f=Fixture();f.enable(1440);f.repo.capture("private 🌿",1,true)
        assertEquals(f.repo.state,SnapshotCodec.decode(SnapshotCodec.encode(f.repo.state)))
        for(minutes in listOf(0,1441))try { f.repo.setSensitivePolicy(true,true,minutes);fail("Invalid timer") } catch(_:StoreException) { }
    }
    @Test fun normalCapacityEvictionRemovesSensitiveTextFromBothRecoverySlots() {
        val f=Fixture();f.enable();f.repo.setLimit(20);f.repo.capture("private",1,true)
        (1..20).forEach { f.repo.capture("normal-$it",it.toLong()) }
        assertTrue(f.repo.state.entries.none { it.sensitive });assertTrue(f.repo.verifyBothDurable())
        assertNull(f.repo.expiryDelay())
    }
    @Test fun markingRecapturedTextAlsoPromotesPendingUndo() {
        val f=Fixture();f.enable(1);f.repo.setDuplicateMode(DuplicateMode.CONSECUTIVE_ONLY)
        f.repo.capture("same",1);f.repo.delete(1);val token=f.repo.undoToken
        f.repo.capture("same",2,true);f.repo.undoDelete(token)
        assertTrue(f.repo.state.entries.all { it.sensitive })
        assertEquals(1,f.repo.state.entries.map { it.expiry }.distinct().size)
        f.clock.time=HistoryTime(7,61000);assertTrue(f.repo.available.entries.isEmpty())
    }
}
