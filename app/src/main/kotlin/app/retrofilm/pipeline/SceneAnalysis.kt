package app.retrofilm.pipeline

import kotlin.math.hypot

/** Prostokąt twarzy w współrzędnych znormalizowanych do obrazu (0..1), np. z wykrywacza twarzy. */
data class FaceBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Rodzaj sceny rozpoznany przez [SceneAnalyzer]; steruje ostrożnością [AutoEnhance]. */
enum class AutoScene { General, Portrait, Landscape, Dominant }

/** Siatka próbek obrazu: liniowe RGB (trójki) w układzie [gw]×[gh]. */
class Sample(val rgb: FloatArray, val gw: Int, val gh: Int) {
    val count: Int get() = gw * gh
}

/**
 * Maski próbek: [skin], [sky] oraz [usable] – próbki nadające się do szacowania oświetlenia (bez skóry, nieba,
 * roślin i mocno nasyconych obiektów).
 */
class SceneMasks(val skin: BooleanArray, val sky: BooleanArray, val usable: BooleanArray)

/** Chroma i jasność OKLab każdej próbki (przed korektami). */
class SceneColors(val chroma: FloatArray, val lightness: FloatArray)

/**
 * Wynik analizy sceny. [dominance] 0..1 mówi, jaka część barwności sceny przypada na jeden zakres barw (zachód
 * słońca, las, ściana w jednym kolorze).
 */
class SceneAnalysis(
    val scene: AutoScene,
    val masks: SceneMasks,
    val colors: SceneColors,
    val dominance: Float,
    val hasFaces: Boolean
) {
    val skin: BooleanArray get() = masks.skin
    val sky: BooleanArray get() = masks.sky
    val usable: BooleanArray get() = masks.usable
    val skinRatio: Float get() = ratio(masks.skin)
    val skyRatio: Float get() = ratio(masks.sky)
    val usableRatio: Float get() = ratio(masks.usable)

    private fun ratio(m: BooleanArray): Float = if (m.isEmpty()) 0f else m.count { it } / m.size.toFloat()
}

/**
 * Rozpoznawanie sceny bez uczenia maszynowego, w przestrzeni OKLab (Ottosson), gdzie odcień skóry, nieba i zieleni
 * zajmują wąskie, stabilne przedziały kątów:
 * - skóra: odcień 28–72°, umiarkowana chroma, średnia jasność; jeśli podano twarze, tylko wnętrze ramek twarzy
 *   (60% szerokości i 70% wysokości, bez włosów i tła), inaczej cały obraz, ale wtedy kolor skóry jest tylko
 *   wskazówką: neutralne powierzchnie w ciepłym świetle mają podobny odcień, więc taka maska nie wyklucza pikseli z
 *   szacowania oświetlenia, a obraz w całości „skórzany” (ponad 60%) nie jest uznawany za skórę;
 * - niebo: odcień 205–275°, jasne, w górnych 60% kadru; rośliny: odcień 105–175° z wyraźną chromą;
 * - dominanta: udział najliczniejszego z 12 zakresów barw w łącznej chromie pikseli barwnych.
 */
@Suppress("MagicNumber") // przedziały kątów i progi chromy są danymi tej heurystyki
object SceneAnalyzer {
    private const val SKIN_FROM = 28f
    private const val SKIN_TO = 72f
    private const val SKY_FROM = 205f
    private const val SKY_TO = 275f
    private const val GREEN_FROM = 105f
    private const val GREEN_TO = 175f
    private const val HUE_BINS = 12
    private const val HUE_BIN_WIDTH = 360f / HUE_BINS
    private const val COLORFUL = 0.05f
    private const val FACE_W = 0.6f
    private const val FACE_H = 0.7f
    private const val PORTRAIT_SKIN = 0.04f
    private const val LANDSCAPE_SHARE = 0.25f
    private const val DOMINANT = 0.6f
    private const val COLOR_SKIN_MAX = 0.6f

