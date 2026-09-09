package mk.wf.labelscanner

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDatabase(context: Context) : SQLiteOpenHelper(context, "wf_labels.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE packages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                created_at TEXT NOT NULL,
                nalog TEXT NOT NULL,
                package_no TEXT NOT NULL,
                article TEXT NOT NULL,
                size TEXT NOT NULL,
                quantity INTEGER NOT NULL,
                customer TEXT NOT NULL,
                barcode TEXT NOT NULL,
                raw_text TEXT NOT NULL,
                photo_path TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private fun values(record: PackageRecord) = ContentValues().apply {
        put("created_at", record.createdAt)
        put("nalog", record.nalog)
        put("package_no", record.packageNo)
        put("article", record.article)
        put("size", record.size)
        put("quantity", record.quantity)
        put("customer", record.customer)
        put("barcode", record.barcode)
        put("raw_text", record.rawText)
        put("photo_path", record.photoPath)
    }

    fun insert(record: PackageRecord): Long = writableDatabase.insert("packages", null, values(record))

    fun update(record: PackageRecord) {
        writableDatabase.update("packages", values(record), "id = ?", arrayOf(record.id.toString()))
    }

    fun getAll(): List<PackageRecord> {
        val out = mutableListOf<PackageRecord>()
        readableDatabase.rawQuery("SELECT * FROM packages ORDER BY id ASC", null).use { c ->
            while (c.moveToNext()) {
                out += PackageRecord(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    createdAt = c.getString(c.getColumnIndexOrThrow("created_at")),
                    nalog = c.getString(c.getColumnIndexOrThrow("nalog")),
                    packageNo = c.getString(c.getColumnIndexOrThrow("package_no")),
                    article = c.getString(c.getColumnIndexOrThrow("article")),
                    size = c.getString(c.getColumnIndexOrThrow("size")),
                    quantity = c.getInt(c.getColumnIndexOrThrow("quantity")),
                    customer = c.getString(c.getColumnIndexOrThrow("customer")),
                    barcode = c.getString(c.getColumnIndexOrThrow("barcode")),
                    rawText = c.getString(c.getColumnIndexOrThrow("raw_text")),
                    photoPath = c.getString(c.getColumnIndexOrThrow("photo_path"))
                )
            }
        }
        return out
    }

    fun countAll(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM packages", null).use { c ->
        c.moveToFirst(); c.getInt(0)
    }

    fun countForOrder(nalog: String): Int = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM packages WHERE nalog = ?", arrayOf(nalog)
    ).use { c -> c.moveToFirst(); c.getInt(0) }

    fun delete(id: Long) {
        writableDatabase.delete("packages", "id = ?", arrayOf(id.toString()))
    }
}
