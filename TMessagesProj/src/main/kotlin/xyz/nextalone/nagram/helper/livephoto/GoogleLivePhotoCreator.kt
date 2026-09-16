package xyz.nextalone.nagram.helper.livephoto

import org.telegram.messenger.FileLog
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

class GoogleLivePhotoCreator : LivePhotoCreator {

    override fun create(
        jpegPath: String,
        videoPath: String,
        outputPath: String,
        presentationTimestampUs: Long
    ): Boolean {
        try {
            val videoLength = File(videoPath).length()
            val xmpData = buildMotionPhotoXmp(videoLength, presentationTimestampUs)

            FileOutputStream(outputPath).use { output ->
                FileInputStream(jpegPath).use { jpegInput ->
                    if (!injectXmpToStream(jpegInput, output, xmpData)) return false
                }
                FileInputStream(videoPath).use { videoInput ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (videoInput.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
            return true
        } catch (e: Exception) {
            FileLog.e("Failed to create Google Motion Photo", e)
            return false
        }
    }

    private fun injectXmpToStream(input: FileInputStream, output: FileOutputStream, xmpData: ByteArray): Boolean {
        val bis = BufferedInputStream(input)
        val b1 = bis.read()
        val b2 = bis.read()
        if (b1 != 0xFF || b2 != 0xD8) {
            FileLog.e("Invalid JPEG: missing SOI marker")
            return false
        }
        output.write(b1)
        output.write(b2)

        val namespaceBytes = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(StandardCharsets.UTF_8)
        val segmentLength = 2 + namespaceBytes.size + xmpData.size
        if (segmentLength > 65535) {
            FileLog.e("XMP data too large")
            return false
        }
        output.write(0xFF); output.write(0xE1)
        output.write((segmentLength shr 8) and 0xFF)
        output.write(segmentLength and 0xFF)
        output.write(namespaceBytes)
        output.write(xmpData)

        val buffer = ByteArray(8192)
        var len: Int
        while (bis.read(buffer).also { len = it } != -1) {
            output.write(buffer, 0, len)
        }
        return true
    }

    private fun buildMotionPhotoXmp(videoLength: Long, presentationTimestampUs: Long): ByteArray {
        val xmp = """
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
                <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                    <rdf:Description
                        xmlns:Camera="http://ns.google.com/photos/1.0/camera/"
                        xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
                        Camera:MotionPhoto="1"
                        Camera:MotionPhotoVersion="1"
                        Camera:MotionPhotoPresentationTimestampUs="${'$'}presentationTimestampUs"
                        GCamera:MotionPhoto="1"
                        GCamera:MotionPhotoVersion="1"
                        GCamera:MotionPhotoPresentationTimestampUs="${'$'}presentationTimestampUs"
                        GCamera:MicroVideo="1"
                        GCamera:MicroVideoVersion="1"
                        GCamera:MicroVideoOffset="${'$'}videoLength"/>
                    <rdf:Description
                        xmlns:Container="http://ns.google.com/photos/1.0/container/"
                        xmlns:Item="http://ns.google.com/photos/1.0/container/item/">
                        <Container:Directory>
                            <rdf:Seq>
                                <rdf:li rdf:parseType="Resource">
                                    <Container:Item
                                        Item:Mime="image/jpeg"
                                        Item:Semantic="Primary"
                                        Item:Length="0"
                                        Item:Padding="0"/>
                                </rdf:li>
                                <rdf:li rdf:parseType="Resource">
                                    <Container:Item
                                        Item:Mime="video/mp4"
                                        Item:Semantic="MotionPhoto"
                                        Item:Length="${'$'}videoLength"/>
                                </rdf:li>
                            </rdf:Seq>
                        </Container:Directory>
                    </rdf:Description>
                </rdf:RDF>
            </x:xmpmeta>
        """.trimIndent()
        return xmp.toByteArray(StandardCharsets.UTF_8)
    }
}
