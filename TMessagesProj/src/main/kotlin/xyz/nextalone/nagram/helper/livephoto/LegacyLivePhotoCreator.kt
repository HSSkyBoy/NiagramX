package xyz.nextalone.nagram.helper.livephoto

import org.telegram.messenger.FileLog
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

// Live Photo creator for Huawei/Honor devices using LIVE_ marker append format.
class LegacyLivePhotoCreator : LivePhotoCreator {

    override fun create(
        jpegPath: String,
        videoPath: String,
        outputPath: String,
        presentationTimestampUs: Long
    ): Boolean {
        try {
            val videoLength = File(videoPath).length()

            FileOutputStream(outputPath).use { fos ->
                val dos = DataOutputStream(BufferedOutputStream(fos))
                FileInputStream(jpegPath).use { it.copyTo(dos) }
                FileInputStream(videoPath).use { it.copyTo(dos) }
                val marker = "500:1046".padEnd(20) + "LIVE_${videoLength}".padEnd(20)
                dos.write(marker.toByteArray(StandardCharsets.UTF_8))
                dos.flush()
            }
            return true
        } catch (e: Exception) {
            FileLog.e("Failed to create Legacy Live Photo", e)
            return false
        }
    }
}
