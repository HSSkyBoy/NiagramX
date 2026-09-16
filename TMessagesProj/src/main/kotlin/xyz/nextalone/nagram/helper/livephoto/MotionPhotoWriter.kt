package xyz.nextalone.nagram.helper.livephoto

import android.os.Build
import java.io.File

// https://github.com/bjzhou/PhotonCamera/blob/main/app/src/main/java/com/hinnka/mycamera/livephoto/MotionPhotoWriter.kt
object MotionPhotoWriter {

    fun getCreator(): LivePhotoCreator {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") ->
                OplusLivePhotoCreator()
            manufacturer.contains("huawei") || manufacturer.contains("honor") ->
                LegacyLivePhotoCreator()
            else -> GoogleLivePhotoCreator()
        }
    }

    fun write(
        jpegPath: String,
        videoPath: String,
        outputPath: String,
        presentationTimestampUs: Long = 0,
    ): Boolean {
        if (!File(jpegPath).exists()) return false
        if (!File(videoPath).exists()) return false
        return getCreator().create(jpegPath, videoPath, outputPath, presentationTimestampUs)
    }
}
