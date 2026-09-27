package dev.volumetile.tile

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.volumetile.R
import dev.volumetile.volume.VolumeController

/**
 * 磁贴服务基类：负责「磁贴 ↔ 音量控制器」的接线与状态回写。
 *
 * 采用被动（passive）磁贴模式——不声明 ACTIVE_TILE meta-data：
 * 每次快捷设置面板展开时系统绑定本服务并回调 [onStartListening]，
 * 即使用户用实体音量键调过音量，磁贴上的级数显示也永远是最新的；
 * 同时该模式让系统在面板展开（磁贴可见）时即拉起被杀的应用进程（预热）。
 *
 * 高频点击优化（见 docs/design.md §7.2）：
 *  - 调音量本身每次点击立即执行（无任何合并/延迟）
 *  - 磁贴副标题回写（updateTile）按 [REFRESH_COALESCE_MS] 合并：
 *    单击立即刷新；连点时 trailing 合并为一次回写，减少连点期间
 *    对 SystemUI 的状态刷新次数（HyperOS 磁贴在状态刷新期间可能丢点击）
 *  - 级数未变化（如到顶/到底）时跳过回写
 */
abstract class VolumeTileServiceBase : TileService() {

    /** 子类声明调节方向：[android.media.AudioManager.ADJUST_RAISE] / ADJUST_LOWER */
    protected abstract val direction: Int

    /** 子类声明磁贴主标签资源 */
    protected abstract val labelRes: Int

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingRefresh: Runnable? = null
    private var lastUpdateAt = 0L
    private var lastLevel: String? = null

    /** 用户点击磁贴（包括锁屏下点击——调音量非敏感操作，直接执行） */
    override fun onClick() {
        // 每次点击都真实执行，绝不由本层合并或延迟
        VolumeController.adjust(applicationContext, direction)
        scheduleRefresh()
    }

    /** 面板展开、磁贴可见时立即刷新显示（不节流，保证展开即最新） */
    override fun onStartListening() {
        refreshTile()
    }

    override fun onDestroy() {
        pendingRefresh?.let(mainHandler::removeCallbacks)
        pendingRefresh = null
        super.onDestroy()
    }

    /**
     * 回写节流：距上次回写超过 [REFRESH_COALESCE_MS] 则立即刷新；
     * 否则合并为一次 trailing 刷新（连点风暴中最多每窗口回写一次）。
     */
    private fun scheduleRefresh() {
        val now = SystemClock.uptimeMillis()
        val sinceLast = now - lastUpdateAt
        if (sinceLast >= REFRESH_COALESCE_MS) {
            refreshTile()
        } else {
            pendingRefresh?.let(mainHandler::removeCallbacks)
            val task = Runnable {
                pendingRefresh = null
                refreshTile()
            }
            pendingRefresh = task
            mainHandler.postDelayed(task, REFRESH_COALESCE_MS - sinceLast)
        }
    }

    private fun refreshTile() {
        lastUpdateAt = SystemClock.uptimeMillis()
        val tile = qsTile ?: return
        val level = VolumeController.levelText(this)
        if (level == lastLevel) return   // 级数未变（到顶/到底等）：跳过无意义回写
        lastLevel = level
        tile.label = getString(labelRes)
        tile.subtitle = level            // API 29+：副标题显示「媒体 7/15」
        tile.stateDescription = level    // API 30+：TalkBack 播报当前级数
        tile.state = Tile.STATE_ACTIVE
        tile.updateTile()
    }

    private companion object {
        /** 连点回写合并窗口（毫秒）：覆盖人类连点间隔（约 120–200ms） */
        const val REFRESH_COALESCE_MS = 250L
    }
}
