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

val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `accountKind` TEXT NOT NULL DEFAULT 'BANK'")
        db.execSQL("UPDATE `transactions` SET accountKind = 'CARD' WHERE channel = 'CARD' AND accountTail IS NOT NULL")
        db.execSQL("UPDATE `transactions` SET accountKind = 'INVEST' WHERE channel = 'INVEST'")
        db.execSQL("CREATE TABLE IF NOT EXISTS `credit_cards` (`key` TEXT NOT NULL, `name` TEXT NOT NULL, `bank` TEXT NOT NULL, `tail` TEXT NOT NULL, `limitPaise` INTEGER, `billingDay` INTEGER NOT NULL, `dueDaysAfter` INTEGER NOT NULL, `color` INTEGER NOT NULL, PRIMARY KEY(`key`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `icon` TEXT NOT NULL, `targetPaise` INTEGER NOT NULL, `savedPaise` INTEGER NOT NULL, `deadline` INTEGER, `notes` TEXT, `createdAt` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `receivables` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `from` TEXT NOT NULL, `amountPaise` INTEGER NOT NULL, `dueDate` INTEGER, `notes` TEXT, `received` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `informal_loans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `lender` TEXT NOT NULL, `amountPaise` INTEGER NOT NULL, `takenDate` INTEGER NOT NULL, `dueDate` INTEGER, `notes` TEXT, `repaid` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `controlled_categories` (`category` TEXT NOT NULL, `monthlyLimitPaise` INTEGER NOT NULL, `note` TEXT, PRIMARY KEY(`category`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `zero_tolerance` (`category` TEXT NOT NULL, `note` TEXT, `since` INTEGER NOT NULL, PRIMARY KEY(`category`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `discipline_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, `amountPaise` INTEGER NOT NULL, `reason` TEXT NOT NULL, `at` INTEGER NOT NULL)")
    }
}

val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `holdings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `account` TEXT NOT NULL, `units` REAL, `investedPaise` INTEGER NOT NULL, `currentPaise` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `notes` TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `loans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `lender` TEXT NOT NULL, `type` TEXT NOT NULL, `outstandingPaise` INTEGER NOT NULL, `asOf` INTEGER NOT NULL, `annualRatePct` REAL NOT NULL, `emiPaise` INTEGER NOT NULL, `dueDay` INTEGER NOT NULL, `notes` TEXT)")
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

    @Query("DELETE FROM transactions WHERE bank = :bank")
    suspend fun deleteByBank(bank: String)

    @Query("SELECT DISTINCT bank FROM transactions ORDER BY bank")
    fun banks(): Flow<List<String>>

    @Query("SELECT DISTINCT bank, accountTail, accountKind FROM transactions WHERE accountTail IS NOT NULL ORDER BY bank, accountTail")
    fun accountRefs(): Flow<List<AccountRef>>

    @Query("DELETE FROM transactions WHERE bank = :bank AND accountTail = :tail")
    suspend fun deleteByAccount(bank: String, tail: String)

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
    @Query("SELECT * FROM balance_anchors") suspend fun list(): List<BalanceAnchor>
}

@Dao
interface CreditCardDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(c: CreditCard)
    @Query("SELECT * FROM credit_cards WHERE `key` = :key") suspend fun get(key: String): CreditCard?
    @Query("DELETE FROM credit_cards WHERE `key` = :key") suspend fun delete(key: String)
    @Query("SELECT * FROM credit_cards ORDER BY bank, tail") fun all(): Flow<List<CreditCard>>
    @Query("SELECT * FROM credit_cards") suspend fun list(): List<CreditCard>
}

@Dao
interface GoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(g: Goal)
    @Query("DELETE FROM goals WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM goals ORDER BY deadline IS NULL, deadline") fun all(): Flow<List<Goal>>
    @Query("SELECT * FROM goals") suspend fun list(): List<Goal>
}

