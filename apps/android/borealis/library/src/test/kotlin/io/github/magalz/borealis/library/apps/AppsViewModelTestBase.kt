package io.github.magalz.borealis.library.apps

import io.github.magalz.borealis.library.testing.FakeAppRepository
import io.github.magalz.borealis.library.testing.FakeHostShortcuts
import io.github.magalz.borealis.logging.testing.MainDispatcherRule
import org.junit.Rule

/**
 * The base of the tests of [AppsViewModel]: the main dispatcher, a fake repository, the shortcuts
 * ([canPinApps] is what they answer) and the view model of the host `host-1` over them.
 */
// Abstract so that Gradle does not run the base as a test of its own.
@Suppress("AbstractClassCanBeConcreteClass")
abstract class AppsViewModelTestBase(canPinApps: Boolean = false) {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    protected val repository = FakeAppRepository()
    protected val shortcuts = FakeHostShortcuts(canPinApps)
    // Lazy: a ViewModel must be created after the rule has set the main dispatcher.
    protected val viewModel by lazy { AppsViewModel("host-1", repository, shortcuts) }
}
