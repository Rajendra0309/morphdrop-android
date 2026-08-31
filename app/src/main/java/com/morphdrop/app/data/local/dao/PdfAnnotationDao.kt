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

    @Query("DELETE FROM pdf_annotations WHERE id = :id")
    suspend fun deleteAnnotation(id: String)
}
