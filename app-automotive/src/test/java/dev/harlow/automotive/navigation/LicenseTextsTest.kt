package dev.harlow.automotive.navigation

import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.entity.License
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseTextsTest {

    private fun library(licenseName: String, content: String? = null) = Library(
        uniqueId = "group:artifact",
        artifactVersion = null,
        name = "Library",
        description = null,
        website = null,
        developers = listOf(),
        organization = null,
        scm = null,
        licenses = setOf(License(licenseName, null, licenseContent = content, hash = "hash")),
        funding = setOf(),
        tag = null,
    )

    private fun text(library: Library) = library.licenses.single().licenseContent

    @Test
    fun `LGPL comes with the text of the GPL`() {
        val result = library("GNU Lesser General Public License v3.0").withBundledLicenseTexts { "<$it>" }

        assertEquals("<LGPL-3.0.txt>\n\n<GPL-3.0.txt>", text(result))
    }

    @Test
    fun `licenses with a text from the plugin stay as they are`() {
        val result = library("Eclipse Public License v. 2.0", content = "plugin").withBundledLicenseTexts { "<$it>" }

        assertEquals("plugin", text(result))
    }

    @Test
    fun `Harlow's entry shows the notice and the license`() {
        val harlow = manualLibraries { "<$it>" }.single { it.name == "Harlow" }

        assertEquals("<NOTICE>\n\n<LICENSE>", text(harlow))
    }

    @Test
    fun `every referenced license text is bundled`() {
        val requested = mutableListOf<String>()
        val read = { asset: String -> requested += asset; "" }
        manualLibraries(read)
        listOf(
            "GNU Lesser General Public License v3.0",
            "GNU General Public License, version 2 with the GNU Classpath Exception",
            "Eclipse Public License v. 2.0",
        ).forEach { library(it).withBundledLicenseTexts(read) }

        // LICENSE and NOTICE are copied from the repository root at build time
        val roots = listOf(File("src/main/assets/licenses"), File(".."))
        requested.distinct().forEach { asset ->
            assertTrue("$asset is missing", roots.any { File(it, asset).isFile })
        }
    }
}
