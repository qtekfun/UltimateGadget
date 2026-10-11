package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dataio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UltimateBackupPrefsNameTest {
    private val main = "com.qtekfun.ultimategadget"
    private val play = "com.qtekfun.ultimategadget.play"

    @Test
    fun defaultPrefsFollowTheTargetPackage() {
        assertEquals(
            "${play}_preferences.xml",
            UltimateBackup.remapPrefsName("${main}_preferences.xml", main, play),
        )
    }

    @Test
    fun otherFilesKeepTheirName() {
        assertEquals(
            "devicesettings_AA:BB.xml",
            UltimateBackup.remapPrefsName("devicesettings_AA:BB.xml", main, play),
        )
    }

    @Test
    fun samePackageIsUnchanged() {
        assertEquals(
            "${main}_preferences.xml",
            UltimateBackup.remapPrefsName("${main}_preferences.xml", main, main),
        )
    }

    @Test
    fun infersPackageFromDefaultFile() {
        val leaves = listOf("devicesettings_AA.xml", "${main}_preferences.xml", "widgetsettings_1.xml")
        assertEquals(main, UltimateBackup.inferSourcePackage(leaves))
        assertNull(UltimateBackup.inferSourcePackage(listOf("devicesettings_AA.xml")))
    }
}
