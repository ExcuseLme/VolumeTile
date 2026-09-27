package dev.volumetile.volume

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import dev.volumetile.R

/**
 * 音量控制核心（无 UI 依赖）。
 *
 * 双路径设计（见 docs/design.md §6.3）：
 *  - 主路径：API 34 音量分组接口 [AudioManager.getVolumeGroupIdForAttributes] +
 *    [AudioManager.adjustVolumeGroupVolume]（尽可能使用新 API）
 *  - 回退路径：经典 [AudioManager.adjustStreamVolume]（稳定兜底）
 *
 * 官方文档明确：分组关联到流类型时，分组接口内部即回退到 adjustStreamVolume，
 * 因此两条路径行为一致、风险有界。
 */
object VolumeController {

    /**
     * `getVolumeGroupIdForAttributes` 未命中时的哨兵值。
     *
     * 公开文档返回值描述为 `AudioVolumeGroup.DEFAULT_VOLUME_GROUP`，其 AOSP 源码值为 -1；
     * 该类是 @hide @SystemApi，公开 SDK 中不可引用，故在此以本地常量声明。
     */
    private const val DEFAULT_VOLUME_GROUP = -1

    /** 媒体用途的音频属性：本应用固定调节媒体音量 */
    private val mediaAttrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    /** FLAG_SHOW_UI：弹出 Android 15/16 新版系统音量面板作为点击反馈 */
    private const val FLAGS = AudioManager.FLAG_SHOW_UI

    /** 调节媒体音量一级。任何异常都不会抛给调用方（磁贴点击路径绝不崩溃）。 */
    fun adjust(context: Context, direction: Int) {
        val am = context.getSystemService(AudioManager::class.java)

        // 路径 A：API 34 音量分组（新 API）
        runCatching {
            val groupId = am.getVolumeGroupIdForAttributes(mediaAttrs)
            if (groupId != DEFAULT_VOLUME_GROUP) {
                am.adjustVolumeGroupVolume(groupId, direction, FLAGS)
                return
            }
            // groupId 无效 → 落入路径 B
        }

        // 路径 B：经典流接口回退（分组缺失 / SecurityException（DND）均落此）
        runCatching {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, FLAGS)
        }
        // 若两路均失败：静默放弃本次点击（媒体流场景实际不会发生）
    }

    /** 磁贴显示用读数。分组 API 无配套读数方法，始终取自经典流 API。 */
    fun levelText(context: Context): String {
        val am = context.getSystemService(AudioManager::class.java)
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return context.getString(R.string.tile_level_fmt, current, max)
    }
}
