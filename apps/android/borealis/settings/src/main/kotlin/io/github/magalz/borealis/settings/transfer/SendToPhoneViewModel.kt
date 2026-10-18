package io.github.magalz.borealis.settings.transfer

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.magalz.borealis.diagnostics.transfer.TransferSession
import io.github.magalz.borealis.diagnostics.transfer.TransferState
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** Gives the dialog the app's one [TransferSession] as a [TransferController]. */
@HiltViewModel
class SendToPhoneViewModel @Inject constructor(session: TransferSession) : ViewModel() {
    val transfer: TransferController =
        object : TransferController {
            override val state: StateFlow<TransferState>
                get() = session.state

            override fun start(file: File) = session.start(file)

            override fun stop() = session.stop()
        }
}
