package com.example.soundtracker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class SoundsActivity : Activity() {
    private var pendingType: EventType? = null
    private val fileViews = HashMap<EventType, TextView>()
    private lateinit var rulesBox: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "साउंड सेटिंग"
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(12), dp(12), dp(12), dp(24))
        val sv = ScrollView(this)
        sv.addView(root)
        setContentView(sv)

        val hint = TextView(this)
        hint.text = "हर गतिविधि के लिए: चालू/बंद, वॉल्यूम, बीप की पिच (Hz), या अपनी ऑडियो फाइल (mp3/wav/ogg) चुनें। फाइल चुनने पर पिच की जगह फाइल बजेगी।"
        hint.textSize = 12f
        root.addView(hint)

        for (t in EventType.values()) {
            if (!t.playable) continue
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(10)
            root.addView(row(t), lp)
        }

        val h2 = TextView(this)
        h2.text = "\nमेरे बनाए नियम (ऐप/स्क्रीन के हिसाब से)"
        h2.textSize = 16f
        root.addView(h2)
        val h3 = TextView(this)
        h3.text = "नया नियम बनाने के लिए: रिकॉर्ड सूची खोलें → किसी एंट्री पर टैप करें → \"साउंड लगाएँ\"।"
        h3.textSize = 12f
        root.addView(h3)
        rulesBox = LinearLayout(this)
        rulesBox.orientation = LinearLayout.VERTICAL
        root.addView(rulesBox)
    }

    override fun onResume() {
        super.onResume()
        rulesBox.removeAllViews()
        val rules = Prefs.rules(this)
        if (rules.isEmpty()) {
            val e = TextView(this)
            e.text = "अभी कोई नियम नहीं है।"
            rulesBox.addView(e)
        }
        for (r in rules) {
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            val tx = TextView(this)
            tx.text = (if (r.by == "cls") "स्क्रीन: " else "ऐप: ") + r.value + "\n${r.freq} Hz, वॉल्यूम ${r.vol}%"
            tx.textSize = 12f
            line.addView(tx, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val test = Button(this)
            test.text = "▶"
            test.setOnClickListener { SoundEngine.playTone(r.freq, r.vol, 120) }
            line.addView(test)
            val del = Button(this)
            del.text = "हटाएँ"
            del.setOnClickListener {
                val l = Prefs.rules(this)
                l.removeAll { it == r }
                Prefs.saveRules(this, l)
                onResume()
            }
            line.addView(del)
            rulesBox.addView(line)
        }
    }

    private fun fileText(t: EventType): String {
        val u = Prefs.custom(this, t)
        return if (u == null) "साउंड: जनरेट किया हुआ बीप" else "साउंड: फाइल — " + (Uri.parse(u).lastPathSegment ?: u)
    }

    private fun row(t: EventType): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(10), dp(10), dp(10), dp(10))
        box.setBackgroundColor(0x14000000)

        val sw = Switch(this)
        sw.text = t.label
        sw.textSize = 17f
        sw.isChecked = Prefs.enabled(this, t)
        sw.setOnCheckedChangeListener { _, v -> Prefs.setBool(this, "en_" + t.name, v) }
        box.addView(sw)

        val d = TextView(this)
        d.text = t.desc
        d.textSize = 12f
        box.addView(d)

        val volTv = TextView(this)
        volTv.text = "वॉल्यूम: ${Prefs.volume(this, t)}%"
        box.addView(volTv)
        box.addView(seek(100, Prefs.volume(this, t)) {
            Prefs.setInt(this, "vol_" + t.name, it)
            volTv.text = "वॉल्यूम: $it%"
        })

        val fTv = TextView(this)
        fTv.text = "पिच: ${Prefs.freq(this, t)} Hz"
        box.addView(fTv)
        box.addView(seek(1800, Prefs.freq(this, t) - 200) {
            Prefs.setInt(this, "freq_" + t.name, it + 200)
            fTv.text = "पिच: ${it + 200} Hz"
        })

        val fileTv = TextView(this)
        fileTv.text = fileText(t)
        fileTv.textSize = 12f
        fileViews[t] = fileTv
        box.addView(fileTv)

        val btns = LinearLayout(this)
        btns.orientation = LinearLayout.HORIZONTAL
        val test = Button(this)
        test.text = "▶ टेस्ट"
        test.setOnClickListener { SoundEngine.playType(this, t) }
        val pick = Button(this)
        pick.text = "🎵 फाइल"
        pick.setOnClickListener {
            pendingType = t
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "audio/*"
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            startActivityForResult(i, 1)
        }
        val clear = Button(this)
        clear.text = "✖ फाइल हटाएँ"
        clear.setOnClickListener {
            Prefs.setStr(this, "file_" + t.name, null)
            fileTv.text = fileText(t)
        }
        btns.addView(test)
        btns.addView(pick)
        btns.addView(clear)
        box.addView(btns)
        return box
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 1 || resultCode != RESULT_OK) return
        val u = data?.data ?: return
        val t = pendingType ?: return
        try {
            contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
        }
        Prefs.setStr(this, "file_" + t.name, u.toString())
        fileViews[t]?.text = fileText(t)
    }
}
