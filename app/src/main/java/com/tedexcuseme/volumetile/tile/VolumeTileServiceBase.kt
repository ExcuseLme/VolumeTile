package com.tedexcuseme.volumetile.tile

import android.media.AudioManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tedexcuseme.volumetile.volume.VolumeAdjustService
import com.tedexcuseme.volumetile.volume.VolumeController

/**
 * 磁贴服务基类 —— 「纯静态工具磁贴」（D 方案 + 无状态外观）。
 *
 * 产品形态：
 *  - label 静态：Manifest/磁贴 XML 提供「音量 +」「音量 -」，运行时永不改写（D 方案）；
 *  - 不显示级数：实测澎湃控制中心不渲染 subtitle，级数反馈由控制中心自带音量条承担；
 *  - 无状态外观：每次监听确保 [Tile.STATE_INACTIVE]（未高亮、仍可点击）。
 *
 * ## v2.0 关键变更：Android 17 音量加固适配
 *
 * 旧实现（v1.3–v1.4）直接在 [onClick] 里调 AudioManager。澎湃 4（HyperOS 4 / Android 17）
 * 引入 *Background audio hardening*：后台应用的音量调用被**静默丢弃且不抛异常**。
 * 磁贴是 SystemUI 绑定的后台服务（进程为 cached），因此必然被拦 —— 表现为「点了没反应」。
 *
 * 现在改为把调节动作交给 [VolumeAdjustService]：它先短暂转前台（使音量调用通过加固校验），
 * 完成调节后立即退出。若前台服务启动失败（厂商限制等），回退到直接调节，
 * 保证最坏情况退回旧行为而非完全失效。
 *
 * 性能/复杂度说明：点击路径从「直接调音量」变为「启动一次极短 FGS + 调音量」，
 * 多一次进程内 startService IPC，但仍无回写、无 Handler、无定时器、无常驻后台。
 */
abstract class VolumeTileServiceBase : TileService() {

    /** 子类声明调节方向：[AudioManager.ADJUST_RAISE] / [AudioManager.ADJUST_LOWER] */
    protected abstract val direction: Int

    /** 点击磁贴：交给前台服务执行（绕开 Android 17 后台音量加固），失败则回退直接调节 */
    override fun onClick() {
        val started = VolumeAdjustService.adjust(applicationContext, direction)
        if (!started) {
            // 回退：FGS 起不来时保持旧行为（在未加固系统上仍然有效）
            VolumeController.adjust(applicationContext, direction)
        }
    }

    /** 面板展开/磁贴可见：刷新刻度缓存 + 确保静态（未高亮）外观；兼作进程预热 */
    override fun onStartListening() {
        VolumeController.refreshScale(applicationContext)
        qsTile?.let { tile ->
            tile.state = Tile.STATE_INACTIVE   // 无条件写：本地缓存无法感知 SystemUI 侧重建
            tile.updateTile()
        }
    }
}
