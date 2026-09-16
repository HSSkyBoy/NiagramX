package xyz.nextalone.nagram.helper

import org.telegram.messenger.FileLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.MessageObject
import xyz.nextalone.nagram.helper.livephoto.MotionPhotoWriter
import java.io.File

object MotionPhotoHelper {

    sealed class MergeResult {
        data class Success(val outputPath: String) : MergeResult()
        data class Error(val errorMessage: String, val exception: Exception? = null) : MergeResult()
    }

    fun createMotionPhoto(currentAccount: Int, messageObject: MessageObject): MergeResult {
        if (!messageObject.isLivePhoto) {
            return MergeResult.Error("Not a live photo messageObject")
        }
        var imageFile = FileLoader.getInstance(currentAccount).getPathToMessage(messageObject.messageOwner, true, true)
        if (imageFile == null || !imageFile.exists()) {
            val normalImageFile = FileLoader.getInstance(currentAccount).getPathToMessage(messageObject.messageOwner)
            if (normalImageFile != null && normalImageFile.exists()) {
                imageFile = normalImageFile
            }
        }
        val video = messageObject.document
        var videoFile = FileLoader.getInstance(currentAccount).getPathToAttach(video)
        if (videoFile == null || !videoFile.exists()) {
            val cached = FileLoader.getInstance(currentAccount).getPathToAttach(video, true)
            if (cached != null && cached.exists()) {
                videoFile = cached
            }
        }
        var duration = 0.0
        for (attribute in video.attributes) {
            if (attribute.duration > 0) { duration = attribute.duration; break }
        }
        val presentationTimestampUs = (duration * 1_000_000 / 2).toLong()
        return createMotionPhoto(imageFile?.path ?: "", videoFile?.path ?: "", presentationTimestampUs)
    }

    fun createMotionPhoto(
        imagePath: String,
        videoPath: String,
        presentationTimestampUs: Long,
    ): MergeResult {
        val imageFile = File(imagePath)
        val videoFile = File(videoPath)

        if (!imageFile.exists() || imageFile.length() == 0L) return MergeResult.Error("Image file not found or empty: $imagePath")
        if (!videoFile.exists() || videoFile.length() == 0L) return MergeResult.Error("Video file not found or empty: $videoPath")

        val cacheDir = FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE)
        val targetDirectory = File(cacheDir, "MotionPhoto")
        if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
            return MergeResult.Error("Cannot create output dir: ${targetDirectory.absolutePath}")
        }

        val outputFile = File(targetDirectory, "${imageFile.nameWithoutExtension}.jpg")

        return try {
            val ok = MotionPhotoWriter.write(imageFile.absolutePath, videoFile.absolutePath, outputFile.absolutePath, presentationTimestampUs)
            if (ok && outputFile.exists()) {
                FileLog.d("MotionPhoto created: ${outputFile.absolutePath}")
                MergeResult.Success(outputFile.absolutePath)
            } else {
                MergeResult.Error("Failed to create MotionPhoto")
            }
        } catch (e: Exception) {
            FileLog.e("Failed to create MotionPhoto", e)
            MergeResult.Error("Failed to create MotionPhoto: ${e.message}", e)
        }
    }
}
