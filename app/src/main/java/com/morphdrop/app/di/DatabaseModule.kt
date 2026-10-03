package com.morphdrop.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.morphdrop.app.data.local.MorphDropDatabase
import com.morphdrop.app.data.local.dao.BookmarkDao
import com.morphdrop.app.data.local.dao.FavoriteDao
import com.morphdrop.app.data.local.dao.HistoryDao
import com.morphdrop.app.data.local.dao.PdfAnnotationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Migration 4 -> 5:
     * (a) favorites: recreated with conversionTypeId as the primary key, copying
     *     existing rows deduplicated (earliest timestamp wins per conversionTypeId).
     * (b) bookmarks: duplicate (fileUri, pageNumber) rows deleted (lowest rowid
     *     kept), then the unique index matching BookmarkEntity is created.
     * (c) conversion_history: nullable conversionTypeId column added.
     */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS favorites_new " +
                    "(conversionTypeId TEXT NOT NULL, timestamp INTEGER NOT NULL, PRIMARY KEY(conversionTypeId))"
            )
            db.execSQL(
                "INSERT OR IGNORE INTO favorites_new (conversionTypeId, timestamp) " +
                    "SELECT conversionTypeId, timestamp FROM favorites ORDER BY timestamp ASC"
            )
            db.execSQL("DROP TABLE favorites")
            db.execSQL("ALTER TABLE favorites_new RENAME TO favorites")

            db.execSQL(
                "DELETE FROM bookmarks WHERE rowid NOT IN " +
                    "(SELECT MIN(rowid) FROM bookmarks GROUP BY fileUri, pageNumber)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_bookmarks_fileUri_pageNumber " +
                    "ON bookmarks (fileUri, pageNumber)"
            )

            db.execSQL("ALTER TABLE conversion_history ADD COLUMN conversionTypeId TEXT")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE conversion_history ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    @Provides
    @Singleton
    fun provideMorphDropDatabase(
        @ApplicationContext context: Context
    ): MorphDropDatabase {
        return Room.databaseBuilder(
            context,
            MorphDropDatabase::class.java,
            "morphdrop.db"
        ).addMigrations(MIGRATION_4_5, MIGRATION_5_6)
            .fallbackToDestructiveMigrationFrom(true, 1, 2, 3)
            .build()
    }

    @Provides
    fun provideHistoryDao(database: MorphDropDatabase): HistoryDao {
        return database.historyDao()
    }

    @Provides
    fun provideFavoriteDao(database: MorphDropDatabase): FavoriteDao {
        return database.favoriteDao()
    }

    @Provides
    fun provideBookmarkDao(database: MorphDropDatabase): BookmarkDao {
        return database.bookmarkDao()
    }

    @Provides
    fun providePdfAnnotationDao(database: MorphDropDatabase): PdfAnnotationDao {
        return database.pdfAnnotationDao()
    }
}
