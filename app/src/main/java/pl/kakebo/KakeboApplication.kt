package pl.kakebo

import android.app.Application
import pl.kakebo.data.local.KakeboDatabase
import pl.kakebo.data.repository.CategoryRepository
import pl.kakebo.data.repository.EntryRepository
import pl.kakebo.data.repository.MonthlyPlanRepository
import pl.kakebo.data.repository.MonthlyReflectionRepository
import pl.kakebo.data.receipts.ReceiptStore
import pl.kakebo.data.statements.BankStatementPdfReader
import pl.kakebo.ui.profile.ProfileDataManager

class KakeboApplication : Application() {
    val database by lazy { KakeboDatabase.open(this) }
    val receiptStore by lazy { ReceiptStore(this) }
    val categoryRepository by lazy { CategoryRepository(database.categoryDao()) }
    val entryRepository by lazy { EntryRepository(database.entryDao(), receiptStore) }
    val bankStatementPdfReader by lazy { BankStatementPdfReader(this) }
    val monthlyPlanRepository by lazy { MonthlyPlanRepository(database.monthlyPlanDao()) }
    val monthlyReflectionRepository by lazy { MonthlyReflectionRepository(database.monthlyReflectionDao()) }
    val profileDataManager by lazy { ProfileDataManager(this, database) }
}
