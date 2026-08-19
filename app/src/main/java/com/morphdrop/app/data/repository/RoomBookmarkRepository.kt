package com.morphdrop.app.data.repository

import com.morphdrop.app.data.local.dao.BookmarkDao
import com.morphdrop.app.data.local.entity.BookmarkEntity
import com.morphdrop.app.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomBookmarkRepository @Inject constructor(
    private val bookmarkDao: BookmarkDao
) : BookmarkRepository {
    override fun getBookmarksForFile(fileUri: String): Flow<List<BookmarkEntity>> {
        return bookmarkDao.getBookmarksForFile(fileUri)
    }

    override suspend fun addBookmark(fileUri: String, pageNumber: Int) {
        bookmarkDao.insertBookmark(BookmarkEntity(fileUri = fileUri, pageNumber = pageNumber))
    }

    override suspend fun removeBookmark(fileUri: String, pageNumber: Int) {
        bookmarkDao.deleteBookmarkForPage(fileUri, pageNumber)
    }

    override suspend fun isPageBookmarked(fileUri: String, pageNumber: Int): Boolean {
        return bookmarkDao.isPageBookmarked(fileUri, pageNumber)
    }
}
