package app.cliphistory

import app.cliphistory.core.ClipboardCopyTracker
import org.junit.Assert.*
import org.junit.Test

class ClipboardCopyTrackerTest {
    @Test fun repeatedNotificationForTheSameCopyIsIgnored() {
        val copies=ClipboardCopyTracker()
        assertFalse(copies.isRepeat(100,"copied text",false))
        assertTrue(copies.isRepeat(100,"copied text",false))
    }
    @Test fun copyingTheSameTextAgainIsANewCopy() {
        val copies=ClipboardCopyTracker()
        assertFalse(copies.isRepeat(100,"same text",false))
        assertFalse(copies.isRepeat(101,"same text",false))
        assertTrue(copies.isRepeat(101,"same text",false))
    }
    @Test fun differentTextInTheSameMillisecondIsNotLost() {
        val copies=ClipboardCopyTracker()
        assertFalse(copies.isRepeat(100,"first",false))
        assertFalse(copies.isRepeat(100,"second",false))
        assertTrue(copies.isRepeat(100,"second",false))
    }
    @Test fun sensitivityChangesAreNotHidden() {
        val copies=ClipboardCopyTracker()
        assertFalse(copies.isRepeat(100,"private",false))
        assertFalse(copies.isRepeat(100,"private",true))
        assertTrue(copies.isRepeat(100,"private",true))
    }
    @Test fun spacesCaseAndUnicodeRemainExact() {
        val copies=ClipboardCopyTracker()
        for(text in listOf("Note","note","note ","note 🌿"))assertFalse(copies.isRepeat(100,text,false))
        assertTrue(copies.isRepeat(100,"note 🌿",false))
    }
    @Test fun absentTimestampsDoNotInventCopyIdentity() {
        val copies=ClipboardCopyTracker()
        assertFalse(copies.isRepeat(100,"text",false))
        repeat(2){assertFalse(copies.isRepeat(0,"text",false))}
        assertFalse(copies.isRepeat(-1,"text",false))
        assertFalse(copies.isRepeat(100,"text",false))
    }
    @Test fun clockRollbackDoesNotBlockNewCopies() {
        val copies=ClipboardCopyTracker()
        assertFalse(copies.isRepeat(100,"text",false))
        assertFalse(copies.isRepeat(99,"text",false))
        assertTrue(copies.isRepeat(99,"text",false))
    }
}
