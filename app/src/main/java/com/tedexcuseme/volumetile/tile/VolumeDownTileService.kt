package com.tedexcuseme.volumetile.tile

import android.media.AudioManager

/** 「音量 -」磁贴：点击使媒体音量降低一级 */
class VolumeDownTileService : VolumeTileServiceBase() {
    override val direction: Int = AudioManager.ADJUST_LOWER
}
