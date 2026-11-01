package app.revanced.patches.reddit.customclients.sync.syncforreddit.posttext

import app.revanced.patcher.extensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.extensions.removeInstructions
import app.revanced.patcher.extensions.replaceInstruction
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val SHEET =
    "Lcom/laurencedawson/reddit_sync/ui/fragment_dialogs/bottom/PostMoreBottomSheetFragment;"

private const val SHEET_BINDING =
    "Lcom/laurencedawson/reddit_sync/ui/fragment_dialogs/bottom/PostMoreBottomSheetFragment_ViewBinding;"

private const val SHEET_BASE =
    "Lcom/laurencedawson/reddit_sync/ui/fragment_dialogs/bottom/material_dialogs/base/AbstractSelectionDialogBottomSheet;"

private const val ROW = "Lcom/laurencedawson/reddit_sync/ui/views/core/MaterialRow;"

private const val ITEM =
    "Lcom/laurencedawson/reddit_sync/ui/fragment_dialogs/bottom/material_dialogs/base/AbstractSelectionDialogBottomSheet\$h;"

private const val DIALOG_BUILDER = "Landroidx/appcompat/app/b\$a;"

private const val DIALOG = "Landroidx/appcompat/app/b;"

private const val REDDIT_HELPER = "Ld7/f;"

private const val MODEL = "Lxa/d;"

private const val INFLATE =
    "Landroid/view/View;->inflate(Landroid/content/Context;ILandroid/view/ViewGroup;)Landroid/view/View;"

private const val FIND_VIEW = "Landroid/view/View;->findViewById(I)Landroid/view/View;"

private const val HTML_TO_TEXT = "$REDDIT_HELPER->j(Ljava/lang/String;)Ljava/lang/CharSequence;"

private const val ACCESSOR = "$MODEL->N0()Ljava/lang/String;"

private const val COPY_TO_CLIPBOARD =
    "$REDDIT_HELPER->b(Ljava/lang/CharSequence;Landroid/content/Context;)V"

/** `android.R.id.button1`, the positive button of an appcompat alert dialog. */
private const val ANDROID_BUTTON_POSITIVE = 0x01020019

/** Marks the row this patch adds, so the sheet can tell it apart from the rows it declares in XML. */
private const val ROW_TAG = "sync_select_text"

private val Instruction.methodRef: String?
    get() = ((this as? ReferenceInstruction)?.reference as? MethodReference)?.let {
        "${it.definingClass}->${it.name}(${it.parameterTypes.joinToString("")})${it.returnType}"
    }

private val Instruction.stringLiteral: String?
    get() = ((this as? ReferenceInstruction)?.reference as? StringReference)?.string

private fun ClassDef.instructions(): List<Instruction> =
    methods.flatMap { it.instructions() }

private fun Method.instructions(): List<Instruction> =
    implementation?.instructions?.toList() ?: emptyList()

private fun Iterable<ClassDef>.classContainingString(value: String): ClassDef =
    firstOrNull { classDef -> classDef.instructions().any { it.stringLiteral == value } }
        ?: throw PatchException("No class contains \"$value\"")

/**
 * Reads the resource id a call site passes to [callee] by taking the constant that loads its first
 * argument. Sync builds its selection dialogs in code, so the ids only exist at the call site.
 */
private fun Method.argumentFor(callee: String): Int {
    val instructions = instructions()
    val call = instructions.indexOfFirst { it.methodRef == callee }
    if (call < 0) throw PatchException("No call to $callee")

    for (index in call - 1 downTo 0) {
        val instruction = instructions[index]
        if (instruction is NarrowLiteralInstruction) return instruction.narrowLiteral
    }

    throw PatchException("No constant loaded before $callee")
}

