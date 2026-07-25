package com.morphdrop.app.util

import android.net.Uri

object ConversionSessionManager {
    var currentConversionTypeId: String = ""
    var inputUris: List<Uri> = emptyList()
    var outputFormat: String = ""
    var outputFileName: String = ""
    var quality: Int = 100
    var pageRangeStart: String = ""
    var pageRangeEnd: String = ""
    
    fun clear() {
        currentConversionTypeId = ""
        inputUris = emptyList()
        outputFormat = ""
        outputFileName = ""
        quality = 100
        pageRangeStart = ""
        pageRangeEnd = ""
    }
}
