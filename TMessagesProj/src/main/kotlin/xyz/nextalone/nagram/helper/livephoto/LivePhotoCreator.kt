package xyz.nextalone.nagram.helper.livephoto

interface LivePhotoCreator {
    fun create(
        jpegPath: String,
        videoPath: String,
        outputPath: String,
        presentationTimestampUs: Long = 0
    ): Boolean
}
