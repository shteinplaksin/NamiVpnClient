package io.github.hhwkart.nami.ui.compose

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationBusTest {

    @Test
    fun repeatedHomeRequestsRemainIndependentEvents() = runTest {
        NavigationBus.open(Destination.Home)
        assertEquals(NavigationRequest.Open(Destination.Home), NavigationBus.requests.first())

        NavigationBus.open(Destination.Home)
        assertEquals(NavigationRequest.Open(Destination.Home), NavigationBus.requests.first())
    }

    @Test
    fun returnRequestIsDistinctFromOpeningADestination() = runTest {
        NavigationBus.returnTo(Destination.Settings)

        assertEquals(
            NavigationRequest.ReturnTo(Destination.Settings),
            NavigationBus.requests.first(),
        )
    }

    @Test
    fun restoredLegacyFlowCanReturnByPoppingItsTransientScreens() = runTest {
        NavigationBus.returnToPrevious(screenCount = 2)

        assertEquals(
            NavigationRequest.ReturnToPrevious(screenCount = 2),
            NavigationBus.requests.first(),
        )
    }
}
