package com.pietrondo.aquariummanager.presentation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Shows a stored photo or the bytes just picked in the editor, decoded off the main thread. */
@Composable
fun PhotoImage(
    bytes: ByteArray?,
    file: File?,
    modifier: Modifier = Modifier,
    maxDimension: Int = 512,
) {
    val image by produceState<ImageBitmap?>(
        initialValue = null,
        bytes,
        file?.absolutePath,
        maxDimension,
    ) {
        value = withContext(Dispatchers.IO) {
            val bitmap = if (bytes != null) {
                decode(bytes, maxDimension)
            } else {
                file?.let { decode(it, maxDimension) }
            }
            bitmap?.asImageBitmap()
        }
    }
    image?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}

private fun decode(bytes: ByteArray, maxDimension: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val sample = sampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
    return BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
    )
}

private fun decode(file: File, maxDimension: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val sample = sampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
    return BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply { inSampleSize = sample },
    )
}

private fun sampleSize(width: Int, height: Int, maxDimension: Int): Int {
    var sample = 1
    while (maxOf(width / sample, height / sample) > maxDimension) sample *= 2
    return sample
}
