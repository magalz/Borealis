package io.github.magalz.borealis.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser

/** The settings DataStore file is settings too: it stays out of every backup and transfer. */
@RunWith(RobolectricTestRunner::class)
class BackupRulesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** The `domain:path` of every `exclude` of [xmlRes]. */
    private fun excludes(xmlRes: Int): List<String> {
        val found = mutableListOf<String>()
        context.resources.getXml(xmlRes).use { xml ->
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == XmlPullParser.START_TAG && xml.name == "exclude") {
                    found +=
                        "${xml.getAttributeValue(null, "domain")}:${xml.getAttributeValue(null, "path")}"
                }
            }
        }
        return found
    }

    @Test
    fun `the full backup rules exclude the preferences and the DataStore directory`() {
        assertThat(excludes(R.xml.backup_rules)).containsExactly("sharedpref:.", "file:datastore/")
    }

    @Test
    fun `the Android 12 rules exclude the DataStore directory from backup and device transfer`() {
        assertThat(excludes(R.xml.backup_rules_s))
            .containsExactly(
                "sharedpref:.",
                "file:datastore/",
                "sharedpref:.",
                "file:datastore/",
            )
    }
}
