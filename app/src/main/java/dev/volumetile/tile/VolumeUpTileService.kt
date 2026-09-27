package dev.volumetile.tile

import android.media.AudioManager

/** 「音量 +」磁贴：点击使媒体音量增加一级 */
class VolumeUpTileService : VolumeTileServiceBase() {
    override val direction: Int = AudioManager.ADJUST_RAISE
}
