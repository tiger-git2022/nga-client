package sp.phone.common

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import gov.anzong.androidnga.base.util.ContextUtils
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * 本地帖子阅读进度：记录每个帖子上次看到的页、位置、最远楼层，以及当时的总楼数，
 * 用于打开帖子时回到上次位置、在列表里显示阅读进度和之后新增的回复数。
 */
data class ReadProgress(
    val tid: Int,
    /** 上次停留的页码，从 1 开始 */
    val page: Int,
    /** 页内第一个可见条目的位置 */
    val index: Int,
    /** 该条目顶部相对列表顶部的偏移（像素） */
    val offset: Int,
    /** 读到过的最大楼层号（主楼为 0） */
    val readFloor: Int,
    /** 上次阅读时帖子的总楼数（含主楼） */
    val rows: Int,
    val subject: String?,
    val updateTime: Long,
) {
    /** 根据列表里的回复数算出上次阅读之后新增的楼数 */
    fun newReplies(replies: Int): Int = (replies + 1 - rows).coerceAtLeast(0)
}

object ReadProgressManager {

    private const val DB_NAME = "read_progress.db"
    private const val TABLE = "progress"
    private const val MAX_RECORDS = 5000

    private val cache = ConcurrentHashMap<Int, ReadProgress>()

    @Volatile
    private var loaded = false

    /** 每次进度变化加一，列表据此判断是否需要刷新 */
    @Volatile
    @JvmStatic
    var version = 0
        private set

    private val writer = Executors.newSingleThreadExecutor { r ->
        Thread(r, "read-progress").apply { isDaemon = true }
    }

    private val helper: DbHelper by lazy { DbHelper(ContextUtils.getContext()) }

    @JvmStatic
    fun get(tid: Int): ReadProgress? {
        if (tid == 0) return null
        ensureLoaded()
        return cache[tid]
    }

    /**
     * 保存进度。readFloor 和 rows 只增不减：往回翻看前面的楼层时不会把“读到第几楼”改小。
     */
    @JvmStatic
    fun save(tid: Int, page: Int, index: Int, offset: Int, lastVisibleFloor: Int, rows: Int, subject: String?) {
        if (tid == 0 || page <= 0) return
        ensureLoaded()
        val old = cache[tid]
        val progress = ReadProgress(
            tid = tid,
            page = page,
            index = index.coerceAtLeast(0),
            offset = offset,
            readFloor = maxOf(lastVisibleFloor, old?.readFloor ?: 0),
            rows = maxOf(rows, old?.rows ?: 0),
            subject = subject ?: old?.subject,
            updateTime = System.currentTimeMillis(),
        )
        if (old != null && old.copy(updateTime = 0) == progress.copy(updateTime = 0)) {
            return
        }
        cache[tid] = progress
        version++
        writer.execute { write(progress) }
    }

    @JvmStatic
    fun remove(tid: Int) {
        ensureLoaded()
        if (cache.remove(tid) != null) {
            version++
            writer.execute {
                runCatching { helper.writableDatabase.delete(TABLE, "tid=?", arrayOf(tid.toString())) }
            }
        }
    }

    @JvmStatic
    fun clear() {
        cache.clear()
        version++
        writer.execute { runCatching { helper.writableDatabase.delete(TABLE, null, null) } }
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            runCatching {
                helper.readableDatabase.query(
                    TABLE,
                    arrayOf("tid", "page", "idx", "off", "read_floor", "rows", "subject", "update_time"),
                    null, null, null, null, null
                ).use { c ->
                    while (c.moveToNext()) {
                        val p = ReadProgress(
                            tid = c.getInt(0),
                            page = c.getInt(1),
                            index = c.getInt(2),
                            offset = c.getInt(3),
                            readFloor = c.getInt(4),
                            rows = c.getInt(5),
                            subject = c.getString(6),
                            updateTime = c.getLong(7),
                        )
                        cache[p.tid] = p
                    }
                }
            }
            loaded = true
        }
    }

    private fun write(p: ReadProgress) {
        runCatching {
            val db = helper.writableDatabase
            val values = ContentValues().apply {
                put("tid", p.tid)
                put("page", p.page)
                put("idx", p.index)
                put("off", p.offset)
                put("read_floor", p.readFloor)
                put("rows", p.rows)
                put("subject", p.subject)
                put("update_time", p.updateTime)
            }
            db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            if (cache.size > MAX_RECORDS) {
                trim(db)
            }
        }
    }

    private fun trim(db: SQLiteDatabase) {
        val overflow = cache.size - MAX_RECORDS
        if (overflow <= 0) return
        val oldest = cache.values.sortedBy { it.updateTime }.take(overflow)
        db.beginTransaction()
        try {
            oldest.forEach {
                db.delete(TABLE, "tid=?", arrayOf(it.tid.toString()))
                cache.remove(it.tid)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private class DbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, 1) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE $TABLE (" +
                        "tid INTEGER PRIMARY KEY, " +
                        "page INTEGER NOT NULL, " +
                        "idx INTEGER NOT NULL, " +
                        "off INTEGER NOT NULL, " +
                        "read_floor INTEGER NOT NULL, " +
                        "rows INTEGER NOT NULL, " +
                        "subject TEXT, " +
                        "update_time INTEGER NOT NULL)"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        }
    }
}
