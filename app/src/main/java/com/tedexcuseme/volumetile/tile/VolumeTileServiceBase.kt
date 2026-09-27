package com.tedexcuseme.volumetile.tile

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tedexcuseme.volumetile.volume.VolumeController

/**
 * 磁贴服务基类：负责「磁贴 ↔ 音量控制器」的接线与状态回写。
 *
 * 被动（passive）磁贴模式——不声明 ACTIVE_TILE meta-data：
 *  - 面板展开（磁贴可见）即绑定 → 系统拉起被杀进程完成预热（gkd 式唤醒即此机制）
 *  - 每次面板展开回调 [onStartListening]，级数显示永远最新（含实体键调节），
 *    同时顺带完成 AudioManager/Binder 预热，首击前缓存已热
 *
 * 点击延迟优化（v1.2，见 docs/design.md §6.1/§7.2）：
 *  - 调音量每次点击同步立即执行，且为单次 IPC（见 VolumeController）
 *  - 回写（updateTile）经 [Handler.post] 移出点击关键路径（R2）：
 *    onClick 在 adjust 完成后立即返回；连点风暴时主线程队列更快排空
 *  - 回写再按 [REFRESH_COALESCE_MS] 合并（v1.1 起）+ 级数未变跳过 + 不再覆写
 *    静态 label（R4，主标签由 Manifest/磁贴 XML 提供）
 */
abstract class VolumeTileServiceBase : TileService() {

    /** 子类声明调节方向：[android.media.AudioManager.ADJUST_RAISE] / ADJUST_LOWER */
    protected abstract val direction: Int

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingRefresh: Runnable? = null
    private var lastUpdateAt = 0L
    private var lastLevel: String? = null

    /** 用户点击磁贴（包括锁屏下点击——调音量非敏感操作，直接执行） */
    override fun onClick() {
        // 第一步：真实调音量（同步、单 IPC）——绝不被后续任何工作延迟
        VolumeController.adjust(applicationContext, direction)
        // 第二步：回写移出关键路径——本点击到此立即返回（R2）
        mainHandler.post { scheduleRefresh() }
    }

    /** 面板展开、磁贴可见时立即刷新（同步，保证展开即最新；兼作 Binder 预热） */
    override fun onStartListening() {
        refreshTile()
    }

    override fun onDestroy() {
        // 移除本 Handler 上全部回调：pendingRefresh 与已 post 的 scheduleRefresh 调度
        mainHandler.removeCallbacksAndMessages(null)
        pendingRefresh = null
        super.onDestroy()
    }

    /**
     * 回写节流：距上次回写 ≥[REFRESH_COALESCE_MS] 立即刷新；
     * 否则合并为一次 trailing 刷新（连点风暴中每窗口至多回写一次）。
     * 注意：本方法只影响副标题回写时机，不影响音量调节本身。
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
        if (level == lastLevel) return   // 级数未变（到顶/到底）：跳过无意义回写
        lastLevel = level
        // 不覆写 tile.label（R4）：主标签由 Manifest/TILE XML 提供，显示相同、省一次赋值
        tile.subtitle = level            // API 29+：副标题「媒体 7/15」
        tile.stateDescription = level    // API 30+：TalkBack 播报
        tile.state = Tile.STATE_ACTIVE
        tile.updateTile()
    }

    private companion object {
        /** 连点回写合并窗口（毫秒）：覆盖人类连点间隔（约 120–200ms） */
        const val REFRESH_COALESCE_MS = 250L
    }
}
