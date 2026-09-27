package dev.volumetile.tile

import android.media.AudioManager
import dev.volumetile.R

/** 「音量 -」磁贴：点击使媒体音量降低一级 */
class VolumeDownTileService : VolumeTileServiceBase() {
    override val direction: Int = AudioManager.ADJUST_LOWER
    override val labelRes: Int = R.string.tile_down_label
}
