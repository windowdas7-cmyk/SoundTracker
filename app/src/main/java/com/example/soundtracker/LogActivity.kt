package com.example.soundtracker

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogActivity : Activity() {
    private lateinit var list: ListView
    private lateinit var count: TextView
    private val rows = ArrayList<LogRow>()
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fmtShort = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private fun kindLabel(k: String): String =
        try {
            EventType.valueOf(k).label
        } catch (e: Exception) {
            k
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "रिकॉर्ड की सूची"
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        setContentView(root)

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        fun btn(text: String, onClick: () -> Unit) {
            val b = Button(this)
            b.text = text
            b.setOnClickListener { onClick() }
            bar.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        btn("रीफ़्रेश") { load() }
        btn("CSV सेव") {
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "text/csv"
            i.putExtra(Intent.EXTRA_TITLE, "soundtracker_log.csv")
            startActivityForResult(i, 2)
        }
        btn("सब मिटाएँ") {
            AlertDialog.Builder(this).setMessage("पूरी सूची मिटा दें?")
                .setPositiveButton("हाँ") { _, _ ->
                    EventLogDb.clear(this)
                    rows.clear()
                    refreshList()
                }
                .setNegativeButton("नहीं", null).show()
        }
        root.addView(bar)

        count = TextView(this)
        count.setPadding(dp(12), dp(4), dp(12), dp(4))
        count.textSize = 12f
        root.addView(count)

        list = ListView(this)
        root.addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        list.adapter = object : ArrayAdapter<LogRow>(this, 0, rows) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = (convertView as? TextView) ?: TextView(context)
                val r = getItem(position)!!
                tv.setPadding(dp(12), dp(8), dp(12), dp(8))
                tv.textSize = 12f
                tv.text = fmtShort.format(Date(r.ts)) + "  ▸ " + kindLabel(r.kind) +
                        "\n" + r.pkg + (if (r.cls.isNotEmpty()) "\n" + r.cls else "") +
                        "\n" + r.info
                return tv
            }
        }
        list.setOnItemClickListener { _, _, pos, _ -> ruleDialog(rows[pos]) }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        rows.clear()
        rows.addAll(EventLogDb.recent(this, 500))
        refreshList()
    }

    private fun refreshList() {
        (list.adapter as ArrayAdapter<*>).notifyDataSetChanged()
        count.text = "ताज़ा ${rows.size} एंट्री (सबसे नई ऊपर)। किसी पर टैप करके उस ऐप/स्क्रीन पर साउंड लगाएँ।"
    }

    private fun ruleDialog(r: LogRow) {
        if (r.pkg.isEmpty() || r.pkg == "logcat") {
            Toast.makeText(this, "इस एंट्री पर नियम नहीं बन सकता", Toast.LENGTH_SHORT).show()
            return
        }
        var freq = 900
        var vol = 80
        val v = LinearLayout(this)
        v.orientation = LinearLayout.VERTICAL
        v.setPadding(dp(20), dp(10), dp(20), 0)

        val rg = RadioGroup(this)
        val rbPkg = RadioButton(this)
        rbPkg.id = View.generateViewId()
        rbPkg.text = "पूरे ऐप पर: ${r.pkg}"
        rg.addView(rbPkg)
        val rbCls = RadioButton(this)
        rbCls.id = View.generateViewId()
        rbCls.text = "सिर्फ़ इस स्क्रीन पर: ${r.cls}"
        if (r.cls.isNotEmpty()) rg.addView(rbCls)
        rbPkg.isChecked = true
        v.addView(rg)

        val fTv = TextView(this)
        fTv.text = "पिच: $freq Hz"
        v.addView(fTv)
        v.addView(seek(1800, freq - 200) {
            freq = it + 200
            fTv.text = "पिच: $freq Hz"
        })
        val vTv = TextView(this)
        vTv.text = "वॉल्यूम: $vol%"
        v.addView(vTv)
        v.addView(seek(100, vol) {
            vol = it
            vTv.text = "वॉल्यूम: $vol%"
        })
        val test = Button(this)
        test.text = "▶ टेस्ट"
        test.setOnClickListener { SoundEngine.playTone(freq, vol, 120) }
        v.addView(test)
        val note = TextView(this)
        note.text = "यह साउंड तब बजेगा जब वह ऐप/स्क्रीन खुलेगी।"
        note.textSize = 12f
        v.addView(note)

        AlertDialog.Builder(this)
            .setTitle("इस पर साउंड लगाएँ")
            .setView(v)
            .setPositiveButton("सेव") { _, _ ->
                val by = if (rbCls.isChecked && r.cls.isNotEmpty()) "cls" else "pkg"
                val value = if (by == "cls") r.cls else r.pkg
                val l = Prefs.rules(this)
                l.removeAll { it.by == by && it.value == value }
                l.add(Rule(by, value, freq, vol))
                Prefs.saveRules(this, l)
                Toast.makeText(this, "नियम सेव हुआ", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("रद्द", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 2 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        exportCsv(uri)
    }

    private fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    private fun exportCsv(uri: Uri) {
        Thread {
            var ok = false
            try {
                val all = EventLogDb.recent(this, 100000).reversed()
                contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { w ->
                    w.write("\uFEFF")
                    w.write("time,event,package,class,info,raw\n")
                    for (r in all) {
                        w.write(
                            listOf(fmt.format(Date(r.ts)), kindLabel(r.kind), r.pkg, r.cls, r.info, r.raw)
                                .joinToString(",") { esc(it) } + "\n"
                        )
                    }
                    ok = true
                }
            } catch (e: Exception) {
            }
            runOnUiThread {
                Toast.makeText(this, if (ok) "CSV सेव हो गई" else "सेव नहीं हो सकी", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }
}
