package dev.krystian.wakewifi

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainViewModelTest {

    @Test
    fun testInitialState() {
        val viewModel = MainViewModel()
        val state = viewModel.uiState.value
        assertFalse(state.isMonitoring)
        assertFalse(state.isWifiConnected)
        assertFalse(state.isAlarmRinging)
    }

    @Test
    fun testSetMonitoring() {
        val viewModel = MainViewModel()
        viewModel.setMonitoring(true)
        assertTrue(viewModel.uiState.value.isMonitoring)

        viewModel.setMonitoring(false)
        assertFalse(viewModel.uiState.value.isMonitoring)
    }
}
