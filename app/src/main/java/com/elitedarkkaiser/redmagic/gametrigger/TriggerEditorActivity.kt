package com.elitedarkkaiser.redmagic.gametrigger

import android.app.Activity
import android.os.Bundle

/** Backwards-compatible entry point; target placement now happens over the selected game. */
class TriggerEditorActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NativeTgkStorage.getProfile(this, intent.getStringExtra(EXTRA_PACKAGE).orEmpty())?.let {
            TriggerEditorService.open(this, it)
        }
        finish()
    }
    companion object {
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_LABEL = "label"
    }
}
