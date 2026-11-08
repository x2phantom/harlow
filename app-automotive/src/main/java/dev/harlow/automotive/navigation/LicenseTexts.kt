package dev.harlow.automotive.navigation

import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.entity.License

/**
 * Licenses for which the AboutLibraries plugin only knows a name and a link, by the name in its generated list. Head
 * units usually have no browser, so the texts ship with the app. The LGPL requires a copy of the GPL as well.
 */
private val LICENSE_ASSETS = mapOf(
    "GNU Lesser General Public License v3.0" to listOf("LGPL-3.0.txt", "GPL-3.0.txt"),
    "GNU General Public License, version 2 with the GNU Classpath Exception" to
        listOf("GPL-2.0-classpath-exception.txt"),
    "Eclipse Public License v. 2.0" to listOf("EPL-2.0.txt"),
)

/** Fills in the license texts the plugin has no content for. [readAsset] reads a file of `assets/licenses`. */
internal fun Library.withBundledLicenseTexts(readAsset: (String) -> String): Library = copy(
    licenses = licenses.map { license ->
        val assets = LICENSE_ASSETS[license.name]
        if (license.licenseContent.isNullOrBlank() && assets != null) {
            license.copy(licenseContent = joinAssets(assets, readAsset))
        } else {
            license
        }
    }.toSet(),
)

/** Harlow itself and what the plugin can't see: embedded icons and the desugaring library (no runtime dependency). */
internal fun manualLibraries(readAsset: (String) -> String): List<Library> = listOf(
    manualLibrary(
        uniqueId = "dev.harlow:harlow",
        name = "Harlow",
        description = "This app, a modified version of Mellow.",
        website = "https://github.com/x2phantom/harlow",
        license = License(
            name = "Apache License 2.0",
            url = "https://www.apache.org/licenses/LICENSE-2.0",
            spdxId = "Apache-2.0",
            licenseContent = joinAssets(listOf("NOTICE", "LICENSE"), readAsset),
            hash = "harlow-apache",
        ),
    ),
    manualLibrary(
        uniqueId = "phosphor-icons:core",
        name = "Phosphor Icons",
        description = "A flexible icon family for interfaces, diagrams, presentations and more.",
        website = "https://phosphoricons.com",
        license = License(
            name = "MIT License",
            url = "https://github.com/phosphor-icons/core/blob/main/LICENSE",
            spdxId = "MIT",
            licenseContent = readAsset("phosphor-MIT.txt"),
            hash = "phosphor-mit",
        ),
    ),
    manualLibrary(
        uniqueId = "com.android.tools:desugar_jdk_libs",
        name = "desugar_jdk_libs",
        description = "Newer Java APIs on older Android versions.",
        website = "https://github.com/google/desugar_jdk_libs",
        license = License(
            name = "GNU General Public License, version 2 with the GNU Classpath Exception",
            url = "https://github.com/google/desugar_jdk_libs/blob/master/LICENSE",
            licenseContent = readAsset("GPL-2.0-classpath-exception.txt"),
            hash = "desugar-gpl2-classpath",
        ),
    ),
)

private fun manualLibrary(
    uniqueId: String,
    name: String,
    description: String,
    website: String,
    license: License,
) = Library(
    uniqueId = uniqueId,
    artifactVersion = null,
    name = name,
    description = description,
    website = website,
    developers = listOf(),
    organization = null,
    scm = null,
    licenses = setOf(license),
    funding = setOf(),
    tag = null,
)

private fun joinAssets(assets: List<String>, readAsset: (String) -> String): String =
    assets.joinToString("\n\n") { readAsset(it) }
