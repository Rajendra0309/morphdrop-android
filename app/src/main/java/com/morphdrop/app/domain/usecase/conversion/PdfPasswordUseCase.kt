package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.net.Uri
import com.morphdrop.app.util.FileHelper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

class PdfPasswordUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    enum class Action { ADD_PASSWORD, REMOVE_PASSWORD }

    sealed class PasswordException(message: String) : Exception(message) {
        class WrongPassword : PasswordException("Incorrect PDF password provided")
        class InvalidAction : PasswordException("Invalid action or missing password")
        class CorruptPdf : PasswordException("PDF file is corrupt or unreadable")
    }

    suspend operator fun invoke(
        pdfUri: Uri,
        action: Action,
        password: String,
        currentPassword: String? = null,
        outputFileName: String = "secured_${System.currentTimeMillis()}.pdf"
    ): Uri = withContext(Dispatchers.IO) {
        if (password.isEmpty()) throw PasswordException.InvalidAction()

        PDFBoxResourceLoader.init(context)

        val inputStream = FileHelper.readFileFromUri(context, pdfUri)

        val document = try {
            if (action == Action.REMOVE_PASSWORD) {
                PDDocument.load(inputStream, currentPassword ?: password)
            } else {
                PDDocument.load(inputStream)
            }
        } catch (e: Exception) {
            inputStream.close()
            if (e.message?.contains("password", ignoreCase = true) == true) {
                throw PasswordException.WrongPassword()
            }
            throw PasswordException.CorruptPdf()
        }

        try {
            when (action) {
                Action.ADD_PASSWORD -> {
                    val accessPermission = AccessPermission()
                    val spp = StandardProtectionPolicy(password, password, accessPermission)
                    spp.encryptionKeyLength = 128
                    spp.permissions = accessPermission
                    document.protect(spp)
                }
                Action.REMOVE_PASSWORD -> {
                    document.isAllSecurityToBeRemoved = true
                }
            }

            val baos = ByteArrayOutputStream()
            document.save(baos)
            FileHelper.saveToCache(context, outputFileName, baos.toByteArray())
        } finally {
            document.close()
            inputStream.close()
        }
    }
}
