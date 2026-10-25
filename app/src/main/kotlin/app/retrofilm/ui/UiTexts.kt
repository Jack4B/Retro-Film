package app.retrofilm.ui

import android.content.Context
import app.retrofilm.R

/** Tłumaczenie [UiMessage] na tekst z strings.xml. */
class UiTexts private constructor(private val context: Context) {
    fun of(msg: UiMessage): String = when (msg) {
        UiMessage.SignatureTooLong -> context.getString(R.string.signature_too_long)
        UiMessage.Saved -> context.getString(R.string.export_done)
        is UiMessage.SavedMany -> if (msg.failed == 0) {
            context.getString(R.string.export_many_done, msg.saved)
        } else {
            context.getString(R.string.export_many_partial, msg.saved, msg.failed)
        }
        UiMessage.LoadFailed -> context.getString(R.string.load_failed)
        is UiMessage.ExportFailed -> context.getString(R.string.export_failed, msg.reason)
    }

    companion object {
        fun from(context: Context) = UiTexts(context)
    }
}
