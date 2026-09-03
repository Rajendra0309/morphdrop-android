package com.morphdrop.app.data.local.dao

import androidx.room.*
import com.morphdrop.app.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE fileUri = :fileUri ORDER BY pageNumber ASC")
    fun getBookmarksForFile(fileUri: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE fileUri = :fileUri AND pageNumber = :pageNumber")
    suspend fun deleteBookmarkForPage(fileUri: String, pageNumber: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE fileUri = :fileUri AND pageNumber = :pageNumber)")
    suspend fun isPageBookmarked(fileUri: String, pageNumber: Int): Boolean
}
