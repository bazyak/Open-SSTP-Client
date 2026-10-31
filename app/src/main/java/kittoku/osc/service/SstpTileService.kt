package kittoku.osc.service

import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceManager
import kittoku.osc.R
import kittoku.osc.preference.OscPrefKey
import kittoku.osc.preference.accessor.getBooleanPrefValue
import kittoku.osc.preference.checkPreferences


@RequiresApi(Build.VERSION_CODES.N)
internal class SstpTileService : TileService() {
    private val prefs by lazy {
        PreferenceManager.getDefaultSharedPreferences(this)
    }

    private val listener by lazy {
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == OscPrefKey.ROOT_STATE.name || key == OscPrefKey.ROOT_CONNECTED.name) {
                updateTileState()
            }
        }
    }

    private val rootState: Boolean
        get() = getBooleanPrefValue(OscPrefKey.ROOT_STATE, prefs)

    private val rootConnected: Boolean
        get() = getBooleanPrefValue(OscPrefKey.ROOT_CONNECTED, prefs)

    private val isVpnPrepared: Boolean
        get() = VpnService.prepare(this) == null

    /**
     * inactive               -> globe with a crossed-out lock
     * active, connecting     -> plain globe
     * active, connected      -> globe with a lock
     */
    private fun renderTile(isActive: Boolean, isConnected: Boolean) {
        val tile = qsTile ?: return

        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE

        val iconRes = when {
            !isActive -> R.drawable.ic_vpn_lock_off_24
            isConnected -> R.drawable.ic_baseline_vpn_lock_24
            else -> R.drawable.ic_baseline_public_24
        }
        tile.icon = Icon.createWithResource(this, iconRes)

        tile.updateTile()
    }

    private fun updateTileState() {
        renderTile(rootState, rootConnected)
    }

    override fun onTileAdded() {
        requestListeningState(this, ComponentName(this, SstpTileService::class.java))
    }

    override fun onStartListening() {
        updateTileState()

        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onStopListening() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun startVpnService(action: String) {
        val intent = Intent(this, SstpVpnService::class.java).setAction(action)

        if (action == ACTION_VPN_CONNECT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onClick() {
        if (!isVpnPrepared || checkPreferences(prefs) != null) return

        if (qsTile.state == Tile.STATE_ACTIVE) {
            // switch the tile off first, then tear the connection down in the background
            renderTile(isActive = false, isConnected = false)
            startVpnService(ACTION_VPN_DISCONNECT)
        } else {
            renderTile(isActive = true, isConnected = false)
            startVpnService(ACTION_VPN_CONNECT)
        }
    }
}
