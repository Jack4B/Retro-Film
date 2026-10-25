package app.retrofilm.pipeline

import androidx.annotation.FontRes
import androidx.annotation.StringRes
import app.retrofilm.R
import app.retrofilm.core.ui.R as UiR

/** Ręczne kroje podpisu (wszystkie SIL OFL, z polskimi znakami). */
enum class SignatureFont(@FontRes val res: Int, @StringRes val label: Int) {
    Caveat(UiR.font.caveat, R.string.signature_font_caveat),
    MarckScript(UiR.font.marck_script, R.string.signature_font_marck_script),
    IndieFlower(UiR.font.indie_flower, R.string.signature_font_indie_flower),
    Sacramento(UiR.font.sacramento, R.string.signature_font_sacramento)
}
