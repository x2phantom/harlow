package dev.harlow.core.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Signing out: empties every table, so nothing of the user stays in the database. */
@Singleton
class DatabaseWiper @Inject constructor(private val database: HarlowDatabase) {
    suspend fun clearAllTables() = withContext(Dispatchers.IO) { database.clearAllTables() }
}
