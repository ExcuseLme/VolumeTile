package dev.volumetile.tile

import android.media.AudioManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.volumetile.R
import dev.volumetile.volume.VolumeController

/**
 * 磁贴服务基类：负责「磁贴 ↔ 音量控制器」的接线与状态回写。
 *
 * 采用被动（passive）磁贴模式——不声明 ACTIVE_TILE meta-data：
 * 每次快捷设置面板展开时系统绑定本服务并回调 [onStartListening]，
 * 即使用户用实体音量键调过音量，磁贴上的级数显示也永远是最新的。
 */
abstract class VolumeTileServiceBase : TileService() {

    /** 子类声明调节方向：[AudioManager.ADJUST_RAISE] / [AudioManager.ADJUST_LOWER] */
    protected abstract val direction: Int

    /** 子类声明磁贴主标签资源 */
    protected abstract val labelRes: Int

    /** 用户点击磁贴（包括锁屏下点击——调音量非敏感操作，直接执行） */
    override fun onClick() {
        VolumeController.adjust(applicationContext, direction)
        refreshTile()
    }

    /** 面板展开、磁贴可见时刷新显示 */
    override fun onStartListening() {
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val level = VolumeController.levelText(this)
        tile.label = getString(labelRes)
        tile.subtitle = level            // API 29+：副标题显示「媒体 7/15」
        tile.stateDescription = level    // API 30+：TalkBack 播报当前级数
        tile.state = Tile.STATE_ACTIVE
        tile.updateTile()
    }
}
