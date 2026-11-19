package com.example.soundtracker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "साउंड ट्रैकर"
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(16), dp(16), dp(16))
        val sv = ScrollView(this)
        sv.addView(root)
        setContentView(sv)

        fun tv(text: String, size: Float = 14f): TextView {
            val t = TextView(this)
            t.text = text
            t.textSize = size
            t.setPadding(0, dp(8), 0, dp(8))
            return t
        }

        fun btn(text: String, onClick: () -> Unit): Button {
            val b = Button(this)
            b.text = text
            b.setOnClickListener { onClick() }
            return b
        }

        status = tv("", 16f)
        root.addView(status)

        root.addView(btn("1) एक्सेसिबिलिटी सेवा चालू करें") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(tv("सूची में \"साउंड ट्रैकर सेवा\" खोलकर चालू करें। अगर Android 13+ पर \"Restricted setting\" दिखे, तो नीचे वाला बटन दबाकर ऐप जानकारी खोलें → ⋮ (तीन बिंदु) → \"Restricted settings की अनुमति दें\", फिर दोबारा सेवा चालू करें।", 12f))
        root.addView(btn("ऐप जानकारी खोलें (Restricted settings के लिए)") {
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            i.data = Uri.parse("package:$packageName")
            startActivity(i)
        })

        val swMaster = Switch(this)
        swMaster.text = "साउंड चालू (मास्टर स्विच)"
        swMaster.textSize = 16f
        swMaster.isChecked = Prefs.master(this)
        swMaster.setOnCheckedChangeListener { _, v -> Prefs.setBool(this, "master", v) }
        root.addView(swMaster)

        val swLog = Switch(this)
        swLog.text = "गतिविधियाँ रिकॉर्ड करें"
        swLog.textSize = 16f
        swLog.isChecked = Prefs.logOn(this)
        swLog.setOnCheckedChangeListener { _, v -> Prefs.setBool(this, "log", v) }
        root.addView(swLog)

        val swLogcat = Switch(this)
        swLogcat.text = "सिस्टम Logcat भी रिकॉर्ड करें (वैकल्पिक)"
        swLogcat.textSize = 16f
        swLogcat.isChecked = Prefs.logcatOn(this)
        swLogcat.setOnCheckedChangeListener { _, v ->
            Prefs.setBool(this, "logcat", v)
            SoundAccessibilityService.instance?.restartLogcat()
        }
        root.addView(swLogcat)
        val adb = tv("Logcat के लिए एक बार कंप्यूटर से यह कमांड चलाएँ (USB debugging चालू करके), फिर सेवा बंद करके दोबारा चालू करें:\nadb shell pm grant $packageName android.permission.READ_LOGS", 12f)
        adb.setTextIsSelectable(true)
        root.addView(adb)

        root.addView(btn("🔊 हर इवेंट का साउंड / वॉल्यूम सेट करें") {
            startActivity(Intent(this, SoundsActivity::class.java))
        })
        root.addView(btn("📋 रिकॉर्ड की गई गतिविधियों की सूची") {
            startActivity(Intent(this, LogActivity::class.java))
        })
        root.addView(tv("गोपनीयता: ऐप सिर्फ़ इवेंट का प्रकार, ऐप/स्क्रीन का नाम और समय सेव करता है। आप जो टाइप करते हैं या स्क्रीन की सामग्री सेव नहीं होती। सारा डेटा सिर्फ़ आपके फोन में रहता है।", 12f))
    }

    override fun onResume() {
        super.onResume()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val on = enabled.contains("$packageName/")
        status.text = if (on) "✅ सेवा चालू है — फोन में कहीं भी साउंड बजेंगे" else "⚠️ सेवा अभी बंद है — नीचे बटन से चालू करें"
    }
}
