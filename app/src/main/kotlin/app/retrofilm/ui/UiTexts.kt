package app.retrofilm.ui

import android.content.Context
import app.retrofilm.R

/** Tłumaczenie [UiMessage] na tekst z strings.xml. */
class UiTexts private constructor(private val context: Context) {
    fun of(msg: UiMessage): String = when (msg) {
        UiMessage.SignatureTooLong -> context.getString(R.string.signature_too_long)
        UiMessage.Saved -> context.getString(R.string.export_done)
        UiMessage.LoadFailed -> context.getString(R.string.load_failed)
        is UiMessage.ExportFailed -> context.getString(R.string.export_failed, msg.reason)
    }

    companion object {
        fun from(context: Context) = UiTexts(context)
    }
}
