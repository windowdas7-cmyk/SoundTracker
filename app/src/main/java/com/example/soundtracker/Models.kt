package com.example.soundtracker

import android.content.Context
import android.widget.SeekBar
import org.json.JSONArray
import org.json.JSONObject

/** हर तरह की गतिविधि जिसके लिए अलग साउंड सेट हो सकता है */
enum class EventType(
    val label: String,
    val desc: String,
    val defFreq: Int = 800,
    val debounceMs: Long = 150,
    val durMs: Int = 100,
    val playable: Boolean = true
) {
    TOUCH("टच / क्लिक", "किसी बटन, आइकन या ऐप पर टैप", 880, 60, 60),
    LONG_PRESS("लंबा दबाना", "किसी चीज़ को देर तक दबाना", 440, 300, 160),
    SWIPE_UP("स्वाइप अप", "उँगली नीचे से ऊपर (सूची आगे बढ़ी)", 1250, 180, 70),
    SWIPE_DOWN("स्वाइप डाउन", "उँगली ऊपर से नीचे (सूची पीछे गई)", 620, 180, 70),
    SWIPE_SIDE("साइड स्वाइप", "बाएँ-दाएँ स्क्रॉल या पेज बदलना", 1000, 180, 70),
    APP_OPEN("दूसरा ऐप खुला", "एक ऐप से दूसरे ऐप में गए", 740, 200, 120),
    SCREEN("ऐप में नई स्क्रीन", "ऐप के अंदर नई स्क्रीन / एक्टिविटी खुली", 980, 200, 80),
    BACK("बैक (अनुमानित)", "पिछली स्क्रीन पर लौटना", 330, 200, 120),
    HOME("होम स्क्रीन", "लॉन्चर सामने आया (HOME, या ऐप बंद करके)", 523, 300, 150),
    RECENTS("रीसेंट ऐप्स", "हाल के ऐप्स की स्क्रीन खुली", 660, 300, 150),
    DRAWER("ऐप ड्रॉवर", "सारे ऐप्स की सूची (ड्रॉवर) खुली", 1320, 300, 120),
    NOTIF_PANEL("नोटिफिकेशन पैनल", "ऊपर से पैनल / क्विक सेटिंग्स खुली", 800, 300, 130),
    NOTIF_ARRIVED("नई नोटिफिकेशन", "कोई नई नोटिफिकेशन आई", 1500, 500, 200),
    KEYBOARD("कीबोर्ड खुला", "स्क्रीन पर कीबोर्ड आया", 1100, 500, 80),
    OTHER("अन्य (सिर्फ़ लॉग)", "जो इवेंट पहचाने नहीं गए", 0, 0, 0, false),
    SYSTEM_LOG("सिस्टम लॉग", "Logcat से मिली सिस्टम लाइनें", 0, 0, 0, false)
}

/** अपना नियम: किसी ऐप या स्क्रीन (क्लास) के खुलने पर अलग साउंड */
data class Rule(val by: String, val value: String, val freq: Int, val vol: Int)

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("st", Context.MODE_PRIVATE)

    fun bool(c: Context, k: String, d: Boolean) = sp(c).getBoolean(k, d)
    fun setBool(c: Context, k: String, v: Boolean) = sp(c).edit().putBoolean(k, v).apply()
    fun int(c: Context, k: String, d: Int) = sp(c).getInt(k, d)
    fun setInt(c: Context, k: String, v: Int) = sp(c).edit().putInt(k, v).apply()
    fun str(c: Context, k: String): String? = sp(c).getString(k, null)
    fun setStr(c: Context, k: String, v: String?) {
        if (v == null) sp(c).edit().remove(k).apply() else sp(c).edit().putString(k, v).apply()
    }

    fun master(c: Context) = bool(c, "master", true)
    fun logOn(c: Context) = bool(c, "log", true)
    fun logcatOn(c: Context) = bool(c, "logcat", false)

    fun enabled(c: Context, t: EventType) = bool(c, "en_" + t.name, true)
    fun volume(c: Context, t: EventType) = int(c, "vol_" + t.name, 70)
    fun freq(c: Context, t: EventType) = int(c, "freq_" + t.name, t.defFreq)
    fun custom(c: Context, t: EventType) = str(c, "file_" + t.name)

    fun rules(c: Context): MutableList<Rule> {
        val out = mutableListOf<Rule>()
        val s = str(c, "rules") ?: return out
        try {
            val a = JSONArray(s)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Rule(o.getString("by"), o.getString("value"), o.getInt("freq"), o.getInt("vol")))
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun saveRules(c: Context, list: List<Rule>) {
        val a = JSONArray()
        for (r in list) {
            a.put(JSONObject().put("by", r.by).put("value", r.value).put("freq", r.freq).put("vol", r.vol))
        }
        setStr(c, "rules", a.toString())
    }
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.seek(max: Int, value: Int, onChange: (Int) -> Unit): SeekBar {
    val sb = SeekBar(this)
    sb.max = max
    sb.progress = value
    sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
            if (fromUser) onChange(p)
        }

        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    })
    return sb
}
