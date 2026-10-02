package com.morphdrop.app.domain.model

/**
 * The last PDF the user opened in the viewer, with the page they left off on.
 * Powers the Home "Pick up where you left off" card. The URI stored is the
 * one the viewer actually opened (already resolved to a readable URI by
 * PdfViewerActivity), so it stays valid across app restarts.
 * @param uri The readable URI of the PDF opened by the viewer.
 * @param page The 0-based page index the user left off on.
 */
data class LastOpenedPdf(
    val uri: String,
    val page: Int,
    val timestamp: Long = System.currentTimeMillis(),
    /** Sender's display filename, for the card subtitle. */
    val displayName: String = "",
    /** Total page count, for "page X of Y" display. */
    val totalPages: Int = 0
)
