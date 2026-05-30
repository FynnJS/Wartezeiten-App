package de.wartezeiten.app.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

class CacheHeadersInterceptorTest {
    @Test
    fun detailEndpointsVaryByParkHeader() {
        assertEquals(
            CachePolicy(TimeUnit.HOURS.toSeconds(24), "park"),
            cachePolicyForPath("/v1/openingtimes"),
        )
        assertEquals(
            CachePolicy(TimeUnit.MINUTES.toSeconds(5), "park"),
            cachePolicyForPath("/v1/crowdlevel"),
        )
    }

    @Test
    fun waitingTimesVaryByParkAndLanguageHeaders() {
        assertEquals(
            CachePolicy(TimeUnit.MINUTES.toSeconds(5), "park, language"),
            cachePolicyForPath("/v1/waitingtimes"),
        )
    }

    @Test
    fun unknownPathIsNotCached() {
        assertNull(cachePolicyForPath("/v1/unknown"))
    }
}
