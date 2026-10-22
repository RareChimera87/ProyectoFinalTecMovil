package me.santiagoreina.parcheu.feature.chat

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import me.santiagoreina.parcheu.core.error.AppError
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiveErrorMappingTest {
    @Test
    fun permissionDeniedMapsToForbidden() {
        val e = FirebaseFirestoreException("denied", Code.PERMISSION_DENIED)
        assertEquals(AppError.Forbidden, liveErrorOf(e))
    }

    @Test
    fun otherFailuresMapToNetwork() {
        assertEquals(
            AppError.Network,
            liveErrorOf(FirebaseFirestoreException("off", Code.UNAVAILABLE)),
        )
        assertEquals(AppError.Network, liveErrorOf(java.io.IOException()))
    }
}
