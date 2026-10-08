package com.elitedarkkaiser.redmagic.ui

/**
 * A handle on something the app opened: an [Expander] unfolded under a row, or -- when there is no
 * row to unfold under -- a [PageHost] screen.
 *
 * One type for both, because the caller does not care which it got. Every screen in this app opens
 * one of these through [M3Dialog.show] and closes it by name, and they have now been a bottom
 * sheet, a page and an expander without a single call site learning the difference.
 *
 * The method names are `Dialog`'s, since that is what they all said when these were sheets.
 */
class Panel internal constructor(
    /** Whether backing out of it closes it. False for the ones that are waiting for an answer. */
    internal val cancelable: Boolean,
    private val remove: (Panel) -> Unit
) {
    private var onDismiss: (() -> Unit)? = null
    private var onCancel: (() -> Unit)? = null
    private var gone = false

    fun setOnDismissListener(listener: () -> Unit) { onDismiss = listener }

    fun setOnCancelListener(listener: () -> Unit) { onCancel = listener }

    /** Closed deliberately: a Done or Close button, or the work finishing. */
    fun dismiss() = close(cancelled = false)

    /** Backed out of, which is what tapping outside a sheet used to be. */
    internal fun cancel() = close(cancelled = true)

    val isShowing: Boolean get() = !gone

    private fun close(cancelled: Boolean) {
        if (gone) return
        gone = true
        if (cancelled) onCancel?.invoke()
        remove(this)
        onDismiss?.invoke()
    }
}
