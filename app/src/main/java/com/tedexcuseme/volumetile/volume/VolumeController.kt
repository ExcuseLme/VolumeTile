package com.tedexcuseme.volumetile.volume

import android.content.Context
import android.media.AudioManager

/**
 * 音量控制核心（无 UI 依赖）。
 *
 * v1.3：对齐网格调节 —— 系统滑条造成的非整数级位置（如 0..150 刻度上的 43），
 * 经磁贴调节后必须落到整数级网格（43 − → 40，45 + → 50，40 ± → 30/50）。
 *
 * 机制（依据小米《MIUI无极音量适配说明》官方公式 step = max / 15，K1 实测确认 max=150）：
 *  - HyperOS 无极音量：API 刻度 0..150，step = 10（即 UI 的 15 级）；
 *  - 普通机型：max = 15，step = 1 —— 网格公式退化为 cur±1，与原生 adjust 恒等（零回归）；
 *  - max % 15 != 0 的机型：网格无定义 → 回退原生 adjustStreamVolume（系统内部仍按
 *    max/15 计算步长，只是不对齐）。
 *
 * 每击 2 次 IPC（读 cur + setStreamVolume 绝对定位）；绝对定位天然消除任何分数残留。
 */
object VolumeController {

    /** 静默：不请求任何系统音量浮层（级数反馈 = 控制中心自带音量条） */
    private const val FLAGS = 0

    private var cachedAudioManager: AudioManager? = null

    /** 刻度缓存：面板每次可见时由 [refreshScale] 刷新（非点击路径，1 IPC） */
    private var cachedMax = 0
    private var cachedStep = 0   // 0 = 网格不可用 → 回退原生 adjust

    /** 面板可见（onStartListening）时调用：刷新 max/step 刻度缓存 */
    fun refreshScale(context: Context) {
        runCatching {
            val max = audioManager(context).getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            cachedMax = max
            cachedStep = if (max > 0 && max % 15 == 0) max / 15 else 0
        }
    }

    /** 调节媒体音量一级并对齐到整数级网格。任何异常都不会抛给调用方。 */
    fun adjust(context: Context, direction: Int) {
        val am = runCatching { audioManager(context) }.getOrNull() ?: return

        if (cachedStep <= 0) refreshScale(context)   // 懒初始化 / 运行时重试
        val step = cachedStep
        if (step <= 0) {
            // 回退：网格信息不可用（异常机型或初始化失败）→ 原生步进
            runCatching { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, FLAGS) }
            return
        }

        runCatching {
            val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            // 升：吸附到网格再走一级；降：反向吸附再走一级
            // （恒等于「先对齐再 ±step」：floor(cur/step)*step+step / ceil(cur/step)*step-step）
            val target = (if (direction == AudioManager.ADJUST_RAISE) {
                cur / step * step + step
            } else {
                (cur + step - 1) / step * step - step
            }).coerceIn(0, cachedMax)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, FLAGS)
        }
    }

    private fun audioManager(context: Context): AudioManager =
        cachedAudioManager
            ?: context.getSystemService(AudioManager::class.java)!!
                .also { cachedAudioManager = it }
}
