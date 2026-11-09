package com.music.bitchord.karaoke.ai

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Child-JVM entry point for [MdxStreamingLoadTest]. The parent test forks
 * this main with `-Xmx384m` — the exact heap growth limit of the phone
 * that reported the dev2 OOM — and a 5-minute song:
 *
 *  - mode NEW: [MdxSeparator.separateToFiles] must complete.
 *  - mode OLD: the pre-dev4 in-memory pipeline (load the whole song into
 *    a FloatArray, then [MdxSeparator.separateFull]) must die with
 *    OutOfMemoryError; exit code 3 marks that expectation.
 *
 * Both modes print their sampled peak live heap and wall time as
 * `CHILD-<mode> ...` lines for the parent to report.
 */
object MdxStreamingChildMain {

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args[0]
        val model = File(args[1])
        val pcm = File(args[2])
        val frames = args[3].toLong()
        val workDir = File(args[4]).apply { mkdirs() }

        val peakUsed = AtomicLong(0)
        val stop = AtomicBoolean(false)
        val sampler = thread(isDaemon = true) {
            val rt = Runtime.getRuntime()
            while (!stop.get()) {
                val used = rt.totalMemory() - rt.freeMemory()
                peakUsed.accumulateAndGet(used) { a, b -> maxOf(a, b) }
                Thread.sleep(25)
            }
        }

        val t0 = System.nanoTime()
        try {
            MdxSeparator(model).use { sep ->
                if (mode == "NEW") {
                    val ok = sep.separateToFiles(
                        pcm, frames,
                        File(workDir, "vocal.wav"), File(workDir, "inst.wav"),
                        workDir,
                    )
                    println("CHILD-NEW completed=$ok")
                    check(ok) { "streaming separation did not complete" }
                } else {
                    val data = FloatArray((frames * 2).toInt())
                    pcm.inputStream().buffered().use { ins ->
                        val buf = ByteArray(1 shl 20)
                        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
                        var idx = 0
                        while (true) {
                            val r = ins.read(buf)
                            if (r < 0) break
                            var k = 0
                            while (k < r) {
                                data[idx++] = bb.getFloat(k)
                                k += 4
                            }
                        }
                    }
                    println("CHILD-OLD loaded song into heap")
                    val (vocal, inst) = sep.separateFull(data)
                    Wav16FileWriter(File(workDir, "vocal.wav"), frames).use {
                        it.write(vocal, vocal.size)
                    }
                    Wav16FileWriter(File(workDir, "inst.wav"), frames).use {
                        it.write(inst, inst.size)
                    }
                    println("CHILD-OLD completed")
                }
            }
        } catch (oome: OutOfMemoryError) {
            println("CHILD-$mode OOM ${oome.message}")
            exitProcess(3)
        }
        stop.set(true)
        sampler.join(2000)
        val secs = (System.nanoTime() - t0) / 1e9
        println("CHILD-$mode peakUsedMB=${peakUsed.get() / 1048576} elapsedSec=$secs")
    }
}
