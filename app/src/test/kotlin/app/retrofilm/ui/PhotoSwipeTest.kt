package app.retrofilm.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoSwipeTest {
    private val width = 1000f

    @Test fun swipeLeftShowsNextAndRightShowsPrevious() {
        assertEquals(1, swipeStep(-300f, 10f, width, multiTouch = false))
        assertEquals(-1, swipeStep(300f, -10f, width, multiTouch = false))
    }

    @Test fun shortSwipeIsIgnored() {
        assertEquals(0, swipeStep(-150f, 0f, width, multiTouch = false))
        assertEquals(0, swipeStep(150f, 0f, width, multiTouch = false))
    }

    @Test fun verticalOrDiagonalSwipeIsIgnored() {
        assertEquals(0, swipeStep(-300f, 400f, width, multiTouch = false))
        assertEquals(0, swipeStep(300f, 200f, width, multiTouch = false))
    }

    @Test fun pinchIsNeverASwipe() {
        assertEquals(0, swipeStep(-500f, 0f, width, multiTouch = true))
    }
}
