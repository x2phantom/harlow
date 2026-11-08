package dev.harlow.core.common

/** Harlow talks to Jellyfin only over HTTPS: the password and the access token must never travel in clear text. */
class InsecureServerUrlException : IllegalArgumentException(
    "Harlow only connects over HTTPS. Use your server's https:// address.",
)

/** What the user typed can't be a server address. */
class InvalidServerUrlException(message: String) : IllegalArgumentException(message)

/**
 * Turns what the user typed into the server's base URL: trims it, adds `https://` when there's no scheme and drops
 * trailing slashes. Throws [InsecureServerUrlException] for `http://` and [InvalidServerUrlException] for anything
 * that isn't an address.
 */
fun normalizeServerUrl(input: String): String {
    val trimmed = input.trim()
    val scheme = trimmed.substringBefore("://", missingDelimiterValue = "").lowercase()
    when (scheme) {
        "", "https" -> Unit
        "http" -> throw InsecureServerUrlException()
        else -> throw InvalidServerUrlException("Enter your server's https:// address.")
    }
    val address = trimmed.substringAfter("://").trimEnd('/')
    if (address.isEmpty() || address.any { it.isWhitespace() }) {
        throw InvalidServerUrlException("Enter your server's address.")
    }
    return "https://$address"
}

/** Whether [url] may be used to reach a server. */
fun isSecureServerUrl(url: String): Boolean = url.trim().startsWith("https://", ignoreCase = true)
