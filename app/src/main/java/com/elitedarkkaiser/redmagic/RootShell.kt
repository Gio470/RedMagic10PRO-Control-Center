package com.elitedarkkaiser.redmagic

import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStream
import java.io.InputStreamReader

object RootShell {

    fun hasRoot(): Boolean {
        val output = execForOutput("id")
        return output?.contains("uid=0") == true
    }

    fun exec(command: String): Boolean {
        return execInteractive(command) || execSuC(command)
    }

    fun execForOutput(command: String): String? {
        return execForOutputInteractive(command) ?: execForOutputSuC(command)
    }

    /**
     * Reads a stream to the end on a thread of its own, so nothing upstream of [drain] can ever
     * be the thing that fills the pipe and stalls the child.
     *
     * `Process.waitFor()` cannot return while either of the child's stdout or stderr pipes is
     * full, because the child itself is then blocked trying to write to it. A short command's
     * output never approaches that -- a `settings put` or a `pm disable` prints nothing worth
     * mentioning -- so every caller of [exec] up to now got away with never reading either
     * stream at all. A script that chains a dozen `cmd`/`dumpsys` calls is a different animal:
     * several of them print freely, and it takes only one pipe filling (commonly 64KB, sometimes
     * less) to deadlock the wait forever -- which, to whoever is waiting on it, looks exactly
     * like the button that started it simply doing nothing.
     *
     * Both stdout and stderr have to be drained on separate threads rather than one after the
     * other, for the same reason one stream alone is not enough: reading stdout to completion
     * before touching stderr blocks on stdout for as long as the child has more to say there,
     * while a child that is instead waiting on a full stderr pipe never gets the chance to finish
     * writing stdout -- neither read ends, and the same deadlock happens one stream later.
     */
    private fun drain(stream: InputStream): Thread {
        val reader = BufferedReader(InputStreamReader(stream))
        val thread = Thread { runCatching { while (reader.readLine() != null) { /* discarded */ } } }
        thread.isDaemon = true
        thread.start()
        return thread
    }

    /** Same as [drain], but keeps what it read instead of discarding it. */
    private fun collect(stream: InputStream): Pair<Thread, StringBuilder> {
        val builder = StringBuilder()
        val reader = BufferedReader(InputStreamReader(stream))
        val thread = Thread {
            runCatching {
                var line: String?
                while (true) {
                    line = reader.readLine() ?: break
                    if (builder.isNotEmpty()) builder.append('\n')
                    builder.append(line)
                }
            }
        }
        thread.isDaemon = true
        thread.start()
        return thread to builder
    }

    private fun execInteractive(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)

            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()

            val out = drain(process.inputStream)
            val err = drain(process.errorStream)
            val result = process.waitFor() == 0
            out.join()
            err.join()
            result
        } catch (e: Exception) {
            false
        }
    }

    private fun execSuC(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val out = drain(process.inputStream)
            val err = drain(process.errorStream)
            val result = process.waitFor() == 0
            out.join()
            err.join()
            result
        } catch (e: Exception) {
            false
        }
    }

    private fun execForOutputInteractive(command: String): String? {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)

            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()

            val (outThread, stdout) = collect(process.inputStream)
            val (errThread, stderr) = collect(process.errorStream)
            process.waitFor()
            outThread.join()
            errThread.join()

            val result = buildString {
                if (stdout.isNotBlank()) append(stdout)
                if (stderr.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append(stderr)
                }
            }.trim()

            result.ifEmpty { null }
        } catch (e: Exception) {
            null
        }
    }

    private fun execForOutputSuC(command: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))

            val (outThread, stdout) = collect(process.inputStream)
            val (errThread, stderr) = collect(process.errorStream)
            process.waitFor()
            outThread.join()
            errThread.join()

            val result = buildString {
                if (stdout.isNotBlank()) append(stdout)
                if (stderr.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append(stderr)
                }
            }.trim()

            result.ifEmpty { null }
        } catch (e: Exception) {
            null
        }
    }
}
