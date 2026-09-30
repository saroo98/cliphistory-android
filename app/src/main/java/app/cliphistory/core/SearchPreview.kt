package app.cliphistory.core

/** Bounded IPC snippet centred on a match, without cutting a UTF-16 pair. */
object SearchPreview {
    fun snippet(text:String,query:String,maxLength:Int=180):String {
        require(maxLength>=8)
        if(text.length<=maxLength)return text
        val match=if(query.isEmpty())0 else text.indexOf(query,ignoreCase=true).coerceAtLeast(0)
        var start=(match-(maxLength-4-minOf(query.length,maxLength-4))/2).coerceAtLeast(0)
        if(start>0 && text[start].isLowSurrogate())start--
        val prefix=if(start>0)"… " else ""
        var end=minOf(text.length,start+maxLength-prefix.length-2)
        if(end>start && text[end-1].isHighSurrogate())end--
        return prefix+text.substring(start,end)+if(end<text.length)" …" else ""
    }
}
