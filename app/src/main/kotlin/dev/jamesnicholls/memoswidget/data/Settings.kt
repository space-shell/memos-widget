package dev.jamesnicholls.memoswidget.data

enum class MemoVisibility(val wireName: String) {
    PRIVATE("PRIVATE"),
    PROTECTED("PROTECTED"),
    PUBLIC("PUBLIC");

    companion object {
        fun fromWireName(name: String): MemoVisibility =
            entries.firstOrNull { it.wireName == name } ?: PRIVATE
    }
}

data class MemosSettings(
    val serverUrl: String = "",
    val accessToken: String = "",
    val defaultVisibility: MemoVisibility = MemoVisibility.PRIVATE,
) {
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && accessToken.isNotBlank()
}
