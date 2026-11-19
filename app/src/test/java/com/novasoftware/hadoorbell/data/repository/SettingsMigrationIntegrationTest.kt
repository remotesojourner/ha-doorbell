package com.novasoftware.hadoorbell.data.repository

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novasoftware.hadoorbell.domain.model.StreamButton
import com.novasoftware.hadoorbell.domain.model.StreamButtonAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, application = SettingsMigrationIntegrationTest.MigrationApplication::class)
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class SettingsMigrationIntegrationTest {
    class MigrationApplication : Application()

    @Test
    fun `opening existing preferences migrates standalone controls before the first read`() = runTest {
        val context = ApplicationProvider.getApplicationContext<MigrationApplication>()
        val oldScope = Job()
        val lockKey = stringPreferencesKey("lock_entity_id")
        try {
            val oldStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + oldScope)) {
                context.preferencesDataStoreFile("settings")
            }
            oldStore.edit { it[lockKey] = "lock.entry" }
        } finally {
            oldScope.cancelAndJoin()
        }

        val layout = SettingsRepositoryImpl(context).streamButtonLayoutFlow.first()

        assertEquals(
            listOf(StreamButton(
                "legacy-door-lock", StreamButtonAction.DoorLock("lock.entry"),
                requiresAuthentication = true
            )),
            layout.bottom
        )
        assertFalse(lockKey in context.dataStore.data.first())
    }
}
