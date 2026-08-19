package com.morphdrop.app.domain.repository

import com.morphdrop.app.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

interface BookmarkRepository {
    fun getBookmarksForFile(fileUri: String): Flow<List<BookmarkEntity>>
    suspend fun addBookmark(fileUri: String, pageNumber: Int)
    suspend fun removeBookmark(fileUri: String, pageNumber: Int)
    suspend fun isPageBookmarked(fileUri: String, pageNumber: Int): Boolean
}
