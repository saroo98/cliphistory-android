package app.cliphistory.core

/** Exact known layouts only. Never guess arguments for a new Android Binder API. */
object ClipboardArguments {
    const val LISTENER = "android.content.IOnPrimaryClipChangedListener"
    fun resolve(types: List<String>, listener: Any?, userId: Int): Array<Any?> {
        val hasListener = types.firstOrNull()==LISTENER
        val tail=if(hasListener)types.drop(1) else types
        val args: List<Any?> = when(tail) {
            listOf("java.lang.String","java.lang.String","int","int") -> listOf("com.android.shell",null,userId,0)
            listOf("java.lang.String","java.lang.String","int") -> listOf("com.android.shell",null,userId)
            listOf("java.lang.String","int","int") -> listOf("com.android.shell",userId,0)
            listOf("java.lang.String","int") -> listOf("com.android.shell",userId)
            else -> throw StoreException("UNSUPPORTED_CLIPBOARD_SIGNATURE")
        }
        if(hasListener && listener==null) throw StoreException("LISTENER_REQUIRED")
        return (if(hasListener)listOf(listener)+args else args).toTypedArray()
    }
}
