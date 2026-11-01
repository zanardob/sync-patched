package app.revanced.patches.reddit.customclients.sync.syncforreddit.swipeback

import app.revanced.patcher.definingClass
import app.revanced.patcher.extensions.ExternalLabel
import app.revanced.patcher.extensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.firstMethodDeclaratively
import app.revanced.patcher.name
import app.revanced.patcher.parameterTypes
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.returnType

/**
 * Sync marks activities translucent by reflecting into hidden Activity methods. Those calls
 * fail silently on newer Android versions, so the window below the gesture stays opaque and
 * the swipe reveals a black screen until the transition finishes.
 */
@Suppress("unused")
val fixSwipeBackTransparencyPatch = bytecodePatch(
    name = "Fix swipe back transparency",
    description = "Shows the previous screen while swiping back from a post on newer Android versions.",
) {
    compatibleWith(
        "com.laurencedawson.reddit_sync",
        "com.laurencedawson.reddit_sync.pro",
        "com.laurencedawson.reddit_sync.dev",
    )

    apply {
        val convertFromTranslucent = firstMethodDeclaratively("Converting activity from translucent: ") {
            returnType("V")
            parameterTypes("Landroid/app/Activity;")
        }

        // Sync calls the reverse conversion from the same helper class. Match it by the hidden
        // method names it looks up, and fall back to its signature in case those strings move.
        val convertToTranslucent = runCatching {
            firstMethodDeclaratively("getActivityOptions", "convertToTranslucent") {
                name { this != convertFromTranslucent.name }
                returnType("V")
                parameterTypes("Landroid/app/Activity;")
            }
        }.getOrElse {
            firstMethodDeclaratively {
                definingClass(convertFromTranslucent.definingClass)
                name { this != convertFromTranslucent.name }
                returnType("V")
                parameterTypes("Landroid/app/Activity;")
            }
        }

        listOf(convertFromTranslucent to false, convertToTranslucent to true)
            .forEach { (method, translucent) ->
                val registerCount = method.implementation?.registerCount
                    ?: throw PatchException("${method.name} has no implementation")

                // v0 and v1 are locals, p0 holds the Activity.
                if (registerCount < 3) throw PatchException("${method.name} has no spare registers")

                method.addInstructionsWithLabels(
                    0,
                    """
                        sget v0, Landroid/os/Build${'$'}VERSION;->SDK_INT:I
                        const/16 v1, 0x1e
                        if-lt v0, v1, :legacy
                        const/4 v0, 0x${if (translucent) 1 else 0}
                        invoke-virtual {p0, v0}, Landroid/app/Activity;->setTranslucent(Z)Z
                        move-result v0
                        if-eqz v0, :legacy
                        return-void
                    """,
                    ExternalLabel("legacy", method.getInstruction(0)),
                )
            }
    }
}
