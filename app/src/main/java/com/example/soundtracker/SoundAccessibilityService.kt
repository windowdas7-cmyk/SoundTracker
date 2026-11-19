package com.example.soundtracker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

class SoundAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: SoundAccessibilityService? = null
    }

    private var launchers: Set<String> = emptySet()
    private var lastPkg = ""
    private var lastCls = ""
    private var prevCls = ""
    private var lastScrollY = 0
    private val lastPlay = HashMap<String, Long>()

    @Volatile
    private var logcatProc: Process? = null

    override fun onServiceConnected() {
        instance = this
        try {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            launchers = packageManager.queryIntentActivities(i, 0)
                .map { it.activityInfo.packageName }.toSet()
        } catch (e: Exception) {
        }
        startLogcat()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        stopLogcat()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null) return
        val pkg = e.packageName?.toString() ?: ""
        if (pkg == packageName) return // अपने ही ऐप के इवेंट छोड़ दें
        val cls = e.className?.toString() ?: ""
        val raw = AccessibilityEvent.eventTypeToString(e.eventType)

        when (e.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED ->
                emit(EventType.TOUCH, pkg, cls, "टच / क्लिक हुआ ($cls)", raw)

            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED ->
                emit(EventType.LONG_PRESS, pkg, cls, "लंबा दबाया गया ($cls)", raw)

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val dx = e.scrollDeltaX
                var dy = e.scrollDeltaY
                if (dy == 0 && dx == 0) dy = e.scrollY - lastScrollY
                lastScrollY = e.scrollY
                when {
                    dy != 0 && Math.abs(dy) >= Math.abs(dx) ->
                        if (dy > 0) emit(EventType.SWIPE_UP, pkg, cls, "ऊपर की ओर स्वाइप/स्क्रॉल", raw)
                        else emit(EventType.SWIPE_DOWN, pkg, cls, "नीचे की ओर स्वाइप/स्क्रॉल", raw)

                    dx != 0 -> emit(EventType.SWIPE_SIDE, pkg, cls, "साइड में स्वाइप/स्क्रॉल", raw)
                    else -> emit(EventType.OTHER, pkg, cls, "स्क्रॉल (दिशा पता नहीं)", raw)
                }
            }

            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED ->
                emit(EventType.NOTIF_ARRIVED, pkg, cls, "ऐप $pkg की नोटिफिकेशन/टोस्ट आया", raw)

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindow(pkg, cls, raw)

            else -> {
                // बाकी इवेंट (जैसे टेक्स्ट बदलना) जानबूझकर रिकॉर्ड नहीं किए जाते, ताकि निजी जानकारी न जाए
            }
        }
    }

    private fun handleWindow(pkg: String, cls: String, raw: String) {
        val low = cls.lowercase()
        val isSystemUi = pkg == "com.android.systemui"

        if (low.contains("softinputwindow") || low.contains("inputmethod")) {
            emit(EventType.KEYBOARD, pkg, cls, "कीबोर्ड सामने आया", raw)
            return
        }

        if (isSystemUi) {
            when {
                low.contains("recents") ->
                    emit(EventType.RECENTS, pkg, cls, "SystemUI: रीसेंट ऐप्स स्क्रीन", raw)

                low.contains("shade") || low.contains("notification") || low.contains("statusbar") ||
                        low.contains("quicksettings") || low.contains("panel") ->
                    emit(EventType.NOTIF_PANEL, pkg, cls, "SystemUI: नोटिफिकेशन पैनल / क्विक सेटिंग्स", raw)

                else -> emit(EventType.OTHER, pkg, cls, "SystemUI की कोई और विंडो", raw)
            }
            return
        }

        // dialog / popup जैसी सामान्य विंडो: सिर्फ़ लॉग
        if (low.startsWith("android.widget.") || low.startsWith("android.view.")) {
            emit(EventType.OTHER, pkg, cls, "पॉपअप / डायलॉग विंडो", raw)
            return
        }

        if (pkg in launchers) {
            when {
                low.contains("recents") -> emit(EventType.RECENTS, pkg, cls, "लॉन्चर की रीसेंट्स स्क्रीन", raw)
                low.contains("allapps") || low.contains("drawer") ->
                    emit(EventType.DRAWER, pkg, cls, "ऐप ड्रॉवर खुला", raw)

                lastPkg != pkg -> emit(EventType.HOME, pkg, cls, "होम स्क्रीन आई (HOME / बैक / ऐप बंद)", raw, true)
                else -> emit(EventType.OTHER, pkg, cls, "लॉन्चर के अंदर स्क्रीन बदली", raw)
            }
            lastPkg = pkg
            lastCls = cls
            prevCls = ""
            return
        }

        // सामान्य ऐप
        when {
            pkg != lastPkg -> emit(EventType.APP_OPEN, pkg, cls, "ऐप खुला / बदला: $pkg", raw, true)
            cls == prevCls && cls != lastCls ->
                emit(EventType.BACK, pkg, cls, "पिछली स्क्रीन पर लौटे — संभवतः BACK दबा", raw, true)

            cls != lastCls -> emit(EventType.SCREEN, pkg, cls, "ऐप के अंदर नई स्क्रीन: $cls", raw, true)
            else -> emit(EventType.OTHER, pkg, cls, "वही स्क्रीन दोबारा", raw)
        }
        if (cls != lastCls || pkg != lastPkg) {
            prevCls = if (pkg == lastPkg) lastCls else ""
            lastCls = cls
            lastPkg = pkg
        }
    }

    /** रिकॉर्ड करो, फिर (अगर चालू हो) साउंड बजाओ */
    private fun emit(t: EventType, pkg: String, cls: String, info: String, raw: String, checkRules: Boolean = false) {
        if (Prefs.logOn(this)) EventLogDb.add(this, t.name, pkg, cls, info, raw)
        if (!Prefs.master(this)) return

        if (checkRules) {
            var hit = false
            for (r in Prefs.rules(this)) {
                if ((r.by == "cls" && r.value == cls) || (r.by == "pkg" && r.value == pkg)) {
                    if (throttle("rule:${r.by}:${r.value}", 200)) SoundEngine.playTone(r.freq, r.vol, 120)
                    hit = true
                }
            }
            if (hit) return
        }

        if (!t.playable || !Prefs.enabled(this, t)) return
        if (!throttle(t.name, t.debounceMs)) return
        SoundEngine.playType(this, t)
    }

    private fun throttle(key: String, ms: Long): Boolean {
        val now = SystemClock.uptimeMillis()
        val last = lastPlay[key] ?: 0L
        if (now - last < ms) return false
        lastPlay[key] = now
        return true
    }

    // ---------- वैकल्पिक: Logcat से सिस्टम-स्तर की जानकारी ----------
    fun restartLogcat() {
        stopLogcat()
        startLogcat()
    }

    private fun startLogcat() {
        if (!Prefs.logcatOn(this)) return
        if (checkSelfPermission("android.permission.READ_LOGS") != PackageManager.PERMISSION_GRANTED) return
        if (logcatProc != null) return
        val keys = listOf(
            "START u0", "Displayed", "moveTaskToFront", "Resuming", "Pausing",
            "finish", "Destroying", "setFocusedRootTask", "Focus"
        )
        val t = Thread {
            try {
                val p = Runtime.getRuntime().exec(
                    arrayOf(
                        "logcat", "-v", "brief", "-T", "1",
                        "ActivityTaskManager:I", "WindowManager:I", "ActivityManager:I", "*:S"
                    )
                )
                logcatProc = p
                p.inputStream.bufferedReader().useLines { seq ->
                    for (line in seq) {
                        if (logcatProc == null) break
                        if (keys.any { line.contains(it) }) {
                            EventLogDb.add(this, EventType.SYSTEM_LOG.name, "logcat", "", line.take(400), "LOGCAT")
                        }
                    }
                }
            } catch (e: Exception) {
            } finally {
                logcatProc = null
            }
        }
        t.isDaemon = true
        t.start()
    }

    private fun stopLogcat() {
        try {
            logcatProc?.destroy()
        } catch (e: Exception) {
        }
        logcatProc = null
    }
}
