package com.financebrain

import android.app.Application
import android.content.Context
import com.financebrain.data.AppDatabase
import com.financebrain.data.TransactionRepository

class FinanceBrainApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val repository: TransactionRepository by lazy { TransactionRepository(database) }

    companion object {
        fun get(context: Context): FinanceBrainApp = context.applicationContext as FinanceBrainApp
    }
}
