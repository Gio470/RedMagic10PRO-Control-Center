package com.elitedarkkaiser.redmagic.ui

import android.view.View
import androidx.activity.ComponentActivity
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Lets a ComposeView live inside a plain framework [android.app.AlertDialog].
 *
 * A ComposeView resolves its lifecycle by walking up the view tree, and a framework dialog builds
 * its own decor view that carries none of the activity's owners -- androidx's ComponentDialog, which
 * AppCompat and Material's dialogs are built on, is what normally supplies them. Without this a
 * Compose child in one of those dialogs throws "ViewTreeLifecycleOwner not found" the moment it
 * attaches.
 *
 * Call it on the root view *before* handing it to the dialog: the owners are stored as tags, so
 * they are found by a child that attaches later.
 */
fun View.hostComposeFrom(activity: ComponentActivity) {
    setViewTreeLifecycleOwner(activity)
    setViewTreeViewModelStoreOwner(activity)
    setViewTreeSavedStateRegistryOwner(activity)
}
