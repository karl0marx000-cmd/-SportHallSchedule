package ru.taviak.sporthallschedule

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Role::class, UserAccount::class, Trainer::class, TrainingType::class, Hall::class, Lesson::class, LessonAudit::class], version = 3, exportSchema = false)
abstract class ScheduleDatabase : RoomDatabase() {
    abstract fun dao(): ScheduleDao
    companion object {
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // The product intentionally has no manager role. Remove old demonstration accounts and role.
                db.execSQL("DELETE FROM users WHERE roleId IN (SELECT id FROM roles WHERE name = 'Руководитель')")
                db.execSQL("DELETE FROM roles WHERE name = 'Руководитель'")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_users_login ON users(login)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_users_trainerId ON users(trainerId)")
            }
        }
        @Volatile private var instance: ScheduleDatabase? = null
        fun get(context: Context): ScheduleDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, ScheduleDatabase::class.java, "sport_hall.db")
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}
