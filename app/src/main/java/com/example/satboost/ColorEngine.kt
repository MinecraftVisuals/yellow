package com.example.satboost

import java.io.OutputStream
import java.util.Locale

object ColorEngine {
    private var proc: Process? = null
    private var out: OutputStream? = null

    fun matrix(sat: Int, con: Int, bri: Int): FloatArray {
        val s = sat / 100f
        val c = con / 100f
        val b = bri / 100f
        val lum = floatArrayOf(0.2126f, 0.7152f, 0.0722f)
        val k = c * b
        val t = 0.5f * (1f - c) * b
        val m = FloatArray(16)
        for (col in 0..2) for (row in 0..2) {
            val a = (1f - s) * lum[col] + if (row == col) s else 0f
            m[col * 4 + row] = a * k
        }
        m[12] = t; m[13] = t; m[14] = t; m[15] = 1f
        return m
    }

    fun apply(sat: Int, con: Int, bri: Int): Boolean {
        val m = matrix(sat, con, bri)
        val sb = StringBuilder("service call SurfaceFlinger 1015 i32 1")
        for (v in m) sb.append(" f ").append(String.format(Locale.US, "%.4f", v))
        return exec(sb.toString())
    }

    fun reset(): Boolean = exec("service call SurfaceFlinger 1015 i32 0")

    @Synchronized
    private fun exec(cmd: String): Boolean = try {
        if (proc?.isAlive != true) {
            proc = Runtime.getRuntime().exec("su")
            out = proc!!.outputStream
        }
        out!!.write((cmd + "\n").toByteArray())
        out!!.flush()
        true
    } catch (e: Exception) {
        proc = null
        false
    }
}
