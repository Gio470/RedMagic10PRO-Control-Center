package com.elitedarkkaiser.redmagic.hardware

import com.elitedarkkaiser.redmagic.RootShell

object RootProvider {
    fun hasRoot(): Boolean {
        return RootShell.hasRoot()
    }

    fun exec(command: String): Boolean {
        return RootShell.exec(command)
    }

    fun output(command: String): String? {
        return RootShell.execForOutput(command)
    }
}
