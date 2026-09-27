package com.tedexcuseme.volumetile.volume

import android.content.Context
import android.media.AudioManager
import com.tedexcuseme.volumetile.R

/**
 * 音量控制核心（无 UI 依赖）。
 *
 * 单 IPC 设计（v1.2 / 延迟优化 R1）：
 * 每次点击仅一次 Binder 调用 [AudioManager.adjustStreamVolume] 直接完成音量变更。
 *
 * 历史说明（v1.0–v1.1 曾采用双路径）：优先走 API 34 音量分组接口
 * （getVolumeGroupIdForAttributes 查询 + adjustVolumeGroupVolume 调节，2 次串行 Binder IPC）。
 * 经全链路延迟审查（docs/design.md §6.3）确认在手机上应弃用分组路径：
 *  - 官方文档明确：分组关联到流类型时，分组接口内部即回退 adjustStreamVolume——
 *    组查询在本设备上是纯延迟、零行为收益；
 *  - 本应用级数显示一直读自 stream 轴（getStreamVolume），读写同轴可消除
 *    "副标题显示值与实际所改对象不一致"的理论分歧。
 */
object VolumeController {

    /**
     * 静默调节（flags = 0）：不请求任何系统音量浮层 UI。
     * 反馈来源：磁贴副标题实时级数 + 控制中心自带音量条的同步变化
     * （澎湃控制中心本身提供音量条，无需额外弹窗）。
     * 同时作为高频点击修复实验 E1：彻底排除系统音量浮层干扰连点的可能。
     */
    private const val FLAGS = 0

    /** 进程级缓存：AudioManager 为系统单例，字段缓存省去每次 ServiceRegistry 查询（R4） */
    private var cachedAudioManager: AudioManager? = null

    /** 调节媒体音量一级。全程 1 次 IPC，任何异常都不会抛给调用方。 */
    fun adjust(context: Context, direction: Int) {
        runCatching {
            audioManager(context)
                .adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, FLAGS)
        }
        // 媒体流不触发 DND SecurityException；兜底仅为"点击路径绝不崩溃"的承诺，
        // 失败则静默放弃本次点击
    }

    /** 磁贴显示用读数（stream 轴，与调节路径同轴）。 */
    fun levelText(context: Context): String {
        val am = audioManager(context)
        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return context.getString(R.string.tile_level_fmt, cur, max)  // "媒体 %1$d/%2$d"
    }

    private fun audioManager(context: Context): AudioManager =
        cachedAudioManager
            ?: context.getSystemService(AudioManager::class.java)!!
                .also { cachedAudioManager = it }
}
