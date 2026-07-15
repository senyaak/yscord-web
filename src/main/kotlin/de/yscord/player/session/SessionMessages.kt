package de.yscord.player.session

import de.yscord.player.TrackInfo

/**
 * Authoritative player state broadcast to every connected client. Playback position
 * is sent as an *anchor* (positionSec at anchorEpochMs) rather than a live value, so
 * each client can extrapolate the current position and stay in sync — `serverEpochMs`
 * lets a client estimate its clock offset from the server.
 */
data class SessionState(
    val tracks: List<TrackInfo>,
    val index: Int,
    val playing: Boolean,
    val positionSec: Double,
    val anchorEpochMs: Long,
    val serverEpochMs: Long,
    val loop: String,
)

/** A command from a client over the WebSocket. Extra fields are per-type payload. */
data class Command(
    val type: String,
    val position: Double? = null,
    val index: Int? = null,
    val delta: Int? = null,
    val query: String? = null,
)

/** Published whenever the authoritative state changes; the WS handler broadcasts it. */
data class StateChangedEvent(val state: SessionState)
