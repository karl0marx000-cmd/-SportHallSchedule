package ru.taviak.sporthallschedule

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName = "roles")
data class Role(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

@Entity(tableName = "users", indices = [Index(value = ["login"], unique = true), Index(value = ["trainerId"], unique = true)])
data class UserAccount(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roleId: Long,
    val login: String,
    val passwordHash: String,
    val trainerId: Long? = null,
    val active: Boolean = true
)

@Entity(tableName = "trainers")
data class Trainer(@PrimaryKey(autoGenerate = true) val id: Long = 0, val fullName: String, val specialization: String, val active: Boolean = true)

@Entity(tableName = "training_types")
data class TrainingType(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val durationMinutes: Int = 60, val active: Boolean = true)

@Entity(tableName = "halls")
data class Hall(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val capacity: Int = 15, val active: Boolean = true)

@Entity(tableName = "lessons")
data class Lesson(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trainerId: Long,
    val trainingTypeId: Long,
    val hallId: Long,
    val lessonDate: String,
    val startTime: String,
    val endTime: String,
    val status: String = "Запланировано",
    val note: String = ""
)

@Entity(tableName = "lesson_audit")
data class LessonAudit(@PrimaryKey(autoGenerate = true) val id: Long = 0, val lessonId: Long, val userId: Long?, val action: String, val changedAt: Long = System.currentTimeMillis())

data class LessonRow(
    val id: Long, val trainerId: Long, val trainingTypeId: Long, val hallId: Long,
    val lessonDate: String, val startTime: String, val endTime: String, val status: String, val note: String,
    val trainerName: String, val typeName: String, val hallName: String
)
data class SignedInUser(val id: Long, val login: String, val role: String, val trainerId: Long?)


data class TrainerCatalogRow(val id: Long, val fullName: String, val specialization: String, val active: Boolean, val login: String?)
