package ru.taviak.sporthallschedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.LocalTime
import android.util.Base64

private val Sage = Color(0xFF5D786B)
private val Ink = Color(0xFF202A35)
private val Fog = Color(0xFFF4F6F3)
private data class LessonFilter(val from: String, val to: String, val query: String, val user: SignedInUser?)

object Passwords {
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH = 256
    fun hash(password: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return "${ITERATIONS}\$" + Base64.encodeToString(salt, Base64.NO_WRAP) + "\$" + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
    fun verify(password: String, encoded: String): Boolean = try {
        val p = encoded.split('$')
        if (p.size != 3) false else {
            val iterations = p[0].toInt()
            val salt = Base64.decode(p[1], Base64.NO_WRAP)
            val expected = Base64.decode(p[2], Base64.NO_WRAP)
            val spec = PBEKeySpec(password.toCharArray(), salt, iterations, expected.size * 8)
            val actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            spec.clearPassword()
            actual.contentEquals(expected)
        }
    } catch (_: Exception) { false }
}

class ScheduleVm(private val dao: ScheduleDao) : ViewModel() {
    private val signedIn = MutableStateFlow<SignedInUser?>(null)
    val user = signedIn.asStateFlow()
    val query = MutableStateFlow("")
    val date = MutableStateFlow("")
    val dateTo = MutableStateFlow("")
    val trainers = dao.trainers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allTrainers = dao.allTrainers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val trainerAccounts = dao.trainersWithAccounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val types = dao.types().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allTypes = dao.allTypes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val halls = dao.halls().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allHalls = dao.allHalls().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val lessons = combine(date, dateTo, query, signedIn) { from, to, q, u -> LessonFilter(from, to, q, u) }
        .flatMapLatest { filter -> dao.rows(filter.from, filter.to, filter.query.trim(), filter.user?.let { if (it.role == "Тренер") it.trainerId else null }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            // Cleanup also handles databases created by older builds that had a manager role.
            dao.deleteManagerAccounts()
            dao.deleteManagerRole()
            val adminRole = ensureRole("Администратор")
            val trainerRole = ensureRole("Тренер")
            val userRole = ensureRole("Пользователь")
            // Disable legacy generic trainer accounts that are not linked to a specific trainer.
            dao.disableUnlinkedTrainerAccounts()

            // Initial catalog records are created only on an empty database.
            val knownTrainers = listOf(
                Trainer(fullName = "Анна Смирнова", specialization = "Йога"),
                Trainer(fullName = "Максим Орлов", specialization = "Силовая тренировка"),
                Trainer(fullName = "Елена Волкова", specialization = "Растяжка")
            )
            for (sample in knownTrainers) {
                if (dao.trainersOnce().none { it.fullName.equals(sample.fullName, ignoreCase = true) }) dao.addTrainer(sample)
            }
            if (dao.typesOnce().isEmpty()) {
                dao.addType(TrainingType(name = "Йога"))
                dao.addType(TrainingType(name = "Силовая тренировка"))
                dao.addType(TrainingType(name = "Функциональный тренинг", durationMinutes = 45))
                dao.addType(TrainingType(name = "Растяжка", durationMinutes = 45))
            }
            if (dao.hallsOnce().isEmpty()) {
                dao.addHall(Hall(name = "Основной зал", capacity = 20))
                dao.addHall(Hall(name = "Малый зал", capacity = 10))
            }

            // Default administrator and public viewer accounts.
            if (dao.userRecordByLogin("admin") == null) {
                dao.addUser(UserAccount(roleId = adminRole, login = "admin", passwordHash = Passwords.hash("admin123")))
            }
            if (dao.userRecordByLogin("guest") == null) {
                dao.addUser(UserAccount(roleId = userRole, login = "guest", passwordHash = Passwords.hash("guest123")))
            }

            // Every active trainer must have an independent account linked by trainerId.
            for (trainer in dao.trainersOnce().filter { it.active }) {
                val account = dao.accountForTrainer(trainer.id)
                if (account != null) {
                    // Normalize the original sample account from earlier project builds.
                    if (trainer.fullName == "Анна Смирнова" && account.login == "coach" && dao.loginIsTaken("anna", account.id) == 0) {
                        dao.updateUser(account.copy(login = "anna", passwordHash = Passwords.hash("anna123")))
                    }
                    continue
                }
                val preferred = when (trainer.fullName) {
                    "Анна Смирнова" -> "anna"
                    "Максим Орлов" -> "maxim"
                    "Елена Волкова" -> "elena"
                    else -> "trainer${trainer.id}"
                }
                var candidate = preferred
                var suffix = 1
                while (dao.loginIsTaken(candidate, null) > 0) {
                    candidate = "$preferred${suffix++}"
                }
                val password = if (candidate == preferred && preferred in setOf("anna", "maxim", "elena")) {
                    "${preferred}123"
                } else "trainer${trainer.id}123"
                dao.addUser(UserAccount(roleId = trainerRole, login = candidate, passwordHash = Passwords.hash(password), trainerId = trainer.id))
            }

            if (dao.lessonCount() == 0) {
                val trainerList = dao.trainersOnce().filter { it.active }
                val typeList = dao.typesOnce().filter { it.active }
                val hallList = dao.hallsOnce().filter { it.active }
                if (trainerList.isNotEmpty() && typeList.isNotEmpty() && hallList.isNotEmpty()) {
                    val today = LocalDate.now().toString()
                    dao.addLesson(Lesson(trainerId = trainerList[0].id, trainingTypeId = typeList[0].id, hallId = hallList[0].id, lessonDate = today, startTime = "09:00", endTime = "10:00", note = "Демонстрационная запись"))
                    if (trainerList.size > 1 && typeList.size > 1 && hallList.size > 1) {
                        dao.addLesson(Lesson(trainerId = trainerList[1].id, trainingTypeId = typeList[1].id, hallId = hallList[1].id, lessonDate = today, startTime = "10:00", endTime = "11:00", note = "Демонстрационная запись"))
                    }
                    if (trainerList.size > 2 && typeList.size > 2) {
                        dao.addLesson(Lesson(trainerId = trainerList[2].id, trainingTypeId = typeList[2].id, hallId = hallList[0].id, lessonDate = LocalDate.now().plusDays(1).toString(), startTime = "11:00", endTime = "12:00", note = "Демонстрационная запись"))
                    }
                }
            }
        }
    }

