package app.revanced.patches.youtube.layout.player.playeroverlayfilter.patch

import app.revanced.extensions.findMutableMethodOf
import app.revanced.patcher.annotation.Description
import app.revanced.patcher.annotation.Name
import app.revanced.patcher.annotation.Version
import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.instruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchResult
import app.revanced.patcher.patch.PatchResultError
import app.revanced.patcher.patch.PatchResultSuccess
import app.revanced.patcher.patch.annotations.DependsOn
import app.revanced.patcher.patch.annotations.Patch
import app.revanced.patcher.util.smali.ExternalLabel
import app.revanced.patches.shared.annotation.YouTubeCompatibility
import app.revanced.patches.shared.patch.mapping.ResourceMappingPatch
import app.revanced.patches.youtube.misc.settings.resource.patch.SettingsPatch
import org.jf.dexlib2.Opcode
import org.jf.dexlib2.iface.instruction.formats.Instruction21c
import org.jf.dexlib2.iface.instruction.formats.Instruction31i

@Patch
@Name("hide-player-overlay-filter")
@Description("Remove the dark filter layer from the player's background.")
@DependsOn(
    [
        ResourceMappingPatch::class,
        SettingsPatch::class
    ]
)
@YouTubeCompatibility
@Version("0.0.1")
class PlayerOverlayFilterPatch : BytecodePatch() {

    // list of resource names to get the id of
    private val resourceIds = arrayOf(
        "scrim_overlay",
        "google_transparent"
    ).map { name ->
        ResourceMappingPatch.resourceMappings.single { it.name == name }.id
    }
    private var patchSuccessArray = Array(resourceIds.size) {false}

    override fun execute(context: BytecodeContext): PatchResult {
        context.classes.forEach { classDef ->
            classDef.methods.forEach { method ->
                with(method.implementation) {
                    this?.instructions?.forEachIndexed { index, instruction ->
                        when (instruction.opcode) {
                            Opcode.CONST -> {
                                when ((instruction as Instruction31i).wideLiteral) {
                                    resourceIds[0] -> { // player overlay filter
                                        val insertIndex = index + 3
                                        val invokeInstruction = instructions.elementAt(insertIndex)
                                        if (invokeInstruction.opcode != Opcode.CHECK_CAST) return@forEachIndexed

                                        val mutableMethod = context.proxy(classDef).mutableClass.findMutableMethodOf(method)
                                        val dummyRegister = (instructions.elementAt(index) as Instruction31i).registerA
                                        val viewRegister = (invokeInstruction as Instruction21c).registerA

                                        val transparent = resourceIds[1]

                                        mutableMethod.addInstructions(
                                            insertIndex + 1, """
                                                invoke-static {}, Lapp/revanced/integrations/patches/layout/PlayerLayoutPatch;->hidePlayerOverlayFilter()Z
                                                move-result v$dummyRegister
                                                if-eqz v$dummyRegister, :currentcolor
                                                const v$dummyRegister, $transparent
                                                invoke-virtual {v$viewRegister, v$dummyRegister}, Landroid/widget/ImageView;->setImageResource(I)V
                                            """, listOf(ExternalLabel("currentcolor", mutableMethod.instruction(insertIndex + 1)))
                                        )

                                        patchSuccessArray[0] = true;
                                        patchSuccessArray[1] = true;
                                    }
                                }
                            }
                            else -> return@forEachIndexed
                        }
                    }
                }
            }
        }

        val errorIndex: Int = patchSuccessArray.indexOf(false)

        if (errorIndex == -1) {
            /*
             * Add settings
             */
            SettingsPatch.addPreference(
                arrayOf(
                    "PREFERENCE: PLAYER_LAYOUT_SETTINGS",
                    "SETTINGS: HIDE_PLAYER_OVERLAY_FILTER"
                )
            )

            SettingsPatch.updatePatchStatus("hide-player-overlay-filter")

            return PatchResultSuccess()
        } else
            return PatchResultError("Instruction not found: $errorIndex")
    }
}
