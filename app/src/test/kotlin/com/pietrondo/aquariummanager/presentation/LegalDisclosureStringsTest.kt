package com.pietrondo.aquariummanager.presentation

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The legal and privacy disclosures are user-visible copy, so they live in string resources.
 * These guards keep the required statements from being edited away.
 */
class LegalDisclosureStringsTest {
    private val stringsXml = File("src/main/res/values/strings.xml").readText()

    @Test
    fun backupDisclosureStaysExplicitAboutUnencryptedArchives() {
        val disclosure = stringValue("backup_disclosure_unencrypted")
        assertTrue(disclosure.contains("unencrypted", ignoreCase = true))
        assertTrue(disclosure.contains("manifest.json"))
        assertTrue(disclosure.contains("library.json"))
    }

    @Test
    fun biologicalAndPrivacyStatementsStayExplicit() {
        val disclaimer = stringValue("disclaimer_biological_body")
        assertTrue(disclaimer.contains("informational", ignoreCase = true))
        assertTrue(disclaimer.contains("veterinary", ignoreCase = true))

        val privacy = stringValue("privacy_offline_summary")
        assertTrue(privacy.contains("100% offline", ignoreCase = true))
        assertTrue(privacy.contains("no internet permission", ignoreCase = true))
    }

    private fun stringValue(name: String): String =
        Regex("<string name=\"$name\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .find(stringsXml)
            ?.groupValues
            ?.get(1)
            ?: error("Missing string resource: $name")
}