@Suppress("unused")
val postTextActionsPatch = bytecodePatch(
    name = "Select and copy post text",
    description = "Adds Select text and Copy text to the original post, matching the comment actions.",
) {
    compatibleWith(
        "com.laurencedawson.reddit_sync",
        "com.laurencedawson.reddit_sync.pro",
        "com.laurencedawson.reddit_sync.dev",
    )

    apply {
        val sheet = classDefs.getOrReplaceMutable(
            classDefs.firstOrNull { it.type == SHEET } ?: throw PatchException("No $SHEET"),
        )

        // The comment sheet builds its items in code and is the only place that shows how Sync
        // lays out a selectable post body.
        val commentSheet = classDefs.getOrReplaceMutable(classDefs.classContainingString("Select text..."))
        val selectDialog = commentSheet.methods.first { method ->
            method.instructions().any { it.stringLiteral == "Select text..." }
        }
        val dialogLayout = selectDialog.argumentFor(INFLATE)
        val selectView = selectDialog.argumentFor(FIND_VIEW)

        val copyDialog = classDefs.getOrReplaceMutable(classDefs.classContainingString("Copy selftext"))

        // Every row of the sheet gets an anonymous ButterKnife listener that only forwards the row
        // back to the fragment. Reuse one for the row added here instead of adding a class.
        val rowListener = classDefs.firstOrNull { classDef ->
            classDef.type != SHEET &&
                classDef.methods.any { it.name == "<init>" && it.parameterTypes.lastOrNull() == SHEET } &&
                classDef.instructions().any { it.methodRef == "$SHEET->onItemClicked(Landroid/view/View;)V" }
        } ?: throw PatchException("No row click listener for $SHEET")

        // The sheet lays out its rows in XML, so the new row has to be built and inserted here.
        val selectTextRow = """
            invoke-virtual {p0}, Landroidx/fragment/app/Fragment;->I0()Landroid/content/Context;
            move-result-object v0
            iget-object v1, p0, $SHEET->mShare:$ROW
            invoke-virtual {v1}, Landroid/view/View;->getParent()Landroid/view/ViewParent;
            move-result-object v1
            check-cast v1, Landroid/view/ViewGroup;
            if-eqz v1, :post_text_row_done
            invoke-virtual {v0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;
            move-result-object v2
            invoke-virtual {v0}, Landroid/content/Context;->getPackageName()Ljava/lang/String;
            move-result-object v3
            const-string v4, "more_copy"
            const-string v5, "id"
            invoke-virtual {v2, v4, v5, v3}, Landroid/content/res/Resources;->getIdentifier(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I
            move-result v4
            invoke-virtual {v1, v4}, Landroid/view/ViewGroup;->findViewById(I)Landroid/view/View;
            move-result-object v4
            if-eqz v4, :post_text_row_done
            invoke-virtual {v1, v4}, Landroid/view/ViewGroup;->indexOfChild(Landroid/view/View;)I
            move-result v5
            new-instance v6, $ROW
            invoke-direct {v6, v0}, $ROW-><init>(Landroid/content/Context;)V
            const-string v4, "outline_text_fields_24"
            const-string v0, "drawable"
            invoke-virtual {v2, v4, v0, v3}, Landroid/content/res/Resources;->getIdentifier(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I
            move-result v4
            invoke-virtual {v6, v4}, $ROW->d(I)V
            const-string v4, "Select text"
            invoke-virtual {v6, v4}, $ROW->k(Ljava/lang/CharSequence;)V
            const-string v4, "$ROW_TAG"
            invoke-virtual {v6, v4}, Landroid/view/View;->setTag(Ljava/lang/Object;)V
            new-instance v4, ${rowListener.type}
            const/4 v0, 0x0
            invoke-direct {v4, v0, p0}, ${rowListener.type}-><init>($SHEET_BINDING$SHEET)V
            invoke-virtual {v6, v4}, Landroid/view/View;->setOnClickListener(Landroid/view/View${'$'}OnClickListener;)V
            invoke-virtual {v1, v6, v5}, Landroid/view/ViewGroup;->addView(Landroid/view/View;I)V
            :post_text_row_done
            nop
        """.trimIndent()

        val postOptions = sheet.methods.firstOrNull { it.name == "o2" }
            ?: throw PatchException("No o2 in $SHEET")
        postOptions.addInstructionsWithLabels(postOptions.implementation!!.instructions.count() - 1, selectTextRow)

        // The sheet dispatches on the row id, and a row built at runtime has none, so the new row
        // and the button of the dialog it opens are recognized by their tags instead.
        val clickHandler = sheet.methods.firstOrNull { it.name == "onItemClicked" }
            ?: throw PatchException("No onItemClicked in $SHEET")
        clickHandler.addInstructionsWithLabels(
            0,
            """
                invoke-virtual {p1}, Landroid/view/View;->getTag()Ljava/lang/Object;
                move-result-object v0
                const-string v1, "$ROW_TAG"
                invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                move-result v1
                if-eqz v1, :post_text_select
                instance-of v1, v0, Landroid/util/Pair;
                if-eqz v1, :post_text_copy
                goto :post_text_dispatch
                :post_text_select
                invoke-virtual {p1}, Landroid/view/View;->getContext()Landroid/content/Context;
                move-result-object v0
                iget-object v1, p0, $SHEET->P0:$MODEL
                invoke-virtual {v1}, $ACCESSOR
                move-result-object v1
                invoke-static {v1}, $HTML_TO_TEXT
                move-result-object v1
                invoke-static {v1}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z
                move-result v2
                if-nez v2, :post_text_select_inflate
                new-instance v2, Ljava/lang/StringBuilder;
                invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V
                iget-object v1, p0, $SHEET->P0:$MODEL
                invoke-virtual {v1}, $MODEL->b1()Ljava/lang/String;
                move-result-object v1
                invoke-virtual {v2, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                const-string v1, "\n"
                invoke-virtual {v2, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                iget-object v1, p0, $SHEET->P0:$MODEL
                invoke-virtual {v1}, $MODEL->e1()Ljava/lang/String;
                move-result-object v1
                invoke-virtual {v2, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
                move-result-object v1
                :post_text_select_inflate
                const v2, 0x${dialogLayout.toUInt().toString(16)}
                const/4 v3, 0x0
                invoke-static {v0, v2, v3}, Landroid/view/View;->inflate(Landroid/content/Context;ILandroid/view/ViewGroup;)Landroid/view/View;
                move-result-object v2
                const v3, 0x${selectView.toUInt().toString(16)}
                invoke-virtual {v2, v3}, Landroid/view/View;->findViewById(I)Landroid/view/View;
                move-result-object v3
                check-cast v3, Landroid/widget/TextView;
                invoke-virtual {v3, v1}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V
                invoke-static {v0}, Lgb/a;->a(Landroid/content/Context;)$DIALOG_BUILDER
                move-result-object v1
                const-string v0, "Select text..."
                invoke-virtual {v1, v0}, $DIALOG_BUILDER->q(Ljava/lang/CharSequence;)$DIALOG_BUILDER
                move-result-object v1
                invoke-virtual {v1, v2}, $DIALOG_BUILDER->r(Landroid/view/View;)$DIALOG_BUILDER
                move-result-object v1
                const-string v0, "Copy"
                const/4 v2, 0x0
                invoke-virtual {v1, v0, v2}, $DIALOG_BUILDER->n(Ljava/lang/CharSequence;Landroid/content/DialogInterface${'$'}OnClickListener;)$DIALOG_BUILDER
                move-result-object v1
                invoke-virtual {v1}, $DIALOG_BUILDER->a()$DIALOG
                move-result-object v1
                invoke-virtual {v1}, Landroid/app/Dialog;->show()V
                const v0, 0x${ANDROID_BUTTON_POSITIVE.toUInt().toString(16)}
                invoke-virtual {v1, v0}, Landroid/app/Dialog;->findViewById(I)Landroid/view/View;
                move-result-object v0
                new-instance v2, Landroid/util/Pair;
                invoke-direct {v2, v1, v3}, Landroid/util/Pair;-><init>(Ljava/lang/Object;Ljava/lang/Object;)V
                invoke-virtual {v0, v2}, Landroid/view/View;->setTag(Ljava/lang/Object;)V
                new-instance v2, ${rowListener.type}
                const/4 v3, 0x0
                invoke-direct {v2, v3, p0}, ${rowListener.type}-><init>($SHEET_BINDING$SHEET)V
                invoke-virtual {v0, v2}, Landroid/view/View;->setOnClickListener(Landroid/view/View${'$'}OnClickListener;)V
                goto :post_text_dispatch
                :post_text_copy
                check-cast v0, Landroid/util/Pair;
                iget-object v1, v0, Landroid/util/Pair;->first:Ljava/lang/Object;
                check-cast v1, Landroid/app/Dialog;
                iget-object v0, v0, Landroid/util/Pair;->second:Ljava/lang/Object;
                check-cast v0, Landroid/widget/TextView;
                invoke-virtual {v0}, Landroid/widget/TextView;->getText()Ljava/lang/CharSequence;
                move-result-object v2
                invoke-virtual {p0}, Landroidx/fragment/app/Fragment;->I0()Landroid/content/Context;
                move-result-object v3
                invoke-static {v2, v3}, $COPY_TO_CLIPBOARD
                invoke-virtual {v1}, Landroid/app/Dialog;->dismiss()V
                return-void
                :post_text_dispatch
                nop
            """.trimIndent(),
        )

        // Sync offers the post body in the copy dialog on self posts only. Offer it on every post,
        // and fall back to the title and the link when a post has no body of its own.
        val rowItems = copyDialog.methods.firstOrNull { it.name == "A4" }
            ?: throw PatchException("No A4 in the copy dialog")
        rowItems.addInstructionsWithLabels(
            rowItems.implementation!!.instructions.count() - 1,
            """
                iget-object v0, p0, ${copyDialog.type}->Q0:$MODEL
                invoke-virtual {v0}, $MODEL->Y0()I
                move-result v0
                const/4 v1, 0x2
                if-eq v0, v1, :post_text_item_done
                iget-object v0, p0, ${copyDialog.type}->U0:$ITEM
                invoke-virtual {p0, v0}, $SHEET_BASE->t4($ITEM)$ITEM
                :post_text_item_done
                nop
            """.trimIndent(),
        )

        val oldLabel = rowItems.instructions().indexOfFirst { it.stringLiteral == "Copy selftext" }
        if (oldLabel < 0) throw PatchException("No Copy selftext item")
        val labelRegister = (rowItems.getInstruction(oldLabel) as OneRegisterInstruction).registerA
        rowItems.replaceInstruction(oldLabel, "const-string v$labelRegister, \"Copy text\"")

        val copyOnClick = copyDialog.methods.firstOrNull { method ->
            method.instructions().any { it.methodRef == HTML_TO_TEXT }
        } ?: throw PatchException("No click handler in the copy dialog")

        val instructions = copyOnClick.instructions()
        val htmlCall = instructions.indexOfFirst { it.methodRef == HTML_TO_TEXT }
        val clipboardCall = (htmlCall..instructions.lastIndex).firstOrNull {
            instructions[it].methodRef == COPY_TO_CLIPBOARD
        } ?: throw PatchException("No clipboard call after the body conversion")

        // The body conversion starts three instructions before the call that renders it.
        val replaceFrom = htmlCall - 3
        copyOnClick.removeInstructions(replaceFrom, clipboardCall - replaceFrom + 1)
        copyOnClick.addInstructionsWithLabels(
            replaceFrom,
            """
                iget-object p1, p0, ${copyDialog.type}->Q0:$MODEL
                invoke-virtual {p1}, $ACCESSOR
                move-result-object p1
                invoke-static {p1}, $HTML_TO_TEXT
                move-result-object p1
                invoke-static {p1}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z
                move-result v0
                if-nez v0, :post_text_copy_body
                new-instance v0, Ljava/lang/StringBuilder;
                invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V
                iget-object p1, p0, ${copyDialog.type}->Q0:$MODEL
                invoke-virtual {p1}, $MODEL->b1()Ljava/lang/String;
                move-result-object p1
                invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                const-string p1, "\n"
                invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                iget-object p1, p0, ${copyDialog.type}->Q0:$MODEL
                invoke-virtual {p1}, $MODEL->e1()Ljava/lang/String;
                move-result-object p1
                invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
                move-result-object p1
                :post_text_copy_body
                invoke-virtual {p0}, Landroidx/fragment/app/Fragment;->B0()Landroidx/fragment/app/FragmentActivity;
                move-result-object v0
                invoke-static {p1, v0}, $COPY_TO_CLIPBOARD
            """.trimIndent(),
        )
    }
}
