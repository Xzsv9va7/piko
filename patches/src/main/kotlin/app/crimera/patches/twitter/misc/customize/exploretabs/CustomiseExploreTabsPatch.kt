/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.customize.exploretabs

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.CUSTOMISE_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private object CustomiseExploreTabsFingerprint : Fingerprint(
    definingClass = "JsonPageTabs;",
    filters =
        listOf(
            opcode(Opcode.NEW_INSTANCE),
        ),
)

private object ExploreTabLayoutOnLayoutFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/material/tabs/TabLayout;",
    name = "onLayout",
)

private object ExploreTabLayoutAttachFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/material/tabs/TabLayout;",
    name = "onAttachedToWindow",
)

context(BytecodePatchContext)
private inline fun applyOptionalHook(block: () -> Unit) {
    try {
        block()
    } catch (_: PatchException) {
    }
}

context(BytecodePatchContext)
private fun hookTabLayout(fingerprint: Fingerprint) {
    val method = fingerprint.method
    val returnVoid = method.instructions.last { it.opcode == Opcode.RETURN_VOID }.location.index
    method.addInstructions(
        returnVoid,
        """
        invoke-static {p0}, $CUSTOMISE_DESCRIPTOR;->onExploreTabLayoutLayout(Landroid/view/View;)V
        """.trimIndent(),
    )
}

@Suppress("unused")
val customiseExploreTabsPatch =
    bytecodePatch(
        name = "Customize explore tabs",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {

            val method = CustomiseExploreTabsFingerprint.method

            val instructions = method.instructions

            val index = instructions.first { it.opcode == Opcode.IGET_OBJECT }.location.index

            method.addInstructions(
                index + 1,
                """
                invoke-static {v1}, $CUSTOMISE_DESCRIPTOR;->exploretabs(Ljava/util/ArrayList;)Ljava/util/ArrayList;
                move-result-object v1
                """.trimIndent(),
            )
            enableSettings("exploreTabCustomisation")

            applyOptionalHook { hookTabLayout(ExploreTabLayoutOnLayoutFingerprint) }
            applyOptionalHook { hookTabLayout(ExploreTabLayoutAttachFingerprint) }
        }
    }
