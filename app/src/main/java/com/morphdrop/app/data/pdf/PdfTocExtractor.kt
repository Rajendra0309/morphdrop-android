package com.morphdrop.app.data.pdf

import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class PdfTocItem(
    val title: String,
    val pageIndex: Int,
    val children: List<PdfTocItem>
)

object PdfTocExtractor {
    
    suspend fun extractToc(file: File): List<PdfTocItem> = withContext(Dispatchers.IO) {
        val tocList = mutableListOf<PdfTocItem>()
        var document: PDDocument? = null
        try {
            document = PDDocument.load(file)
            val documentCatalog = document.documentCatalog
            val outline = documentCatalog.documentOutline
            
            if (outline != null) {
                // PDFBox uses Page Tree, we need to map page object to index
                val pageTree = documentCatalog.pages
                
                // Helper to extract node
                fun extractNode(node: PDOutlineNode): List<PdfTocItem> {
                    val items = mutableListOf<PdfTocItem>()
                    var currentItem: PDOutlineItem? = node.getFirstChild()
                    
                    while (currentItem != null) {
                        var pageIndex = -1
                        val destination = currentItem.destination
                        if (destination is PDPageDestination) {
                            val page = destination.page
                            if (page != null) {
                                pageIndex = pageTree.indexOf(page)
                            }
                        } else if (currentItem.action != null) {
                            // Sometime destinations are inside GoTo actions
                            val action = currentItem.action
                            if (action is com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo) {
                                val actionDest = action.destination
                                if (actionDest is PDPageDestination) {
                                    val page = actionDest.page
                                    if (page != null) {
                                        pageIndex = pageTree.indexOf(page)
                                    }
                                }
                            }
                        }
                        
                        val title = currentItem.getTitle() ?: "Untitled"
                        
                        val children = if (currentItem.hasChildren()) {
                            extractNode(currentItem)
                        } else emptyList()
                        
                        items.add(PdfTocItem(title, pageIndex, children))
                        currentItem = currentItem.getNextSibling()
                    }
                    return items
                }
                
                tocList.addAll(extractNode(outline))
            }
        } catch (e: Exception) {
            Log.e("PdfTocExtractor", "Failed to extract TOC", e)
        } finally {
            document?.close()
        }
        
        tocList
    }
}
