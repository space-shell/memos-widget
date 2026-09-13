package dev.jamesnicholls.memoswidget.net

object UrlUtil {

    /**
     * Normalise a user-entered server address into a bare base URL.
     * - defaults missing scheme to https
     * - strips trailing slashes
     * - strips a trailing /api/v1 suffix (users often copy it from docs)
     */
    fun normaliseBaseUrl(raw: String): String {
        var url = raw.trim()
        if (url.isEmpty()) return url
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        url = url.trimEnd('/')
        url = url.removeSuffix("/api/v1")
        return url.trimEnd('/')
    }
}
