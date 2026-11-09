package com.pietrondo.aquariummanager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File

private const val MAX_PHOTO_DIMENSION = 1600
private const val PHOTO_JPEG_QUALITY = 85

/**
 * Reads a picked image, applies its EXIF rotation, downsizes it and returns JPEG bytes.
 * Down at [MAX_PHOTO_DIMENSION] px so the database backup stays a few hundred KB per photo.
 */
fun readPhotoBytes(context: Context, uri: Uri): ByteArray? {
    val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > MAX_PHOTO_DIMENSION * 2) {
        sample *= 2
    }
    var bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply {
        inSampleSize = sample
    }) ?: return null

    val rotation = readExifRotation(context, raw)
    if (rotation != 0f) {
        val matrix = Matrix().apply { postRotate(rotation) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        bitmap = rotated
    }

    val scaled = scaleDown(bitmap, MAX_PHOTO_DIMENSION)
    if (scaled !== bitmap) bitmap.recycle()
    return ByteArrayOutputStream().use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, out)
        scaled.recycle()
        out.toByteArray()
    }
}

private fun readExifRotation(context: Context, raw: ByteArray): Float {
    val temp = File.createTempFile("photo", ".jpg", context.cacheDir)
    return try {
        temp.writeBytes(raw)
        val orientation = ExifInterface(temp.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (_: Exception) {
        0f
    } finally {
        temp.delete()
    }
}

private fun scaleDown(bitmap: Bitmap, max: Int): Bitmap {
    val largest = maxOf(bitmap.width, bitmap.height)
    if (largest <= max) return bitmap
    val ratio = max.toFloat() / largest
    return Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * ratio).toInt().coerceAtLeast(1),
        (bitmap.height * ratio).toInt().coerceAtLeast(1),
        true,
    )
}
