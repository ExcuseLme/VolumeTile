package com.tedexcuseme.volumetile.tile

import android.media.AudioManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tedexcuseme.volumetile.volume.VolumeController

/**
 * 磁贴服务基类 —— v1.3 起为「纯静态工具磁贴」（D 方案 + 无状态外观）。
 *
 * 产品形态：
 *  - label 静态：Manifest/磁贴 XML 提供「音量 +」「音量 -」，运行时永不改写（D 方案）；
 *  - 不显示级数：实测澎湃控制中心不渲染 subtitle（原生 Android 会渲染），级数反馈
 *    统一由控制中心自带音量条承担；
 *  - 无状态外观：不表达"开/关"语义 → 每次监听确保 [Tile.STATE_INACTIVE]（未高亮、
 *    仍可点击）。系统仅提供 ACTIVE / INACTIVE / UNAVAILABLE 三态，无"无状态"档位，
 *    INACTIVE 即最接近"纯工具"的中性外观。
 *
 * 性能形态（v1.3 移除 v1.0–1.2 的整套动态回写机制）：
 *  - onClick：仅调音量（网格定位 2 IPC / 回退 1 IPC）——零回写、零 Handler、零定时器
 *  - onStartListening：刷新刻度缓存（1 IPC）+ 无条件写 STATE_INACTIVE（1 IPC）。
 *    状态写必须保留：若 SystemUI 侧重建了磁贴而我方进程未重启，本地状态无法感知，
 *    缺省态存在为 UNAVAILABLE 的风险 —— CustomTile.handleClick 在 SystemUI 侧
 *    对 UNAVAILABLE 直接丢弃点击，磁贴会"失灵"。
 *  - 删除项：subtitle/stateDescription 回写、250ms 节流合并、mainHandler、
 *    pendingRefresh、lastLevel/lastUpdateAt、onDestroy 回调清理、levelText 读数
 *    → 每击省 2–3 次 IPC 与一次 SystemUI 磁贴视图重绘（连点吞吐同步减负）
 *
 * 被动磁贴模式不变：面板展开（磁贴可见）即绑定 → 系统拉起被杀进程完成预热。
 */
abstract class VolumeTileServiceBase : TileService() {

    /** 子类声明调节方向：[AudioManager.ADJUST_RAISE] / [AudioManager.ADJUST_LOWER] */
    protected abstract val direction: Int

    /** 点击磁贴：唯一工作就是调音量，绝不被任何回写延迟 */
    override fun onClick() {
        VolumeController.adjust(applicationContext, direction)
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
