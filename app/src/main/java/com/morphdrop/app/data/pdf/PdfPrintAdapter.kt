package com.morphdrop.app.data.pdf

import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.File
import java.io.IOException

class PdfPrintAdapter(private val file: File, private val jobName: String) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }

        val info = PrintDocumentInfo.Builder(jobName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .build()

        callback?.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        var input: FileInputStream? = null
        var output: FileOutputStream? = null

        try {
            input = FileInputStream(file)
            output = FileOutputStream(destination?.fileDescriptor)

            val buf = ByteArray(16384)
            var size: Int
            while (input.read(buf).also { size = it } >= 0 && cancellationSignal?.isCanceled == false) {
                output.write(buf, 0, size)
            }

            if (cancellationSignal?.isCanceled == true) {
                callback?.onWriteCancelled()
            } else {
                callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            }
        } catch (e: IOException) {
            callback?.onWriteFailed(e.message)
        } finally {
            try {
                input?.close()
                output?.close()
            } catch (e: IOException) {
                // Ignore
            }
        }
    }
}
