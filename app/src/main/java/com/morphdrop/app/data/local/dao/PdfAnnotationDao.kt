package com.morphdrop.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.morphdrop.app.data.local.entity.PdfAnnotationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfAnnotationDao {
    @Query("SELECT * FROM pdf_annotations WHERE pdfUri = :uri")
    fun getAnnotationsForPdf(uri: String): Flow<List<PdfAnnotationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: PdfAnnotationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotations(annotations: List<PdfAnnotationEntity>)

    @Query("DELETE FROM pdf_annotations WHERE id = :id")
    suspend fun deleteAnnotation(id: String)

    @Query("DELETE FROM pdf_annotations WHERE id IN (:ids)")
    suspend fun deleteAnnotations(ids: List<String>)

    @androidx.room.Transaction
    suspend fun saveAnnotationsAtomic(toDeleteIds: List<String>, toInsert: List<PdfAnnotationEntity>) {
        if (toDeleteIds.isNotEmpty()) {
            deleteAnnotations(toDeleteIds)
        }
        if (toInsert.isNotEmpty()) {
            insertAnnotations(toInsert)
        }
    }
}
