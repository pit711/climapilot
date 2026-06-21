package com.climapilot.free.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log

/**
 * EN: Thin wrapper around the phone's IR blaster (ConsumerIrManager) for the IR-remote mode. transmit()
 *     blocks for the pattern duration (~190 ms), so callers run it off the main thread. Needs the
 *     android.permission.TRANSMIT_IR permission (declared in the manifest).
 * DE: Dünne Hülle um den IR-Blaster des Handys (ConsumerIrManager) für den IR-Fernbedienungs-Modus.
 *     transmit() blockiert für die Musterdauer (~190 ms), Aufrufer führen es daher abseits des
 *     Main-Threads aus. Braucht die android.permission.TRANSMIT_IR (im Manifest deklariert).
 */
object IrRemote {
    private const val TAG = "MideaIR"

    /** EN: True if this phone has a usable IR emitter. DE: True, wenn dieses Handy einen nutzbaren IR-Sender hat. */
    fun hasEmitter(ctx: Context): Boolean {
        val ir = ctx.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        return ir?.hasIrEmitter() == true
    }

    /** EN: Transmit a Midea IR pattern (38 kHz). Returns true on success. DE: Ein Midea-IR-Muster senden (38 kHz). True bei Erfolg. */
    fun transmit(ctx: Context, pattern: IntArray): Boolean {
        val ir = ctx.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        if (ir == null || !ir.hasIrEmitter()) {
            Log.w(TAG, "no IR emitter"); return false
        }
        return runCatching { ir.transmit(MideaIr.CARRIER_HZ, pattern) }
            .onFailure { Log.e(TAG, "transmit failed", it) }
            .isSuccess
    }
}