    private suspend fun ensureRole(name: String): Long = dao.roleIdByName(name) ?: dao.addRole(Role(name = name))

    suspend fun login(login: String, password: String): String? {
        if (login.isBlank() || password.isBlank()) return "Введите логин и пароль"
        val hash = dao.passwordHash(login.trim()) ?: return "Неверный логин или пароль"
        if (!Passwords.verify(password, hash)) return "Неверный логин или пароль"
        val account = dao.userForLogin(login.trim()) ?: return "Учетная запись отключена"
        if (account.role == "Тренер" && account.trainerId == null) {
            return "Учётная запись не привязана к тренеру. Обратитесь к администратору."
        }
        signedIn.value = account
        return null
    }
    fun logout() { signedIn.value = null; query.value = ""; date.value = ""; dateTo.value = "" }

    fun saveLesson(id: Long?, trainerId: Long, typeId: Long, hallId: Long, day: String, start: String, end: String, note: String, done: (String?) -> Unit) = viewModelScope.launch {
        if (signedIn.value?.role != "Администратор") { done("Недостаточно прав для изменения расписания"); return@launch }
        val error = when {
            day.isBlank() || start.isBlank() || end.isBlank() -> "Заполните дату и время"
            !validDate(day) -> "Введите существующую дату в формате ГГГГ-ММ-ДД"
            !validTime(start) || !validTime(end) -> "Время должно быть в формате ЧЧ:ММ"
            runCatching { LocalTime.parse(start) >= LocalTime.parse(end) }.getOrDefault(true) -> "Время окончания должно быть позже начала"
            dao.trainerConflict(day, trainerId, start, end, id ?: 0L) > 0 -> "У тренера уже есть занятие в это время"
            dao.hallConflict(day, hallId, start, end, id ?: 0L) > 0 -> "Зал уже занят в это время"
            else -> null
        }
        if (error != null) { done(error); return@launch }
        val lesson = Lesson(id = id ?: 0L, trainerId = trainerId, trainingTypeId = typeId, hallId = hallId, lessonDate = day, startTime = start, endTime = end, status = "Запланировано", note = note.trim())
        val lessonId = if (id == null) dao.addLesson(lesson) else { dao.updateLesson(lesson); id }
        dao.audit(LessonAudit(lessonId = lessonId, userId = signedIn.value?.id, action = if (id == null) "Создано занятие" else "Изменено занятие"))
        done(null)
    }
    fun cancelLesson(id: Long) = viewModelScope.launch {
        if (signedIn.value?.role != "Администратор") return@launch
        dao.cancel(id)
        dao.audit(LessonAudit(lessonId = id, userId = signedIn.value?.id, action = "Занятие отменено"))
    }

