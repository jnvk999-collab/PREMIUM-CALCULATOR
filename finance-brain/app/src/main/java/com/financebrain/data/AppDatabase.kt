package com.financebrain.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `balance_anchors` (`key` TEXT NOT NULL, `amountPaise` INTEGER NOT NULL, `at` INTEGER NOT NULL, PRIMARY KEY(`key`))")
    }
}

class Converters {
    @TypeConverter fun dirToString(d: Direction) = d.name
    @TypeConverter fun stringToDir(s: String) = Direction.valueOf(s)
    @TypeConverter fun srcToString(s: Source) = s.name
    @TypeConverter fun stringToSrc(s: String) = Source.valueOf(s)
}

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(t: Transaction): Long

    @Update
    suspend fun update(t: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM transactions WHERE counterparty = :counterparty")
    suspend fun deleteByCounterparty(counterparty: String)

    @Query("DELETE FROM transactions WHERE source = :source")
    suspend fun deleteBySource(source: Source)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun all(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    fun between(from: Long, to: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: Long): Transaction?

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun allNow(): List<Transaction>

    @Update
    suspend fun updateAll(rows: List<Transaction>)

    @Query("SELECT COUNT(*) FROM transactions")
    fun count(): Flow<Int>

    @Query("UPDATE transactions SET category = :category WHERE counterparty = :counterparty AND userEdited = 0")
    suspend fun recategorizeMerchant(counterparty: String, category: String)

    /** Same amount and direction within a time window: the cross-source duplicate check. */
    @Query("SELECT * FROM transactions WHERE amountPaise = :amount AND direction = :direction AND timestamp BETWEEN :from AND :to LIMIT 5")
    suspend fun similar(amount: Long, direction: Direction, from: Long, to: Long): List<Transaction>
}

@Dao
interface BalanceAnchorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(a: BalanceAnchor)

    @Query("DELETE FROM balance_anchors WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("SELECT * FROM balance_anchors")
    fun all(): Flow<List<BalanceAnchor>>
}

@Dao
interface ProcessedEmailDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: ProcessedEmail)

    @Query("SELECT COUNT(*) FROM processed_email WHERE messageId = :id")
    suspend fun exists(id: String): Int
}

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(a: Account)

    @Query("SELECT * FROM accounts WHERE bank = :bank AND accountTail = :tail")
    suspend fun get(bank: String, tail: String): Account?

    @Query("SELECT * FROM accounts WHERE balancePaise IS NOT NULL ORDER BY bank, accountTail")
    fun all(): Flow<List<Account>>

    @Query("DELETE FROM accounts WHERE balancePaise IS NULL")
    suspend fun deleteWithoutBalance()

    @Query("DELETE FROM accounts")
    suspend fun clear()
}

@Dao
interface MerchantRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(r: MerchantRule)

    @Query("SELECT * FROM merchant_rules WHERE merchantKey = :key")
    suspend fun get(key: String): MerchantRule?
}

@Dao
interface ProcessedSmsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<ProcessedSms>)

    @Query("SELECT smsId FROM processed_sms")
    suspend fun ids(): List<Long>

    @Query("DELETE FROM processed_sms")
    suspend fun clear()
}

@Database(
    entities = [Transaction::class, Account::class, MerchantRule::class, ProcessedSms::class, ProcessedEmail::class, BalanceAnchor::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactions(): TransactionDao
    abstract fun accounts(): AccountDao
    abstract fun merchantRules(): MerchantRuleDao
    abstract fun processedSms(): ProcessedSmsDao
    abstract fun processedEmail(): ProcessedEmailDao
    abstract fun balanceAnchors(): BalanceAnchorDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "finance_brain.db")
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigrationFrom(1)
                .build().also { instance = it }
        }
    }
}
