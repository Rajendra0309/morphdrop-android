package com.morphdrop.app.domain.model

enum class BatchPdfOperation(val displayName: String, val description: String) {
    COMPRESS(
        displayName = "Compress",
        description = "Reduce file sizes while maintaining readability"
    ),
    WATERMARK(
        displayName = "Add Watermark",
        description = "Stamp custom text or image on all pages"
    ),
    PAGE_NUMBERS(
        displayName = "Add Page Numbers",
        description = "Insert formatted page numbers with custom position"
    ),
    PASSWORD(
        displayName = "Add Password",
        description = "Protect all documents with strong encryption"
    ),
    ROTATE(
        displayName = "Rotate Pages",
        description = "Change orientation of pages across all files"
    ),
    MERGE(
        displayName = "Merge PDFs",
        description = "Combine all selected PDFs into one document"
    )
}

enum class WatermarkType {
    TEXT, IMAGE
}

enum class WatermarkPosition(val label: String) {
    CENTER("Center"),
    TOP_LEFT("Top Left"),
    TOP_RIGHT("Top Right"),
    BOTTOM_LEFT("Bottom Left"),
    BOTTOM_RIGHT("Bottom Right"),
    DIAGONAL("Diagonal")
}

data class WatermarkConfig(
    val type: WatermarkType = WatermarkType.TEXT,
    val text: String = "CONFIDENTIAL",
    val fontSizeSp: Float = 36f,
    val fontColor: Long = 0xFF888888,
    val imageUri: String? = null,
    val imageScale: Float = 0.5f,
    val opacity: Float = 0.35f,
    val rotationDegrees: Float = 45f,
    val position: WatermarkPosition = WatermarkPosition.DIAGONAL,
    val skipFirstPage: Boolean = false
)

enum class PageNumberPosition(val label: String) {
    TOP_LEFT("Top Left"),
    TOP_CENTER("Top Center"),
    TOP_RIGHT("Top Right"),
    BOTTOM_LEFT("Bottom Left"),
    BOTTOM_CENTER("Bottom Center"),
    BOTTOM_RIGHT("Bottom Right")
}

enum class PageNumberFormat(val label: String, val example: String) {
    PAGE_X("Page 1", "Page 1"),
    NUMBER_ONLY("1", "1"),
    PAGE_X_OF_Y("Page 1 of 10", "Page 1 of 10"),
    X_OF_Y("1/10", "1/10"),
    CUSTOM("- 1 -", "- 1 -")
}

data class PageNumberConfig(
    val position: PageNumberPosition = PageNumberPosition.BOTTOM_CENTER,
    val format: PageNumberFormat = PageNumberFormat.PAGE_X_OF_Y,
    val customTemplate: String = "- {page} -",
    val fontSizeSp: Float = 12f,
    val fontColor: Long = 0xFF000000,
    val startNumber: Int = 1,
    val skipFirstPage: Boolean = false,
    val skipLastPage: Boolean = false,
    val marginDp: Float = 24f
)

data class BatchCompressConfig(
    val isTargetSizeMode: Boolean = false,
    val quality: Float = 0.5f,
    val targetSizeMb: Float = 5f
)

data class BatchPasswordConfig(
    val password: String = "",
    val ownerPassword: String? = null,
    val allowPrinting: Boolean = true,
    val allowCopying: Boolean = true,
    val allowEditing: Boolean = true
)

enum class RotateScope(val label: String) {
    ALL_PAGES("All Pages"),
    EVEN_PAGES("Even Pages Only"),
    ODD_PAGES("Odd Pages Only")
}

data class BatchRotateConfig(
    val degrees: Int = 90,
    val scope: RotateScope = RotateScope.ALL_PAGES
)

data class BatchMergeConfig(
    val addTableOfContents: Boolean = false,
    val addPageNumbers: Boolean = false
)

data class BatchPdfItemResult(
    val uriString: String,
    val fileName: String,
    val outputUriString: String? = null,
    val originalSize: Long = 0L,
    val newSize: Long = 0L,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)
