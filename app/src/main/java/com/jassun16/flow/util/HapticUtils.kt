package com.jassun16.flow.util

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log

object HapticUtils {

    private fun getVibrator(context: Context) =
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
            .defaultVibrator

    private fun vibrate(context: Context, effect: VibrationEffect) {
        try {
            getVibrator(context).vibrate(effect)
        } catch (e: Exception) {
            Log.w("HapticUtils", "Haptic failed: ${e.message}")
        }
    }

    fun tick(context: Context) = vibrate(
        context,
        VibrationEffect.createOneShot(10, 80)
    )

    fun click(context: Context) = vibrate(
        context,
        VibrationEffect.createOneShot(14, 120)
    )

    fun heavyClick(context: Context) = vibrate(
        context,
        VibrationEffect.createOneShot(20, 200)
    )

    fun doubleClick(context: Context) = vibrate(
        context,
        VibrationEffect.createWaveform(
            longArrayOf(0, 14, 60, 14),
            intArrayOf(0, 180, 0, 180),
            -1
        )
    )

    fun thud(context: Context) = vibrate(
        context,
        VibrationEffect.createWaveform(
            longArrayOf(0, 28, 48, 16),
            intArrayOf(0, 230, 0, 90),
            -1
        )
    )

    fun hardStop(context: Context) = vibrate(
        context,
        VibrationEffect.createOneShot(35, 255)
    )

    fun bookmarkOn(context: Context) = vibrate(
        context,
        VibrationEffect.createOneShot(18, 180)
    )
}