    fun saveCatalog(kind: String, id: Long?, name: String, detail: String, accountLogin: String = "", accountPassword: String = "", done: (String?) -> Unit) = viewModelScope.launch {
        if (signedIn.value?.role != "Администратор") { done("Недостаточно прав для изменения справочников"); return@launch }
        val n = name.trim()
        if (n.isBlank()) { done("Введите название"); return@launch }
        when (kind) {
            "Тренеры" -> {
                val login = accountLogin.trim()
                val existingTrainer = id?.let { selectedId -> dao.trainersOnce().firstOrNull { it.id == selectedId } }
                if (login.isBlank()) { done("Для каждого тренера требуется отдельный логин"); return@launch }
                val currentAccount = id?.let { dao.accountForTrainer(it) }
                if (currentAccount == null && accountPassword.length < 6) {
                    done("Задайте пароль тренера длиной не менее 6 символов"); return@launch
                }
                if (accountPassword.isNotBlank() && accountPassword.length < 6) {
                    done("Новый пароль должен содержать не менее 6 символов"); return@launch
                }
                if (dao.loginIsTaken(login, currentAccount?.id) > 0) {
                    done("Этот логин уже занят. Введите другой логин."); return@launch
                }
                val trainerRoleId = ensureRole("Тренер")
                if (id == null) {
                    val trainerId = dao.addTrainer(Trainer(fullName = n, specialization = detail.trim().ifBlank { "Общая подготовка" }))
                    dao.addUser(UserAccount(roleId = trainerRoleId, login = login, passwordHash = Passwords.hash(accountPassword), trainerId = trainerId))
                } else {
                    val old = existingTrainer ?: run { done("Тренер не найден"); return@launch }
                    dao.updateTrainer(old.copy(fullName = n, specialization = detail.trim().ifBlank { "Общая подготовка" }))
                    if (currentAccount != null) {
                        dao.updateUser(currentAccount.copy(login = login, passwordHash = if (accountPassword.isNotBlank()) Passwords.hash(accountPassword) else currentAccount.passwordHash, roleId = trainerRoleId))
                    } else {
                        dao.addUser(UserAccount(roleId = trainerRoleId, login = login, passwordHash = Passwords.hash(accountPassword), trainerId = old.id))
                    }
                }
            }
            "Виды тренировок" -> {
                val duration = detail.toIntOrNull() ?: 60
                if (duration !in 15..240) { done("Длительность должна быть от 15 до 240 минут"); return@launch }
                if (id == null) dao.addType(TrainingType(name = n, durationMinutes = duration))
                else { val old = dao.typesOnce().firstOrNull { it.id == id } ?: run { done("Вид тренировки не найден"); return@launch }; dao.updateType(old.copy(name = n, durationMinutes = duration)) }
            }
            "Залы" -> {
                val capacity = detail.toIntOrNull() ?: 15
                if (capacity !in 1..500) { done("Вместимость должна быть от 1 до 500"); return@launch }
                if (id == null) dao.addHall(Hall(name = n, capacity = capacity))
                else { val old = dao.hallsOnce().firstOrNull { it.id == id } ?: run { done("Зал не найден"); return@launch }; dao.updateHall(old.copy(name = n, capacity = capacity)) }
            }
        }
        done(null)
    }
    fun deactivateCatalog(kind: String, id: Long) = viewModelScope.launch {
        if (signedIn.value?.role != "Администратор") return@launch
        when (kind) {
            "Тренеры" -> {
                dao.trainersOnce().firstOrNull { it.id == id }?.let { dao.updateTrainer(it.copy(active = false)) }
                dao.deactivateTrainerAccount(id)
            }
            "Виды тренировок" -> dao.typesOnce().firstOrNull { it.id == id }?.let { dao.updateType(it.copy(active = false)) }
            "Залы" -> dao.hallsOnce().firstOrNull { it.id == id }?.let { dao.updateHall(it.copy(active = false)) }
        }
    }
    private fun validDate(s: String): Boolean = try { LocalDate.parse(s); true } catch (_: DateTimeParseException) { false }
    private fun validTime(s: String): Boolean = try { LocalTime.parse(s); s.matches(Regex("\\d{2}:\\d{2}")) } catch (_: Exception) { false }
}

class VmFactory(private val dao: ScheduleDao) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = ScheduleVm(dao) as T
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = ScheduleDatabase.get(this).dao()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Sage, background = Fog, surface = Color.White, onSurface = Ink, onBackground = Ink)) {
                val vm: ScheduleVm = viewModel(factory = VmFactory(dao))
                SportApp(vm)
            }
        }
    }
}
