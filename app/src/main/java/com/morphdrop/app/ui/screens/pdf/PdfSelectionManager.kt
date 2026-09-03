package com.morphdrop.app.ui.screens.pdf

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.morphdrop.app.data.pdf.PdfWord

class PdfSelectionManager {
    var selectedWords by mutableStateOf<List<PdfWord>>(emptyList())
        private set
        
    var isSelecting by mutableStateOf(false)
        private set

    private var allWords = emptyList<PdfWord>()
    
    var startIndex by mutableStateOf(-1)
        private set
    var endIndex by mutableStateOf(-1)
        private set

    enum class HandleType { START, END }
    var draggingHandle by mutableStateOf<HandleType?>(null)
        private set

    fun setPageWords(words: List<PdfWord>) {
        allWords = words
    }

    fun startSelection(touchOffset: Offset, pageSize: androidx.compose.ui.geometry.Size) {
        val wordIndex = findClosestWordIndex(touchOffset, pageSize)
        if (wordIndex != -1) {
            isSelecting = true
            startIndex = wordIndex
            endIndex = wordIndex
            updateSelectedWords()
        }
    }

    fun updateSelection(dragOffset: Offset, pageSize: androidx.compose.ui.geometry.Size) {
        if (!isSelecting) return
        val wordIndex = findClosestWordIndex(dragOffset, pageSize)
        if (wordIndex != -1) {
            if (draggingHandle == HandleType.START) {
                startIndex = wordIndex
            } else {
                endIndex = wordIndex
            }
            updateSelectedWords()
        }
    }

    fun startHandleDrag(type: HandleType) {
        isSelecting = true
        draggingHandle = type
    }
    
    fun stopHandleDrag() {
        isSelecting = false
        draggingHandle = null
    }

    fun stopSelection() {
        isSelecting = false
    }

    fun clearSelection() {
        startIndex = -1
        endIndex = -1
        isSelecting = false
        draggingHandle = null
        selectedWords = emptyList()
    }
    
    fun selectAll() {
        if (allWords.isNotEmpty()) {
            startIndex = 0
            endIndex = allWords.size - 1
            isSelecting = false // Keep false so context menu shows up immediately
            updateSelectedWords()
        }
    }

    private fun findClosestWordIndex(touchOffset: Offset, pageSize: androidx.compose.ui.geometry.Size): Int {
        if (allWords.isEmpty()) return -1
        
        val normX = touchOffset.x / pageSize.width
        val normY = touchOffset.y / pageSize.height
        
        var closestIndex = -1
        var minDistance = Float.MAX_VALUE
        
        for (i in allWords.indices) {
            val word = allWords[i]
            val bounds = word.bounds
            
            // If strictly inside the bounds, return immediately
            if (bounds.contains(normX, normY)) return i
            
            // Otherwise find closest center
            val centerX = bounds.centerX()
            val centerY = bounds.centerY()
            val dx = normX - centerX
            val dy = normY - centerY
            val distSq = dx * dx + dy * dy
            if (distSq < minDistance) {
                minDistance = distSq
                closestIndex = i
            }
        }
        
        // Only return if it's reasonably close (e.g., within 0.1 normalized distance ~ 10% of screen)
        return if (minDistance < 0.01f) closestIndex else -1
    }

    private fun updateSelectedWords() {
        if (startIndex == -1 || endIndex == -1 || allWords.isEmpty()) {
            selectedWords = emptyList()
            return
        }
        val min = minOf(startIndex, endIndex)
        val max = maxOf(startIndex, endIndex)
        selectedWords = allWords.subList(min, max + 1)
    }
    
    fun getSelectedText(): String {
        if (selectedWords.isEmpty()) return ""
        val sb = StringBuilder()
        var prevWord: PdfWord? = null
        for (word in selectedWords) {
            if (prevWord != null) {
                val height = word.bounds.bottom - word.bounds.top
                if (Math.abs(word.bounds.top - prevWord.bounds.top) > height * 0.5f) {
                    sb.append("\n")
                } else {
                    sb.append(" ")
                }
            }
            sb.append(word.text)
            prevWord = word
        }
        return sb.toString()
    }
}
