@file:Suppress("DEPRECATION")

/*
 * ProgressDialogManager
 *
 * Centralised manager for progress dialogs to avoid race conditions when multiple
 * overlapping operations attempt to show/dismiss progress dialogs. Uses owner
 * tokens and a reference-counting approach to guarantee the dialog is only
 * dismissed when the last owner hides it.
 */

package com.ichi2.anki

import android.app.Activity
import android.app.ProgressDialog
import android.content.DialogInterface
import android.view.WindowManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ProgressDialogManager {
    private val owners = ConcurrentHashMap.newKeySet<String>()
    private var dialog: ProgressDialog? = null
    private var showJob: Job? = null
    private var lastContext: Activity? = null

    /**
     * Request to show a progress dialog for the given owner. Returns a ProgressDialog
     * instance (may not be shown yet if the delay has not elapsed).
     */
    fun showWithDelay(
        owner: String,
        context: Activity,
        delayMillis: Long,
        onCancel: (() -> Unit)?,
        manualCancelButton: Int?,
    ): ProgressDialog {
        owners.add(owner)

        synchronized(this) {
            if (dialog == null) {
                dialog =
                    ProgressDialog(context, R.style.AppCompatProgressDialogStyle).apply {
                        setCancelable(onCancel != null)
                        if (manualCancelButton != null) {
                            setCancelable(false)
                            setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(manualCancelButton)) { _, _ ->
                                Timber.i("Progress dialog cancelled via cancel button")
                                onCancel?.invoke()
                            }
                        } else {
                            onCancel?.let {
                                setOnCancelListener { _ ->
                                    Timber.i("Progress dialog cancelled via cancel listener")
                                    it()
                                }
                            }
                        }
                    }
            }

            // schedule show after delay if not already scheduled/shown
            if (showJob == null) {
                showJob =
                    AnkiDroidApp.applicationScope.launch {
                        try {
                            kotlinx.coroutines.delay(delayMillis)
                            // If still have owners, show dialog on UI thread
                            if (owners.isNotEmpty()) {
                                context.runOnUiThread {
                                    try {
                                        // prevent touches
                                        context.window.setFlags(
                                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                                        )
                                        dialog?.show()
                                        Timber.i("ProgressDialogManager: shown dialog for owners=%d", owners.size)
                                    } catch (e: Exception) {
                                        Timber.w(e, "ProgressDialogManager: failed to show dialog")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Timber.w(e)
                        } finally {
                            synchronized(this@ProgressDialogManager) {
                                showJob = null
                            }
                        }
                    }
            }
            lastContext = context
            return dialog!!
        }
    }

    /**
     * Hide request for the owner. Dialog is dismissed when the last owner hides it.
     */
    fun hide(owner: String) {
        owners.remove(owner)
        synchronized(this) {
            if (owners.isEmpty()) {
                try {
                    // cancel any pending show
                    showJob?.cancel()
                    showJob = null
                    val d = dialog
                    if (d != null) {
                        try {
                            d.dismiss()
                        } catch (e: Exception) {
                            Timber.w(e, "ProgressDialogManager: failed to dismiss dialog")
                        }
                        dialog = null
                    }
                } finally {
                    // clear touch blocking flag on UI thread if possible using the last activity context
                    try {
                        lastContext?.runOnUiThread {
                            try {
                                lastContext?.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                            } catch (e: Exception) {
                                Timber.w(e, "ProgressDialogManager: failed to clear not-touchable flag")
                            }
                        }
                        lastContext = null
                    } catch (e: Exception) {
                        Timber.w(e)
                    }
                }
            }
        }
    }

    /** Helper to create a new owner token */
    fun newOwnerToken(): String = UUID.randomUUID().toString()
}
