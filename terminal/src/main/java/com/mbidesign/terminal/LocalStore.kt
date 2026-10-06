package com.mbidesign.terminal

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

class LocalStore(context: Context) : SQLiteOpenHelper(context, "mbi-terminal.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE workers(id TEXT PRIMARY KEY, uid TEXT NOT NULL, name TEXT NOT NULL, active INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE events(id TEXT PRIMARY KEY, employeeId TEXT NOT NULL, uid TEXT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, time INTEGER NOT NULL, method TEXT NOT NULL, status TEXT NOT NULL, deviceUid TEXT NOT NULL, message TEXT NOT NULL, clockTrusted INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX events_worker_time ON events(employeeId,time)")
        db.execSQL("CREATE INDEX events_status_time ON events(status,time)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    @Synchronized fun setWorkers(workers: List<Worker>) {
        val db = writableDatabase; db.beginTransaction()
        try {
            db.execSQL("UPDATE workers SET active=0")
            workers.forEach { w -> db.insertWithOnConflict("workers", null, ContentValues().apply {
                put("id", w.id); put("uid", w.uid); put("name", w.name); put("active", if (w.active) 1 else 0)
            }, SQLiteDatabase.CONFLICT_REPLACE) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun workers(): List<Worker> = readableDatabase.rawQuery("SELECT * FROM workers WHERE active=1 ORDER BY name COLLATE NOCASE", null).use { c ->
        buildList { while (c.moveToNext()) add(worker(c)) }
    }
    fun worker(id: String): Worker? = readableDatabase.rawQuery("SELECT * FROM workers WHERE id=?", arrayOf(id)).use { c -> if (c.moveToFirst()) worker(c) else null }
    private fun worker(c: Cursor) = Worker(c.getString(0), c.getString(1), c.getString(2), c.getInt(3) == 1)
    fun events(id: String, since: Long = 0, until: Long = Long.MAX_VALUE): List<AttendanceEvent> = readableDatabase.rawQuery(
        "SELECT * FROM events WHERE employeeId=? AND time>=? AND time<? AND status!='duplicate' ORDER BY time,id",
        arrayOf(id, since.toString(), until.toString())
    ).use { c -> buildList { while (c.moveToNext()) add(event(c)) } }
    fun pending(includeConflict: Boolean = false): List<AttendanceEvent> = readableDatabase.rawQuery(
        "SELECT * FROM events WHERE status IN (${if (includeConflict) "'pending','conflict'" else "'pending'"}) ORDER BY time,id", null
    ).use { c -> buildList { while (c.moveToNext()) add(event(c)) } }
    private fun event(c: Cursor) = AttendanceEvent(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getLong(5), c.getString(6), c.getString(7), c.getString(8), c.getString(9), c.getInt(10) == 1)
    @Synchronized fun saveVerified(w: Worker, type: String, time: Long, deviceUid: String, trusted: Boolean, method: String = "face"): AttendanceEvent {
        require(w.active) { "Вработениот е блокиран." }
        val db = writableDatabase; db.beginTransaction()
        try {
            require(Attendance.next(Attendance.state(events(w.id, Attendance.dayStart(time))), type) != null) { "Ова дејство веќе е запишано или не одговара на тековната состојба." }
            val ev = AttendanceEvent(UUID.randomUUID().toString(), w.id, w.uid, w.name, type, time, method, "pending", deviceUid, "", trusted)
            db.insertOrThrow("events", null, values(ev)); db.setTransactionSuccessful(); return ev
        } finally { db.endTransaction() }
    }
    @Synchronized fun importRemote(events: List<AttendanceEvent>) {
        val db = writableDatabase; db.beginTransaction()
        try {
            events.forEach { db.insertWithOnConflict("events", null, values(it.copy(status = "synced")), SQLiteDatabase.CONFLICT_IGNORE) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun replaceRemoteWindow(since: Long, events: List<AttendanceEvent>) {
        val db = writableDatabase; db.beginTransaction()
        try {
            // Apply administrator edits and deletions while keeping unacknowledged local events.
            db.delete("events", "status='synced' AND time>=?", arrayOf(since.toString()))
            events.forEach { db.insertWithOnConflict("events", null, values(it.copy(status="synced")), SQLiteDatabase.CONFLICT_IGNORE) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun status(id: String, status: String, message: String = "") {
        writableDatabase.update("events", ContentValues().apply { put("status", status); put("message", message) }, "id=?", arrayOf(id))
    }
    private fun values(e: AttendanceEvent) = ContentValues().apply {
        put("id", e.id); put("employeeId", e.employeeId); put("uid", e.uid); put("name", e.name)
        put("type", e.type); put("time", e.time); put("method", e.method); put("status", e.status)
        put("deviceUid", e.deviceUid); put("message", e.message); put("clockTrusted", if (e.clockTrusted) 1 else 0)
    }
}
