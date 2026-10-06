package tv.own.owntv.player

/** With the HUD hidden, live channel navigation takes priority over revealing VOD controls. */
internal fun downRevealsHiddenControls(isLive: Boolean, canZap: Boolean): Boolean = !isLive || !canZap
