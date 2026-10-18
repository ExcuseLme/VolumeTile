package com.tedexcuseme.volumetile.tile

import android.media.AudioManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tedexcuseme.volumetile.volume.VolumeAdjustService
import com.tedexcuseme.volumetile.volume.VolumeController

/**
 * 磁贴服务基类 —— 「纯静态工具磁贴」。
 *
 * 产品形态：
 *  - label 静态：由 service 上的 android:label 提供，运行时永不改写；
 *  - 不显示级数：实测澎湃控制中心不渲染 subtitle，级数反馈由控制中心自带音量条承担；
 *  - 无状态外观：每次监听确保 [Tile.STATE_INACTIVE]（未高亮、仍可点击）。
 *
 * 点击路径：交给 [VolumeAdjustService] 执行。Android 17（澎湃 4）引入
 * Background audio hardening，会静默丢弃后台应用的音量调用；磁贴是 SystemUI 绑定的
 * 后台服务（进程为 cached），必然被拦。前台服务能把进程提升到
 * UID_STATE_FOREGROUND_SERVICE，从而通过校验。FGS 启动失败时回退为直接调节。
 */
abstract class VolumeTileServiceBase : TileService() {

    /** 子类声明调节方向：[AudioManager.ADJUST_RAISE] / [AudioManager.ADJUST_LOWER] */
    protected abstract val direction: Int

    /** 点击磁贴：交给前台服务执行（通过 Android 17 后台音量加固校验），失败则回退直接调节 */
    override fun onClick() {
        if (!VolumeAdjustService.adjust(applicationContext, direction)) {
            // 回退：FGS 起不来时保持旧行为（在未加固系统上仍然有效）
            VolumeController.adjust(applicationContext, direction)
        }
    }

    /**
     * 面板展开 / 磁贴可见：确保静态（未高亮）外观。
     *
     * 状态写必须无条件执行：本地 [Tile] 对象无法感知 SystemUI 侧的磁贴重建，
     * 若缺省态为 UNAVAILABLE，SystemUI 会直接丢弃点击（磁贴「失灵」）。
     */
    override fun onStartListening() {
        qsTile?.let { tile ->
            tile.state = Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }
}