    fun analyze(s: Sample, faces: List<FaceBox> = emptyList()): SceneAnalysis {
        val colors = oklab(s)
        val hue = hues(s)
        val n = s.count
        val skin = BooleanArray(n)
        val sky = BooleanArray(n)
        val green = BooleanArray(n)
        for (i in 0 until n) {
            val c = colors.chroma[i]
            val l = colors.lightness[i]
            val gy = i / s.gw
            skin[i] = isSkin(l, c, hue[i]) && inFaces(faces, i % s.gw, gy, s)
            sky[i] = hue[i] in SKY_FROM..SKY_TO && c >= 0.025f && l >= 0.5f && gy < s.gh * 0.6f
            green[i] = hue[i] in GREEN_FROM..GREEN_TO && c >= 0.04f
        }
        if (faces.isEmpty() && skin.count { it } > n * COLOR_SKIN_MAX) skin.fill(false)
        // bez twarzy odcień skóry nie wyklucza pikseli z szacowania oświetlenia (ciepłe szarości wyglądają tak samo)
        val usable = BooleanArray(n) {
            (faces.isEmpty() || !skin[it]) && !sky[it] && !green[it] && colors.chroma[it] < 0.12f
        }
        val dominance = dominance(colors.chroma, hue)
        val scene =
            classify(
                skin.count {
                    it
                } / n.toFloat(),
                (sky.count { it } + green.count { it }) / n.toFloat(),
                dominance,
                faces.isNotEmpty()
            )
        return SceneAnalysis(scene, SceneMasks(skin, sky, usable), colors, dominance, faces.isNotEmpty())
    }

    private fun classify(skinShare: Float, skyGreen: Float, dominance: Float, faces: Boolean): AutoScene = when {
        skinShare >= PORTRAIT_SKIN && (faces || skinShare >= 0.08f) -> AutoScene.Portrait
        skyGreen >= LANDSCAPE_SHARE -> AutoScene.Landscape
        dominance >= DOMINANT -> AutoScene.Dominant
        else -> AutoScene.General
    }

    /** Jasność i chroma OKLab każdej próbki. */
    private fun oklab(s: Sample): SceneColors {
        val lab = OkLab()
        val rgb = FloatArray(3)
        val light = FloatArray(s.count)
        val chroma = FloatArray(s.count)
        for (i in 0 until s.count) {
            for (k in 0..2) rgb[k] = AdjustmentProcessor.linearToSrgb(s.rgb[3 * i + k].coerceIn(0f, 1f))
            lab.fromRgb(rgb)
            light[i] = lab.lab[0]
            chroma[i] = hypot(lab.lab[1], lab.lab[2])
        }
        return SceneColors(chroma, light)
    }

    /** Kąt barwy OKLab (stopnie) każdej próbki. */
    private fun hues(s: Sample): FloatArray {
        val lab = OkLab()
        val rgb = FloatArray(3)
        return FloatArray(s.count) { i ->
            for (k in 0..2) rgb[k] = AdjustmentProcessor.linearToSrgb(s.rgb[3 * i + k].coerceIn(0f, 1f))
            lab.fromRgb(rgb)
            OkLab.hueDegrees(lab.lab[1], lab.lab[2])
        }
    }

    private fun isSkin(l: Float, c: Float, h: Float): Boolean =
        h in SKIN_FROM..SKIN_TO && c in 0.04f..0.15f && l in 0.3f..0.95f

    /** Bez twarzy cały obraz może zawierać skórę; z twarzami tylko środek ramki. */
    private fun inFaces(faces: List<FaceBox>, gx: Int, gy: Int, s: Sample): Boolean {
        if (faces.isEmpty()) return true
        val x = (gx + 0.5f) / s.gw
        val y = (gy + 0.5f) / s.gh
        return faces.any {
            val cx = (it.left + it.right) / 2f
            val cy = (it.top + it.bottom) / 2f
            val hw = (it.right - it.left) * FACE_W / 2f
            val hh = (it.bottom - it.top) * FACE_H / 2f
            x in cx - hw..cx + hw && y in cy - hh..cy + hh
        }
    }

    private fun dominance(chroma: FloatArray, hue: FloatArray): Float {
        val bins = FloatArray(HUE_BINS)
        var total = 0f
        for (i in chroma.indices) {
            if (chroma[i] < COLORFUL) continue
            bins[(hue[i] / HUE_BIN_WIDTH).toInt().coerceIn(0, HUE_BINS - 1)] += chroma[i]
            total += chroma[i]
        }
        // za mało barwnych pikseli, by mówić o dominancie
        if (total < chroma.size * 0.02f * COLORFUL * 2f) return 0f
        // sąsiednie zakresy liczymy razem, bo jedna barwa rozlewa się na dwa przedziały
        var best = 0f
        for (b in 0 until HUE_BINS) best = maxOf(best, bins[b] + bins[(b + 1) % HUE_BINS])
        return (best / total).coerceIn(0f, 1f)
    }
}
