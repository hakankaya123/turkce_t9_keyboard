package com.example.comexamplet9keyboard

import android.content.ClipboardManager
import android.content.Context
import android.text.InputType
import android.util.Log
import android.view.inputmethod.EditorInfo

/**
 * Panoyu dinler ve kopyalanan metni ClipStore'a yazar.
 * Hassas kaynaklardan (şifre alanları, şifre yöneticileri, IS_SENSITIVE bayrağı) gelen içerik kaydedilmez.
 */
class ClipManager(
    private val context: Context,
    private val currentEditorInfo: () -> EditorInfo?
) {
    private var lastClip: String? = null
    private val clipboard by lazy { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    private val listener = ClipboardManager.OnPrimaryClipChangedListener { capture(true) }

    fun start() {
        try {
            clipboard.addPrimaryClipChangedListener(listener)
        } catch (e: Exception) {
            Log.w(TAG, "Pano dinleyicisi eklenemedi", e)
        }
    }

    fun stop() {
        try {
            clipboard.removePrimaryClipChangedListener(listener)
        } catch (e: Exception) {
            Log.w(TAG, "Pano dinleyicisi kaldırılamadı", e)
        }
    }

    // Kopyalama sırasında klavyenin bağlı olduğu alan şifre türündeyse veya uygulama bilinen bir
    // şifre yöneticisiyse pano geçmişine alınmaz. (ClipDescription kaynak paketini vermez; bu en iyi tahmindir.)
    private fun sourceLooksSensitive(): Boolean {
        val info = currentEditorInfo() ?: return false
        if (info.packageName in SENSITIVE_PACKAGES) return true
        val t = info.inputType
        if ((t and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT) {
            when (t and InputType.TYPE_MASK_VARIATION) {
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> return true
            }
        }
        if ((t and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER &&
            (t and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        ) return true
        return false
    }

    fun capture(force: Boolean) {
        try {
            val c = clipboard.primaryClip ?: return
            if (c.itemCount == 0) return
            if (c.description?.extras?.getBoolean(
                    "android.content.extra.IS_SENSITIVE",
                    false
                ) == true
            ) return
            if (sourceLooksSensitive()) return
            val t = c.getItemAt(0).coerceToText(context)?.toString() ?: return
            if (!force && t == lastClip) return
            lastClip = t
            if (t.isNotBlank() && t.length <= 5000) ClipStore.add(context, t)
        } catch (e: Exception) {
            Log.w(TAG, "Pano içeriği okunamadı", e)
        }
    }

    private companion object {
        const val TAG = "ClipManager"

        // Bu uygulamalardan kopyalanan metinler pano geçmişine kaydedilmez.
        val SENSITIVE_PACKAGES = setOf(
            "com.x8bit.bitwarden", "com.bitwarden.authenticator",
            "com.onepassword.android", "com.agilebits.onepassword",
            "com.lastpass.lpandroid", "com.dashlane",
            "org.keepassdroid", "com.kunzisoft.keepass.free", "com.kunzisoft.keepass.libre",
            "keepass2android.keepass2android", "org.proton.pass", "me.proton.android.pass",
            "com.google.android.apps.authenticator2", "com.authy.authy", "com.nordpass.android.app.password.manager"
        )
    }
}
