package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.settings.SettingsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsPersistenceTest {

    @Test
    fun `library folders survive a new settings manager instance`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val folders = setOf(
            "content://com.android.externalstorage.documents/tree/primary%3AMusic",
            "content://com.android.externalstorage.documents/tree/primary%3APodcasts"
        )

        SettingsManager(context).setLibraryFolders(folders)

        val restored = SettingsManager(context).settingsFlow.first()
        assertEquals(folders, restored.libraryFolderUris)
    }

    @Test
    fun `finished first-run setup is not shown again after restart`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        SettingsManager(context).setOnboardingComplete()

        assertEquals(true, SettingsManager(context).settingsFlow.first().onboardingComplete)
    }
}
