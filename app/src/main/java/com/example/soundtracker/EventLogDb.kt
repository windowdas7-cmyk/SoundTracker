package com.example.soundtracker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.concurrent.Executors

class LogRow(
    val id: Long, val ts: Long, val kind: String,
    val pkg: String, val cls: String, val info: String, val raw: String
)

object EventLogDb {
    private class Helper(c: Context) : SQLiteOpenHelper(c.applicationContext, "log.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE logs(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, " +
                        "kind TEXT, pkg TEXT, cls TEXT, info TEXT, raw TEXT)"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}
    }

    private var helper: Helper? = null
    private val exec = Executors.newSingleThreadExecutor()
    private var counter = 0

    @Synchronized
    private fun h(c: Context): Helper {
        if (helper == null) helper = Helper(c)
        return helper!!
    }

    fun add(c: Context, kind: String, pkg: String, cls: String, info: String, raw: String) {
        val ts = System.currentTimeMillis()
        exec.execute {
            try {
                val db = h(c).writableDatabase
                val v = ContentValues()
                v.put("ts", ts)
                v.put("kind", kind)
                v.put("pkg", pkg)
                v.put("cls", cls)
                v.put("info", info)
                v.put("raw", raw)
                db.insert("logs", null, v)
                counter++
                if (counter % 300 == 0) {
                    db.execSQL("DELETE FROM logs WHERE id < (SELECT MAX(id) FROM logs) - 20000")
                }
            } catch (e: Exception) {
            }
        }
    }

    fun recent(c: Context, limit: Int): List<LogRow> {
        val out = ArrayList<LogRow>()
        try {
            val cur = h(c).readableDatabase.rawQuery(
                "SELECT id,ts,kind,pkg,cls,info,raw FROM logs ORDER BY id DESC LIMIT ?",
                arrayOf(limit.toString())
            )
            while (cur.moveToNext()) {
                out.add(
                    LogRow(
                        cur.getLong(0), cur.getLong(1), cur.getString(2) ?: "",
                        cur.getString(3) ?: "", cur.getString(4) ?: "",
                        cur.getString(5) ?: "", cur.getString(6) ?: ""
                    )
                )
            }
            cur.close()
        } catch (e: Exception) {
        }
        return out
    }

    fun clear(c: Context) {
        exec.execute {
            try {
                h(c).writableDatabase.execSQL("DELETE FROM logs")
            } catch (e: Exception) {
            }
        }
    }
}
