package com.hamza.studyhub.sources

import org.json.JSONObject

/**
 * Every future school source plugs into Hamza Study Hub through this contract.
 * It keeps the agent pipeline independent from Teams, Untis, WhatsApp or iSchool.
 */
interface SchoolSourceAdapter {
    val id: String
    val displayName: String
    val trust: SourceTrust

    /** Convert source-specific payload into the canonical event shape used by agents. */
    fun normalize(payload: JSONObject): JSONObject
}

enum class SourceTrust {
    OFFICIAL,
    OFFICIAL_SIGNAL,
    COMMUNITY,
    USER_IMPORTED
}

object SourceCatalog {
    const val WEBUNTIS = "WEBUNTIS"
    const val UNTIS_NOTIFICATION = "UNTIS_NOTIFICATION"
    const val TEAMS_NOTIFICATION = "TEAMS_NOTIFICATION"
    const val WHATSAPP_PARENT = "WHATSAPP_PARENT"
    const val SCREENSHOT = "SCREENSHOT"
    const val SHARED_CONTENT = "SHARED_CONTENT"
    const val ISCHOOL = "ISCHOOL"

    val plannedSources = listOf(
        WEBUNTIS,
        UNTIS_NOTIFICATION,
        TEAMS_NOTIFICATION,
        WHATSAPP_PARENT,
        SCREENSHOT,
        SHARED_CONTENT,
        ISCHOOL
    )
}
