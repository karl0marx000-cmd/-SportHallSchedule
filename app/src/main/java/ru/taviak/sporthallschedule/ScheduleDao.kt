package ru.taviak.sporthallschedule

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM roles ORDER BY id") suspend fun roles(): List<Role>
    @Insert suspend fun addRole(x: Role): Long
    @Query("SELECT id FROM roles WHERE name=:name LIMIT 1") suspend fun roleIdByName(name: String): Long?
    @Query("DELETE FROM users WHERE roleId IN (SELECT id FROM roles WHERE name = 'Руководитель')") suspend fun deleteManagerAccounts()
    @Query("DELETE FROM roles WHERE name = 'Руководитель'") suspend fun deleteManagerRole()
    @Query("UPDATE users SET active=0 WHERE roleId IN (SELECT id FROM roles WHERE name = 'Тренер') AND trainerId IS NULL") suspend fun disableUnlinkedTrainerAccounts()
    @Insert suspend fun addUser(x: UserAccount): Long
    @Update suspend fun updateUser(x: UserAccount)
    @Query("SELECT * FROM users WHERE trainerId=:trainerId AND active=1 LIMIT 1") suspend fun accountForTrainer(trainerId: Long): UserAccount?
    @Query("SELECT COUNT(*) FROM users WHERE lower(login)=lower(:login) AND (:exceptId IS NULL OR id != :exceptId)") suspend fun loginIsTaken(login: String, exceptId: Long?): Int
    @Query("UPDATE users SET active=0 WHERE trainerId=:trainerId") suspend fun deactivateTrainerAccount(trainerId: Long)
    @Query("SELECT t.id AS id, t.fullName AS fullName, t.specialization AS specialization, t.active AS active, u.login AS login FROM trainers t LEFT JOIN users u ON u.trainerId=t.id AND u.active=1 ORDER BY t.fullName") fun trainersWithAccounts(): Flow<List<TrainerCatalogRow>>
    @Query("SELECT users.id AS id, users.login AS login, roles.name AS role, users.trainerId AS trainerId FROM users JOIN roles ON roles.id=users.roleId WHERE lower(users.login)=lower(:login) AND users.active=1 LIMIT 1") suspend fun userForLogin(login: String): SignedInUser?
    @Query("SELECT passwordHash FROM users WHERE lower(login)=lower(:login) AND active=1 LIMIT 1") suspend fun passwordHash(login: String): String?
    @Query("SELECT * FROM users WHERE lower(login)=lower(:login) LIMIT 1") suspend fun userRecordByLogin(login: String): UserAccount?

    @Query("SELECT * FROM trainers WHERE active=1 ORDER BY fullName") fun trainers(): Flow<List<Trainer>>
    @Query("SELECT * FROM trainers ORDER BY fullName") fun allTrainers(): Flow<List<Trainer>>
    @Query("SELECT * FROM trainers") suspend fun trainersOnce(): List<Trainer>
    @Insert suspend fun addTrainer(x: Trainer): Long
    @Update suspend fun updateTrainer(x: Trainer)

    @Query("SELECT * FROM training_types WHERE active=1 ORDER BY name") fun types(): Flow<List<TrainingType>>
    @Query("SELECT * FROM training_types ORDER BY name") fun allTypes(): Flow<List<TrainingType>>
    @Query("SELECT * FROM training_types") suspend fun typesOnce(): List<TrainingType>
    @Insert suspend fun addType(x: TrainingType): Long
    @Update suspend fun updateType(x: TrainingType)

    @Query("SELECT * FROM halls WHERE active=1 ORDER BY name") fun halls(): Flow<List<Hall>>
    @Query("SELECT * FROM halls ORDER BY name") fun allHalls(): Flow<List<Hall>>
    @Query("SELECT * FROM halls") suspend fun hallsOnce(): List<Hall>
    @Insert suspend fun addHall(x: Hall): Long
    @Update suspend fun updateHall(x: Hall)

    @Query("SELECT l.id AS id,l.trainerId AS trainerId,l.trainingTypeId AS trainingTypeId,l.hallId AS hallId,l.lessonDate AS lessonDate,l.startTime AS startTime,l.endTime AS endTime,l.status AS status,l.note AS note,t.fullName AS trainerName,tt.name AS typeName,h.name AS hallName FROM lessons l JOIN trainers t ON t.id=l.trainerId JOIN training_types tt ON tt.id=l.trainingTypeId JOIN halls h ON h.id=l.hallId WHERE (:dFrom='' OR l.lessonDate>=:dFrom) AND (:dTo='' OR l.lessonDate<=:dTo) AND (:q='' OR t.fullName LIKE '%'||:q||'%' OR tt.name LIKE '%'||:q||'%' OR h.name LIKE '%'||:q||'%') AND (:trainerId IS NULL OR l.trainerId=:trainerId) ORDER BY l.lessonDate,l.startTime")
    fun rows(dFrom: String, dTo: String, q: String, trainerId: Long?): Flow<List<LessonRow>>

    @Query("SELECT COUNT(*) FROM lessons WHERE id!=:excludeId AND status='Запланировано' AND lessonDate=:d AND trainerId=:t AND startTime<:e AND endTime>:s") suspend fun trainerConflict(d: String, t: Long, s: String, e: String, excludeId: Long): Int
    @Query("SELECT COUNT(*) FROM lessons WHERE id!=:excludeId AND status='Запланировано' AND lessonDate=:d AND hallId=:h AND startTime<:e AND endTime>:s") suspend fun hallConflict(d: String, h: Long, s: String, e: String, excludeId: Long): Int
    @Insert suspend fun addLesson(x: Lesson): Long
    @Query("SELECT COUNT(*) FROM lessons") suspend fun lessonCount(): Int
    @Update suspend fun updateLesson(x: Lesson)
    @Query("SELECT * FROM lessons WHERE id=:id LIMIT 1") suspend fun lessonById(id: Long): Lesson?
    @Query("UPDATE lessons SET status='Отменено' WHERE id=:id") suspend fun cancel(id: Long)
    @Insert suspend fun audit(x: LessonAudit)
}
