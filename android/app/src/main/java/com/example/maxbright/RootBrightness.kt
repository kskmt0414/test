package com.example.maxbright

import java.io.DataOutputStream

/**
 * Root-only "beyond the slider" control.
 *
 * A non-root app is capped at the OS maximum. With root we can write the panel
 * driver's sysfs nodes directly to drive the backlight to its physical maximum
 * and force High Brightness Mode (HBM). Node names and the HBM magic values are
 * device specific; the shell snippets below try the known patterns for Xiaomi
 * HyperOS (mi_display disp_param) plus a generic sweep, all on the safe side.
 *
 * WARNING: continuous HBM causes heat, fast battery drain and OLED burn-in.
 * Use it only when needed and turn it off afterwards.
 */
object RootBrightness {

    /** True if a `su` binary is present and grants a shell. */
    fun isRootAvailable(): Boolean = runAsRoot("id") { line -> line.contains("uid=0") }

    fun enable(): Boolean = runAsRoot(SCRIPT_ON)

    fun disable(): Boolean = runAsRoot(SCRIPT_OFF)

    // --- shell snippets ---------------------------------------------------

    // Push every backlight node to its max_brightness, then try to turn HBM on.
    private val SCRIPT_ON = """
        for d in /sys/class/backlight/* /sys/class/leds/lcd-backlight /sys/class/leds/*backlight*; do
          [ -w "${'$'}d/brightness" ] || continue
          m=${'$'}(cat "${'$'}d/max_brightness" 2>/dev/null)
          [ -n "${'$'}m" ] && echo "${'$'}m" > "${'$'}d/brightness" 2>/dev/null
        done
        for p in /sys/class/mi_display/disp-DSI-0/disp_param /sys/class/mi_disp/disp-DSI-0/disp_param; do
          [ -w "${'$'}p" ] || continue
          for v in 0x50000 0xF0000 0x10000; do echo "${'$'}v" > "${'$'}p" 2>/dev/null; done
        done
        for p in ${'$'}(find /sys -maxdepth 7 -iname '*hbm*' -perm -u+w 2>/dev/null); do
          [ -f "${'$'}p" ] && echo 1 > "${'$'}p" 2>/dev/null
        done
    """.trimIndent()

    // Turn HBM off (backlight returns to normal via the OS on its own).
    private val SCRIPT_OFF = """
        for p in /sys/class/mi_display/disp-DSI-0/disp_param /sys/class/mi_disp/disp-DSI-0/disp_param; do
          [ -w "${'$'}p" ] || continue
          for v in 0xE0000 0x20000 0x00000; do echo "${'$'}v" > "${'$'}p" 2>/dev/null; done
        done
        for p in ${'$'}(find /sys -maxdepth 7 -iname '*hbm*' -perm -u+w 2>/dev/null); do
          [ -f "${'$'}p" ] && echo 0 > "${'$'}p" 2>/dev/null
        done
    """.trimIndent()

    /**
     * Runs [command] as root. Returns true on exit code 0 (or when [check]
     * matches a line of output). Never throws; returns false if su is missing.
     */
    private fun runAsRoot(command: String, check: ((String) -> Boolean)? = null): Boolean {
        return try {
            val process = ProcessBuilder("su").redirectErrorStream(true).start()
            DataOutputStream(process.outputStream).use { os ->
                os.writeBytes(command)
                os.writeBytes("\nexit\n")
                os.flush()
            }
            var matched = false
            if (check != null) {
                process.inputStream.bufferedReader().forEachLine { line ->
                    if (check(line)) matched = true
                }
            }
            val code = process.waitFor()
            if (check != null) matched else code == 0
        } catch (_: Exception) {
            false
        }
    }
}
