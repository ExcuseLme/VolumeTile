package com.tedexcuseme.volumetile.volume

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

/**
 * 音量调节前台服务 —— Android 17（API 37）「Background audio hardening」适配层。
 *
 * ## 为什么需要它
 *
 * Android 17 起音频框架对**后台**应用的音量调用做加固拦截，命中后**静默丢弃且不抛异常**
 * （官方 *Background audio hardening*；实现见 AOSP HardeningEnforcer#blockVolumeMethod）。
 * 判定分两级：
 *  - level: full    —— 有前台服务（FGS），但该 FGS 不具备 while-in-use（WIU）能力；
 *  - level: partial —— 根本没有运行前台服务。
 *
 * 快捷设置磁贴由 SystemUI 绑定运行，属后台绑定服务、进程常态 cached，因此在澎湃 4
 * （HyperOS 4 / Android 17）上必然命中 partial 级加固 —— 这正是 v1.4 及以前
 * 「点击磁贴无反应、也不报错」的根因。
 *
 * ## 为什么本方案成立（已核 AOSP 17 源码与官方文档）
 *
 * 1. 放行条件：本应用 targetSdk = 36（< 37），AOSP 判定 isPreCinnamonBun == true，
 *    得到 DENIED_IF_PARTIAL —— 只要存在前台服务即放行，无需精确的 WIU 能力：
 *
 *        } else if (isPreCinnamonBun) {
 *            yield new int[]{DENIED_IF_PARTIAL, EXEMPTION_TARGET_SDK};  // 仅拦「无 FGS」
 *        }
 *        yield new int[]{DENIED_IF_FULL, EXEMPTION_NONE};               // targetSdk>=37 走这里
 *
 *    AppOps 侧 MODE_FOREGROUND 的放行条件是 uidState <= UID_STATE_FOREGROUND(500)，
 *    而前台服务的进程状态为 UID_STATE_FOREGROUND_SERVICE(400)，一旦 FGS 生效即通过。
 *    这也是坚持 targetSdk = 36 的原因：升到 37 会切到 DENIED_IF_FULL，
 *    要求 FGS 必须精确具备 WIU，容错空间收窄而非放宽。
 *
 * 2. 能否从磁贴启动 FGS：磁贴由 SystemUI（系统进程）绑定，AOSP
 *    ActiveServices#canStartForegroundServiceLocked 在常规判定被拒时，会通过
 *    canBindingClientStartFgsLocked 继承绑定方的后台启动前台服务能力
 *    （REASON_FGS_BINDING）。因此点击磁贴后启动本服务是系统允许的路径。
 *
 * ## 时序
 *
 * startForeground() 内部同步调用 IActivityManager.setServiceForeground，返回时 AMS
 * 已记录前台状态，随后才执行音量调用 —— 严格保证「先转前台、再调音量」的顺序。
 *
 * ## 兼容性
 *
 * 未启用加固的系统（Android 16 / 澎湃 3 及更早）上本路径同样有效：FGS 对音量调节无副作用。
 * 若 FGS 无法启动，调用方回退到直接调节，保证最坏情况退回 v1.4 行为而非彻底失效。
 *
 * ## 设计约束（延续项目 G2：无后台常驻）
 *
 *  - 生命周期极短：调完立即 stopSelf，不留常驻通知、不常驻后台。
 *  - 通知为 MIN 优先级 + 静默，仅作为 FGS 的法定凭证；即使未授予通知权限，
 *    前台服务状态本身仍然成立，因此本应用依旧零运行时权限。
 */
class VolumeAdjustService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 第一步：进入前台。必须早于任何音量调用，否则仍会被加固拦截。
        runCatching { enterForeground() }

        val direction = intent?.getIntExtra(EXTRA_DIRECTION, INVALID_DIRECTION) ?: INVALID_DIRECTION
        if (direction != INVALID_DIRECTION) {
            // 前台已建立时加固校验必过；即使建立失败也照常尝试（未加固系统仍然生效）。
            VolumeController.adjust(applicationContext, direction)
        }

        // 一次性任务：调完立即退出，不占用后台、不常驻。
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun enterForeground() {
        val notification = buildNotification()
        // minSdk = 34，使用带类型的三参数版本。
        // 类型选 specialUse：本服务不播放音频（mediaPlayback 语义不实且需媒体播放权限），
        // 也不能用 SHORT_SERVICE —— 加固豁免要求「非 SHORT_SERVICE 的 FGS」，
        // 而 SHORT_SERVICE 本身不授予 WIU。specialUse（无法归入其他类别）语义最贴合，
        // 只需一个安装即授予的 normal 级权限。
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(com.tedexcuseme.volumetile.R.string.adjust_channel_name),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = getString(com.tedexcuseme.volumetile.R.string.adjust_channel_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager?.createNotificationChannel(channel)

        // 静音由 channel 保证（IMPORTANCE_MIN + setSound(null) + enableVibration(false)）；
        // framework 的 Notification.Builder 没有 setSilent()（那是 androidx NotificationCompat 的 API），
        // 本项目零第三方依赖，故不引入 NotificationCompat。
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(com.tedexcuseme.volumetile.R.string.adjust_notif_title))
            .setSmallIcon(com.tedexcuseme.volumetile.R.drawable.ic_volume_up)
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "volume_adjust"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_DIRECTION = "direction"
        private const val INVALID_DIRECTION = Int.MIN_VALUE

        /**
         * 启动一次「前台服务内」的音量调节。
         *
         * 返回 false 表示 FGS 无法启动（系统限制 / 厂商策略），调用方应回退到直接调节，
         * 让旧系统与未启用加固的设备行为完全不变。
         */
        fun adjust(context: Context, direction: Int): Boolean {
            val intent = Intent(context, VolumeAdjustService::class.java)
                .putExtra(EXTRA_DIRECTION, direction)
            return runCatching {
                context.startForegroundService(intent)
                true
            }.getOrDefault(false)
        }
    }
}