@Dao
interface ReceivableDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(r: Receivable)
    @Query("DELETE FROM receivables WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM receivables ORDER BY received, dueDate") fun all(): Flow<List<Receivable>>
    @Query("SELECT * FROM receivables") suspend fun list(): List<Receivable>
}

@Dao
interface InformalLoanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(l: InformalLoan)
    @Query("DELETE FROM informal_loans WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM informal_loans ORDER BY repaid, dueDate") fun all(): Flow<List<InformalLoan>>
    @Query("SELECT * FROM informal_loans") suspend fun list(): List<InformalLoan>
}

@Dao
interface DisciplineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertControlled(c: ControlledCategory)
    @Query("DELETE FROM controlled_categories WHERE category = :category") suspend fun deleteControlled(category: String)
    @Query("SELECT * FROM controlled_categories") fun controlled(): Flow<List<ControlledCategory>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertZero(z: ZeroTolerance)
    @Query("DELETE FROM zero_tolerance WHERE category = :category") suspend fun deleteZero(category: String)
    @Query("SELECT * FROM zero_tolerance") fun zero(): Flow<List<ZeroTolerance>>
    @Insert suspend fun insertEntry(e: DisciplineEntry)
    @Query("DELETE FROM discipline_entries WHERE id = :id") suspend fun deleteEntry(id: Long)
    @Query("SELECT * FROM discipline_entries ORDER BY at DESC") fun entries(): Flow<List<DisciplineEntry>>
    @Query("SELECT * FROM controlled_categories") suspend fun listControlled(): List<ControlledCategory>
    @Query("SELECT * FROM zero_tolerance") suspend fun listZero(): List<ZeroTolerance>
    @Query("SELECT * FROM discipline_entries") suspend fun listEntries(): List<DisciplineEntry>
}

@Dao
interface HoldingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(h: Holding): Long
    @Query("DELETE FROM holdings WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM holdings ORDER BY currentPaise DESC") fun all(): Flow<List<Holding>>
    @Query("SELECT * FROM holdings") suspend fun list(): List<Holding>
    @Query("SELECT COUNT(*) FROM holdings") suspend fun count(): Int
}

@Dao
interface LoanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(l: Loan): Long
    @Query("DELETE FROM loans WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM loans ORDER BY outstandingPaise DESC") fun all(): Flow<List<Loan>>
    @Query("SELECT * FROM loans") suspend fun list(): List<Loan>
    @Query("SELECT COUNT(*) FROM loans") suspend fun count(): Int
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

    @Query("SELECT * FROM accounts") suspend fun list(): List<Account>

    @Query("DELETE FROM accounts WHERE bank = :bank")
    suspend fun deleteByBank(bank: String)

    @Query("DELETE FROM accounts WHERE bank = :bank AND accountTail = :tail")
    suspend fun deleteByAccount(bank: String, tail: String)

    @Query("DELETE FROM accounts")
    suspend fun clear()
}

@Dao
interface MerchantRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(r: MerchantRule)

    @Query("SELECT * FROM merchant_rules WHERE merchantKey = :key")
    suspend fun get(key: String): MerchantRule?
    @Query("SELECT * FROM merchant_rules") suspend fun list(): List<MerchantRule>
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
    entities = [Transaction::class, Account::class, MerchantRule::class, ProcessedSms::class, ProcessedEmail::class, BalanceAnchor::class,
        CreditCard::class, Goal::class, Receivable::class, InformalLoan::class, ControlledCategory::class, ZeroTolerance::class, DisciplineEntry::class,
        Holding::class, Loan::class],
    version = 5,
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
    abstract fun cards(): CreditCardDao
    abstract fun goals(): GoalDao
    abstract fun receivables(): ReceivableDao
    abstract fun informalLoans(): InformalLoanDao
    abstract fun discipline(): DisciplineDao
    abstract fun holdings(): HoldingDao
    abstract fun loans(): LoanDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "finance_brain.db")
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigrationFrom(1)
                .build().also { instance = it }
        }
    }
}
