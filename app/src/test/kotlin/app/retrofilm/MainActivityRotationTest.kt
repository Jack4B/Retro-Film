package app.retrofilm

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Obrót telefonu nie może odtwarzać edytora ani kasować edycji (zgłoszenie z testów na telefonie). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityRotationTest {
    @Test fun activityHandlesOrientationAndSizeChangesItself() {
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            PackageManager.GET_META_DATA
        )
        val needed = ActivityInfo.CONFIG_ORIENTATION or ActivityInfo.CONFIG_SCREEN_SIZE or
            ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE or ActivityInfo.CONFIG_SCREEN_LAYOUT
        assertTrue("configChanges obejmuje obrót i rozmiar", info.configChanges and needed == needed)
    }
}
