package org.telegram.plugin

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register
import org.telegram.tasks.EmojiPackTask
import org.telegram.tasks.GenerateLottieMetadataAssetFileTask

class TelegramBuildAppPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val telegramModule = project.rootProject.findProject(":TMessagesProj") ?: project
        val androidComponents =
            project.extensions.findByType(AndroidComponentsExtension::class.java)
                ?: error("Apply com.android.application/library before org.telegram.build-app-plugin")

        androidComponents.onVariants { variant ->
            val suffix = variant.name.replaceFirstChar { it.uppercase() }
            val emojiTask = project.tasks.register<EmojiPackTask>("pack${suffix}Emoji") {
                emojiDir.set(telegramModule.layout.projectDirectory.dir("emoji"))
                outputDir.set(project.layout.buildDirectory.dir("generated/emojiAssets/${variant.name}"))
            }
            variant.sources.assets?.addGeneratedSourceDirectory(emojiTask, EmojiPackTask::outputDir)
        }

        androidComponents.onVariants { variant ->
            val suffix = variant.name.replaceFirstChar { it.uppercase() }
            val lottieTask = project.tasks.register<GenerateLottieMetadataAssetFileTask>("generate${suffix}LottieMeta") {
                runtimeSymbolList.set(variant.artifacts.get(SingleArtifact.RUNTIME_SYMBOL_LIST))
                outputDir.set(project.layout.buildDirectory.dir("generated/lottieMeta/${variant.name}/assets"))
                variant.sources.res?.all?.let { layers ->
                    rawResourceDirs.from(
                        telegramModule.fileTree("src/main/res") {
                            include("raw*/*.json")
                        },
                        project.fileTree("src/main/res") {
                            include("raw*/*.json")
                        }
                    )
                }
            }

            variant.sources.assets?.addGeneratedSourceDirectory(lottieTask, GenerateLottieMetadataAssetFileTask::outputDir)
        }
    }
}
