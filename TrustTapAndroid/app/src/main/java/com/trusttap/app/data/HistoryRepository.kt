package com.trusttap.app.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.gson.Gson
import com.trusttap.app.model.AnalysisResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class HistoryRepository private constructor(private val context: Context) {
    private val dao = TrustTapDatabase.get(context).historyDao()
    private val gson = Gson()
    private val crypto = HistoryCrypto.get(context)

    fun observeAll(): Flow<List<HistoryEntity>> = dao.observeAll()

    suspend fun save(uri: Uri, response: AnalysisResponse, claimedCaption: String?) =
        withContext(Dispatchers.IO) {
            val isVideo = context.contentResolver.getType(uri)?.startsWith("video/") == true
            val thumbnailPath = createThumbnail(uri, isVideo)
            dao.insert(
                HistoryEntity(
                    mediaType = if (isVideo) "Video" else "Image",
                    thumbnailPath = thumbnailPath,
                    createdAt = System.currentTimeMillis(),
                    claimedCaption = claimedCaption?.trim()?.ifBlank { null },
                    responseJson = crypto.encrypt(gson.toJson(response))
                )
            )
        }

    suspend fun saveText(response: AnalysisResponse, text: String) =
        withContext(Dispatchers.IO) {
            dao.insert(
                HistoryEntity(
                    mediaType = "Text",
                    thumbnailPath = null,
                    createdAt = System.currentTimeMillis(),
                    claimedCaption = text.trim().ifBlank { null },
                    responseJson = crypto.encrypt(gson.toJson(response))
                )
            )
        }

    suspend fun delete(entry: HistoryEntity) = withContext(Dispatchers.IO) {
        dao.deleteById(entry.id)
        entry.thumbnailPath?.let { File(it).delete() }
    }

    fun decodeResponse(entry: HistoryEntity): AnalysisResponse =
        gson.fromJson(crypto.decrypt(entry.responseJson), AnalysisResponse::class.java)

    private fun createThumbnail(uri: Uri, isVideo: Boolean): String? {
        val bitmap = if (isVideo) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (_: Exception) {
                null
            } finally {
                retriever.release()
            }
        } else {
            context.contentResolver.openInputStream(uri).use { input ->
                input?.let { BitmapFactory.decodeStream(it) }
            }
        } ?: return null

        val historyDir = File(context.filesDir, "history-thumbnails").apply { mkdirs() }
        val output = File.createTempFile("thumb_", ".jpg", historyDir)
        FileOutputStream(output).use { stream ->
            val scaled = scaleDown(bitmap, 720)
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, stream)
            if (scaled !== bitmap) scaled.recycle()
        }
        bitmap.recycle()
        return output.absolutePath
    }

    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / largest.toFloat()
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    companion object {
        @Volatile private var instance: HistoryRepository? = null

        fun get(context: Context): HistoryRepository =
            instance ?: synchronized(this) {
                instance ?: HistoryRepository(context.applicationContext).also { instance = it }
            }
    }
}
