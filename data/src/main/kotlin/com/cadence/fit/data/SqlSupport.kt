package com.cadence.fit.data

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

internal fun Cursor.text(column: String): String = getString(getColumnIndexOrThrow(column)) ?: ""

internal fun Cursor.textOrNull(column: String): String? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getString(index)
}

internal fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))

internal fun Cursor.longOrNull(column: String): Long? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getLong(index)
}

internal fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))

internal fun Cursor.intOrNull(column: String): Int? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getInt(index)
}

internal fun Cursor.double(column: String): Double = getDouble(getColumnIndexOrThrow(column))

internal fun Cursor.doubleOrNull(column: String): Double? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getDouble(index)
}

internal fun Cursor.bool(column: String): Boolean = int(column) != 0

internal inline fun <T> SQLiteDatabase.inTransaction(block: SQLiteDatabase.() -> T): T {
    beginTransaction()
    return try {
        val result = block()
        setTransactionSuccessful()
        result
    } finally {
        endTransaction()
    }
}

internal inline fun <reified T : Enum<T>> enumValueOrNull(name: String?): T? {
    if (name == null) return null
    return enumValues<T>().firstOrNull { it.name.equals(name, ignoreCase = true) }
}
