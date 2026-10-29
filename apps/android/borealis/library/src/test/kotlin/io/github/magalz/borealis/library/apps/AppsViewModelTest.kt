package io.github.magalz.borealis.library.apps

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.library.domain.App
import io.github.magalz.borealis.library.domain.AppsSnapshot
import io.github.magalz.borealis.library.domain.Host
import io.github.magalz.borealis.library.domain.QuitResult
import io.github.magalz.borealis.library.domain.Reachability
import io.github.magalz.borealis.library.domain.RunningApp
import io.github.magalz.borealis.library.domain.StreamRequest
import io.github.magalz.borealis.library.testing.FakeAppRepository
import io.github.magalz.borealis.library.testing.FakeHostShortcuts
import io.github.magalz.borealis.library.testing.MainDispatcherRule
import io.github.magalz.borealis.library.testing.aHost
import io.github.magalz.borealis.library.testing.anApp
import io.github.magalz.borealis.library.testing.subscribe
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class AppsViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val repository = FakeAppRepository()
    private val shortcuts = FakeHostShortcuts(canPinApps = false)
    // Lazy: a ViewModel must be created after the rule has set the main dispatcher.
    private val viewModel by lazy { AppsViewModel("host-1", repository, shortcuts) }

    private val desktop = anApp(id = 1, name = "Desktop")
    private val steam = anApp(id = 2, name = "steam", hdrSupported = true)
    private val zelda = anApp(id = 3, name = "Zelda")
    private val secret = anApp(id = 4, name = "Secret", hidden = true)

    private fun snapshot(
        vararg apps: App,
        running: App? = null,
        host: Host? =
            aHost(
                id = "host-1",
                name = "Desk PC",
                runningApp = running?.let { RunningApp(it.id, it.name) },
            ),
        loading: Boolean = false,
    ) = AppsSnapshot(host = host, apps = apps.toList(), loading = loading)

    private fun item(app: App, running: Boolean = false) =
        AppItemUiState(app.id, app.name, running, app.hidden, app.hdrSupported)

    @Test
    fun `the apps of the host are requested`() = runTest {
        subscribe(viewModel.uiState)

        assertThat(repository.requestedHostIds).containsExactly("host-1")
    }

    @Test
    fun `the screen is loading until the first app list arrives`() = runTest {
        repository.emit(snapshot(loading = true))
        subscribe(viewModel.uiState)
        assertThat(viewModel.uiState.value.loading).isTrue()

        repository.emit(snapshot(desktop))
        runCurrent()

        assertThat(viewModel.uiState.value.loading).isFalse()
    }

    @Test
    fun `the host name and the running app id are in the state`() = runTest {
        repository.emit(snapshot(desktop, steam, running = steam))
        subscribe(viewModel.uiState)

        assertThat(viewModel.uiState.value.hostName).isEqualTo("Desk PC")
        assertThat(viewModel.uiState.value.runningAppId).isEqualTo(2)
    }

    @Test
    fun `a running app that is hidden is named in the state though it is not listed`() = runTest {
        val quiet = anApp(id = 5, name = "Quiet", hidden = true)
        repository.emit(snapshot(desktop, quiet, running = quiet))
        subscribe(viewModel.uiState)

        assertThat(viewModel.uiState.value.runningAppId).isEqualTo(5)
        assertThat(viewModel.uiState.value.runningAppName).isEqualTo("Quiet")
        assertThat(viewModel.uiState.value.items.map { it.id }).containsExactly(1)
    }

    @Test
    fun `no app is named when nothing runs`() = runTest {
        repository.emit(snapshot(desktop))
        subscribe(viewModel.uiState)

        assertThat(viewModel.uiState.value.runningAppName).isNull()
    }

    @Test
    fun `the running app comes first and the others follow by name ignoring case`() = runTest {
        repository.emit(snapshot(zelda, steam, desktop, running = zelda))
        subscribe(viewModel.uiState)

        assertThat(viewModel.uiState.value.items)
            .containsExactly(item(zelda, running = true), item(desktop), item(steam))
            .inOrder()
    }

    @Test
    fun `hidden apps are left out until the toggle shows them`() = runTest {
        repository.emit(snapshot(desktop, secret))
        subscribe(viewModel.uiState)
        assertThat(viewModel.uiState.value.items).containsExactly(item(desktop))

        viewModel.onToggleShowHidden()
        runCurrent()
        assertThat(viewModel.uiState.value.showHidden).isTrue()
        assertThat(viewModel.uiState.value.items)
            .containsExactly(item(desktop), item(secret))
            .inOrder()

        viewModel.onToggleShowHidden()
        runCurrent()
        assertThat(viewModel.uiState.value.items).containsExactly(item(desktop))
    }

    @Test
    fun `selecting an app when nothing runs starts it`() = runTest {
        repository.emit(snapshot(desktop, steam))
        subscribe(viewModel.uiState)

        viewModel.onAppSelected(2)

        viewModel.effects.test {
            assertThat(awaitItem())
                .isEqualTo(
                    AppsEffect.StartStream(StreamRequest("host-1", 2, "steam", hdrSupported = true))
                )
        }
    }

    @Test
    fun `selecting another app while one runs opens its actions instead of starting`() = runTest {
        repository.emit(snapshot(desktop, steam, running = desktop))
        subscribe(viewModel.uiState)

        viewModel.onAppSelected(2)
        runCurrent()

        assertThat(viewModel.uiState.value.actions?.app?.id).isEqualTo(2)
        viewModel.effects.test { expectNoEvents() }
    }

    @Test
    fun `selecting an app that is gone does nothing`() = runTest {
        repository.emit(snapshot(desktop))
        subscribe(viewModel.uiState)

        viewModel.onAppSelected(99)
        runCurrent()

        viewModel.effects.test { expectNoEvents() }
    }

    @Test
    fun `the running app offers resume, quit and details`() = runTest {
        repository.emit(snapshot(desktop, steam, running = desktop))
        subscribe(viewModel.uiState)

        viewModel.onActions(1)
        runCurrent()

        assertThat(viewModel.uiState.value.actions)
            .isEqualTo(
                AppActionsUiState(
                    item(desktop, running = true),
                    runningAppName = "Desktop",
                    choices = listOf(AppAction.RESUME, AppAction.QUIT, AppAction.DETAILS),
                )
            )
    }

    @Test
    fun `another app offers quit and start, details and hide while one runs`() = runTest {
        repository.emit(snapshot(desktop, steam, running = desktop))
        subscribe(viewModel.uiState)

        viewModel.onActions(2)
        runCurrent()

        assertThat(viewModel.uiState.value.actions)
            .isEqualTo(
                AppActionsUiState(
                    item(steam),
                    runningAppName = "Desktop",
                    choices = listOf(AppAction.QUIT_AND_START, AppAction.DETAILS, AppAction.HIDE),
                )
            )
    }

    @Test
    fun `an app offers start, details and hide when nothing runs`() = runTest {
        repository.emit(snapshot(desktop, steam))
        subscribe(viewModel.uiState)

        viewModel.onActions(2)
        runCurrent()

        assertThat(viewModel.uiState.value.actions)
            .isEqualTo(
                AppActionsUiState(
                    item(steam),
                    runningAppName = null,
                    choices = listOf(AppAction.START, AppAction.DETAILS, AppAction.HIDE),
                )
            )
    }

    @Test
    fun `a hidden app offers unhide instead of hide`() = runTest {
        repository.emit(snapshot(desktop, secret))
        subscribe(viewModel.uiState)
        viewModel.onToggleShowHidden()
        runCurrent()

        viewModel.onActions(4)
        runCurrent()

        assertThat(viewModel.uiState.value.actions?.choices)
            .containsExactly(AppAction.START, AppAction.DETAILS, AppAction.UNHIDE)
            .inOrder()
    }

    @Test
    fun `the actions follow the running app while the sheet is open`() = runTest {
        repository.emit(snapshot(desktop, steam))
        subscribe(viewModel.uiState)
        viewModel.onActions(2)
        runCurrent()

        repository.emit(snapshot(desktop, steam, running = desktop))
        runCurrent()

        assertThat(viewModel.uiState.value.actions?.choices?.first())
            .isEqualTo(AppAction.QUIT_AND_START)
    }

    @Test
    fun `the actions close when their app disappears`() = runTest {
        repository.emit(snapshot(desktop, steam))
        subscribe(viewModel.uiState)
        viewModel.onActions(2)
        runCurrent()

        repository.emit(snapshot(desktop))
        runCurrent()

        assertThat(viewModel.uiState.value.actions).isNull()
    }

    @Test
    fun `the sheet does not reopen when its app comes back`() = runTest {
        repository.emit(snapshot(desktop, steam))
        subscribe(viewModel.uiState)
        viewModel.onActions(2)
        runCurrent()
        repository.emit(snapshot(desktop))
        runCurrent()

        repository.emit(snapshot(desktop, steam))
        runCurrent()

        assertThat(viewModel.uiState.value.actions).isNull()
    }

    @Test
    fun `dismissing the actions clears them`() = runTest {
        repository.emit(snapshot(desktop))
        subscribe(viewModel.uiState)
        viewModel.onActions(1)
        runCurrent()

        viewModel.onActionsDismissed()
        runCurrent()

        assertThat(viewModel.uiState.value.actions).isNull()
    }

    @Test
    fun `resume and start start the stream and close the actions`() = runTest {
        repository.emit(snapshot(desktop, steam))
        subscribe(viewModel.uiState)
        viewModel.onActions(2)
        runCurrent()

        viewModel.onActionChosen(AppAction.START)
        runCurrent()

        assertThat(viewModel.uiState.value.actions).isNull()
        viewModel.effects.test {
            assertThat(awaitItem())
                .isEqualTo(
                    AppsEffect.StartStream(StreamRequest("host-1", 2, "steam", hdrSupported = true))
                )
        }
    }

    @Test
    fun `quit quits the host app and says so`() = runTest {
        repository.emit(snapshot(desktop, running = desktop))
        subscribe(viewModel.uiState)
        viewModel.onActions(1)
        runCurrent()

        viewModel.onActionChosen(AppAction.QUIT)

        viewModel.effects.test {
            assertThat(awaitItem()).isEqualTo(AppsEffect.ShowMessage(AppsMessage.QuitDone))
        }
        assertThat(repository.quitCalls).isEqualTo(1)
        assertThat(viewModel.uiState.value.actions).isNull()
    }

    @Test
    fun `a failed quit shows the adapter text and starts nothing`() = runTest {
        repository.quitResult = QuitResult.Failed("Host refused")
        repository.emit(snapshot(desktop, steam, running = desktop))
        subscribe(viewModel.uiState)
        viewModel.onActions(2)
        runCurrent()

        viewModel.onActionChosen(AppAction.QUIT_AND_START)

        viewModel.effects.test {
            assertThat(awaitItem())
                .isEqualTo(AppsEffect.ShowMessage(AppsMessage.QuitFailed("Host refused")))
            expectNoEvents()
        }
    }

    @Test
    fun `quit and start quits first and then starts the chosen app`() = runTest {
        repository.emit(snapshot(desktop, steam, running = desktop))
        subscribe(viewModel.uiState)
        viewModel.onActions(2)
        runCurrent()

        viewModel.onActionChosen(AppAction.QUIT_AND_START)

        viewModel.effects.test {
            assertThat(awaitItem())
                .isEqualTo(
                    AppsEffect.StartStream(StreamRequest("host-1", 2, "steam", hdrSupported = true))
                )
            expectNoEvents()
        }
        assertThat(repository.quitCalls).isEqualTo(1)
    }

    @Test
    fun `hide and unhide change the app on this device`() = runTest {
        repository.emit(snapshot(desktop, secret))
        subscribe(viewModel.uiState)
        viewModel.onToggleShowHidden()
        viewModel.onActions(1)
        runCurrent()
        viewModel.onActionChosen(AppAction.HIDE)
        viewModel.onActions(4)
        runCurrent()
        viewModel.onActionChosen(AppAction.UNHIDE)
        runCurrent()

        assertThat(repository.hiddenCalls)
            .containsExactly(Triple("host-1", 1, true), Triple("host-1", 4, false))
            .inOrder()
        assertThat(viewModel.uiState.value.actions).isNull()
    }

    @Test
    fun `details opens a dialog for the app until it is dismissed`() = runTest {
        repository.emit(snapshot(desktop, steam))
        subscribe(viewModel.uiState)
        viewModel.onActions(2)
        runCurrent()

        viewModel.onActionChosen(AppAction.DETAILS)
        runCurrent()
        assertThat(viewModel.uiState.value.details).isEqualTo(item(steam))
        assertThat(viewModel.uiState.value.actions).isNull()

        viewModel.onDetailsDismissed()
        runCurrent()
        assertThat(viewModel.uiState.value.details).isNull()
    }

    @Test
    fun `choosing an action with no sheet open does nothing`() = runTest {
        repository.emit(snapshot(desktop, running = desktop))
        subscribe(viewModel.uiState)

        viewModel.onActionChosen(AppAction.QUIT)
        runCurrent()

        assertThat(repository.quitCalls).isEqualTo(0)
    }

    @Test
    fun `an unknown host closes the screen`() = runTest {
        repository.emit(snapshot(host = null))
        subscribe(viewModel.uiState)

        viewModel.effects.test {
            assertThat(awaitItem()).isEqualTo(AppsEffect.Close(AppsMessage.HostUnknown))
        }
    }

    @Test
    fun `a host that is not paired closes the screen`() = runTest {
        repository.emit(snapshot(desktop, host = aHost(paired = false)))
        subscribe(viewModel.uiState)

        viewModel.effects.test {
            assertThat(awaitItem()).isEqualTo(AppsEffect.Close(AppsMessage.HostNotPaired))
        }
    }

    @Test
    fun `a host that is not polled yet and not paired keeps the screen open until it is online and paired`() =
        runTest {
            repository.emit(
                snapshot(desktop, host = aHost(reachability = Reachability.UNKNOWN, paired = false))
            )
            subscribe(viewModel.uiState)

            viewModel.effects.test { expectNoEvents() }

            repository.emit(
                snapshot(desktop, host = aHost(reachability = Reachability.ONLINE, paired = true))
            )
            runCurrent()

            assertThat(viewModel.uiState.value.items).containsExactly(item(desktop))
            viewModel.effects.test { expectNoEvents() }
        }

    @Test
    fun `an offline host that is not paired closes the screen as offline`() = runTest {
        repository.emit(
            snapshot(desktop, host = aHost(reachability = Reachability.OFFLINE, paired = false))
        )
        subscribe(viewModel.uiState)

        viewModel.effects.test {
            assertThat(awaitItem()).isEqualTo(AppsEffect.Close(AppsMessage.HostOffline))
        }
    }

    @Test
    fun `an action that is not in the open sheet is ignored`() = runTest {
        repository.emit(snapshot(desktop, steam))
        subscribe(viewModel.uiState)
        viewModel.onActions(2)
        runCurrent()
        val sheet = viewModel.uiState.value.actions

        viewModel.onActionChosen(AppAction.QUIT)
        viewModel.onActionChosen(AppAction.RESUME)
        runCurrent()

        assertThat(repository.quitCalls).isEqualTo(0)
        assertThat(viewModel.uiState.value.actions).isEqualTo(sheet)
        viewModel.effects.test { expectNoEvents() }
    }

    @Test
    fun `a host that goes offline closes the screen once`() = runTest {
        repository.emit(snapshot(desktop))
        subscribe(viewModel.uiState)

        repository.emit(snapshot(desktop, host = aHost(reachability = Reachability.OFFLINE)))
        runCurrent()
        repository.emit(
            snapshot(desktop, host = aHost(reachability = Reachability.OFFLINE, name = "Renamed"))
        )
        runCurrent()

        viewModel.effects.test {
            assertThat(awaitItem()).isEqualTo(AppsEffect.Close(AppsMessage.HostOffline))
            expectNoEvents()
        }
    }

    @Test
    fun `a host that is not polled yet keeps the screen open`() = runTest {
        repository.emit(snapshot(desktop, host = aHost(reachability = Reachability.UNKNOWN)))
        subscribe(viewModel.uiState)

        viewModel.effects.test { expectNoEvents() }
        assertThat(viewModel.uiState.value.items).containsExactly(item(desktop))
    }

    @Test
    fun `polling stops five seconds after the last collector leaves`() = runTest {
        val screen = subscribe(viewModel.uiState)
        assertThat(repository.activeCollectors).isEqualTo(1)

        screen.cancel()
        advanceTimeBy(4_999)
        runCurrent()
        assertThat(repository.activeCollectors).isEqualTo(1)

        advanceTimeBy(2)
        runCurrent()
        assertThat(repository.activeCollectors).isEqualTo(0)
    }

    @Test
    fun `a launch request starts the app as a tap does`() = runTest {
        repository.emit(snapshot(desktop, steam))
        subscribe(viewModel.uiState)

        viewModel.onLaunchRequested(2)

        viewModel.effects.test {
            assertThat(awaitItem())
                .isEqualTo(
                    AppsEffect.StartStream(StreamRequest("host-1", 2, "steam", hdrSupported = true))
                )
        }
    }

    @Test
    fun `a launch request while another app runs opens the actions`() = runTest {
        repository.emit(snapshot(desktop, steam, running = desktop))
        subscribe(viewModel.uiState)

        viewModel.onLaunchRequested(2)
        runCurrent()

        assertThat(viewModel.uiState.value.actions?.app?.id).isEqualTo(2)
        viewModel.effects.test { expectNoEvents() }
    }

    @Test
    fun `a launch request for an app that is not listed does nothing`() = runTest {
        repository.emit(snapshot(desktop))
        subscribe(viewModel.uiState)

        viewModel.onLaunchRequested(99)
        runCurrent()

        viewModel.effects.test { expectNoEvents() }
    }

    @Test
    fun `a launch request after the screen closed does not start a stream`() = runTest {
        repository.emit(snapshot(desktop, host = aHost(reachability = Reachability.OFFLINE)))
        subscribe(viewModel.uiState)

        viewModel.onLaunchRequested(1)

        viewModel.effects.test {
            assertThat(awaitItem()).isEqualTo(AppsEffect.Close(AppsMessage.HostOffline))
            expectNoEvents()
        }
    }

    @Test
    fun `opening a host publishes its shortcut once`() = runTest {
        repository.emit(snapshot(desktop))
        subscribe(viewModel.uiState)

        repository.emit(snapshot(desktop, steam))
        runCurrent()

        assertThat(shortcuts.openedHosts).containsExactly("host-1" to "Desk PC")
    }

    @Test
    fun `a host that is not paired or offline gets no shortcut`() = runTest {
        repository.emit(snapshot(desktop, host = aHost(id = "host-1", paired = false)))
        subscribe(viewModel.uiState)
        repository.emit(
            snapshot(desktop, host = aHost(id = "host-1", reachability = Reachability.OFFLINE))
        )
        runCurrent()
        repository.emit(snapshot(desktop, host = null))
        runCurrent()

        assertThat(shortcuts.openedHosts).isEmpty()
    }

    @Test
    fun `a host that is paired after the screen opened gets its shortcut then`() = runTest {
        repository.emit(
            snapshot(
                desktop,
                host = aHost(id = "host-1", paired = false, reachability = Reachability.UNKNOWN),
            )
        )
        subscribe(viewModel.uiState)
        assertThat(shortcuts.openedHosts).isEmpty()

        repository.emit(snapshot(desktop))
        runCurrent()

        assertThat(shortcuts.openedHosts).containsExactly("host-1" to "Desk PC")
    }

    @Test
    fun `every sheet offers to add the app to the home screen when pinning is possible`() =
        runTest {
            shortcuts.canPinApps = true
            repository.emit(snapshot(desktop, steam, running = desktop))
            subscribe(viewModel.uiState)

            viewModel.onActions(1)
            runCurrent()
            assertThat(viewModel.uiState.value.actions?.choices)
                .containsExactly(
                    AppAction.RESUME,
                    AppAction.QUIT,
                    AppAction.DETAILS,
                    AppAction.ADD_TO_HOME_SCREEN,
                )
                .inOrder()

            viewModel.onActions(2)
            runCurrent()
            assertThat(viewModel.uiState.value.actions?.choices)
                .containsExactly(
                    AppAction.QUIT_AND_START,
                    AppAction.DETAILS,
                    AppAction.HIDE,
                    AppAction.ADD_TO_HOME_SCREEN,
                )
                .inOrder()
        }

    @Test
    fun `an app cannot be added to the home screen when the device cannot pin`() = runTest {
        repository.emit(snapshot(desktop, steam))
        subscribe(viewModel.uiState)

        viewModel.onActions(2)
        runCurrent()

        assertThat(viewModel.uiState.value.actions?.choices)
            .containsExactly(AppAction.START, AppAction.DETAILS, AppAction.HIDE)
            .inOrder()
        viewModel.onActionChosen(AppAction.ADD_TO_HOME_SCREEN)
        assertThat(shortcuts.pinned).isEmpty()
    }

    @Test
    fun `adding to the home screen pins the app, closes the sheet and starts nothing`() = runTest {
        shortcuts.canPinApps = true
        repository.emit(snapshot(desktop, steam))
        subscribe(viewModel.uiState)
        viewModel.onActions(2)
        runCurrent()

        viewModel.onActionChosen(AppAction.ADD_TO_HOME_SCREEN)
        runCurrent()

        assertThat(shortcuts.pinned)
            .containsExactly(
                FakeHostShortcuts.PinCall("host-1", "Desk PC", 2, "steam", hdrSupported = true)
            )
        assertThat(viewModel.uiState.value.actions).isNull()
        viewModel.effects.test { expectNoEvents() }
    }

    @Test
    fun `a launcher that refuses the shortcut says so`() = runTest {
        shortcuts.canPinApps = true
        shortcuts.pinResult = false
        repository.emit(snapshot(desktop))
        subscribe(viewModel.uiState)
        viewModel.onActions(1)
        runCurrent()

        viewModel.onActionChosen(AppAction.ADD_TO_HOME_SCREEN)

        viewModel.effects.test {
            assertThat(awaitItem()).isEqualTo(AppsEffect.ShowMessage(AppsMessage.PinFailed))
        }
    }

    @Test
    fun `a launch request starts an app that was hidden since the shortcut was pinned`() = runTest {
        repository.emit(snapshot(desktop, secret))
        subscribe(viewModel.uiState)
        assertThat(viewModel.uiState.value.items).containsExactly(item(desktop))

        viewModel.onLaunchRequested(4)

        viewModel.effects.test {
            assertThat(awaitItem())
                .isEqualTo(
                    AppsEffect.StartStream(
                        StreamRequest("host-1", 4, "Secret", hdrSupported = false)
                    )
                )
        }
    }

    @Test
    fun `a launch request for a hidden app while another runs opens its actions`() = runTest {
        repository.emit(snapshot(desktop, secret, running = desktop))
        subscribe(viewModel.uiState)

        viewModel.onLaunchRequested(4)
        runCurrent()

        assertThat(viewModel.uiState.value.actions?.app?.id).isEqualTo(4)
        assertThat(viewModel.uiState.value.actions?.choices)
            .containsExactly(AppAction.QUIT_AND_START, AppAction.DETAILS, AppAction.UNHIDE)
            .inOrder()
        viewModel.effects.test { expectNoEvents() }
    }
}
