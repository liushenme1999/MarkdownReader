package space.liushenme.markdownreader.ui.screens.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderUiStateTest {

    @Test
    fun loadError_isNullForNonFailureStates() {
        assertNull(ReaderUiState(loadState = ReaderLoadState.Loading).loadError)
        assertNull(ReaderUiState(loadState = ReaderLoadState.Empty).loadError)
        assertNull(ReaderUiState(loadState = ReaderLoadState.Ready).loadError)
    }

    @Test
    fun loadError_exposesSpecificRecoveryMessage() {
        val states = listOf(
            ReaderLoadState.MissingSource("missing"),
            ReaderLoadState.BrokenBundle("broken"),
            ReaderLoadState.NetworkRequired("network"),
            ReaderLoadState.UnsupportedFormat("unsupported"),
            ReaderLoadState.PermissionDenied("permission"),
            ReaderLoadState.Failed("failed"),
        )

        assertEquals(
            listOf("missing", "broken", "network", "unsupported", "permission", "failed"),
            states.map { ReaderUiState(loadState = it).loadError },
        )
    }
}
