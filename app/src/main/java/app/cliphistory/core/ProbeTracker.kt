package app.cliphistory.core

/** Single-writer, bounded tracking for diagnostics; late test clips must not evict real history. */
class ProbeTracker {
    enum class Match { NOT_TEST, CURRENT, IGNORE }
    private val remembered = LinkedHashSet<String>()
    private var current: String? = null
    private var started = 0L
    private var result = "NOT_RUN"
    fun arm(nonce: String, now: Long) {
        if(!nonce.startsWith("ClipHistory self-test ") || nonce.length !in 30..120)throw StoreException("INVALID_TEST_NONCE")
        remembered.remove(nonce);remembered.add(nonce)
        while(remembered.size>8)remembered.remove(remembered.first())
        current=nonce;started=now;result="WAITING"
    }
    fun match(text: String?, now: Long): Match {
        status(now)
        if(text==null || text !in remembered)return Match.NOT_TEST
        return if(text==current && result=="WAITING") Match.CURRENT else Match.IGNORE
    }
    fun complete(success: Boolean) { if(result=="WAITING")result=if(success) "PASS" else "STORAGE_FAILED" }
    fun status(now: Long): String {
        if(result=="WAITING" && now-started>=10_000)result="NO_CALLBACK_RECEIVED"
        return result
    }
}
