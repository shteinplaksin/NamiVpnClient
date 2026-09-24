package io.github.hhwkart.nami.ui.compose

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationBusTest {

    @Test
    fun repeatedHomeRequestsRemainIndependentEvents() = runTest {
        NavigationBus.open(Destination.Home)
        assertEquals(Destination.Home, NavigationBus.destination.first())

        NavigationBus.open(Destination.Home)
        assertEquals(Destination.Home, NavigationBus.destination.first())
    }
}
