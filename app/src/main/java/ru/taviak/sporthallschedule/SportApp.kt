package ru.taviak.sporthallschedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

private val Green = Color(0xFF5D786B)
private val Dark = Color(0xFF202A35)
private val Muted = Color(0xFF77827B)
private val Pale = Color(0xFFE6EEE8)
private val Page = Color(0xFFF4F6F3)
private enum class Screen(val title: String) { Schedule("Расписание"), Trainers("Тренеры"), Types("Тренировки"), Halls("Залы") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportApp(vm: ScheduleVm) {
    val user by vm.user.collectAsStateWithLifecycle()
    val lessons by vm.lessons.collectAsStateWithLifecycle()
    val trainers by vm.trainers.collectAsStateWithLifecycle()
    val allTrainers by vm.allTrainers.collectAsStateWithLifecycle()
    val trainerAccounts by vm.trainerAccounts.collectAsStateWithLifecycle()
    val types by vm.types.collectAsStateWithLifecycle()
    val allTypes by vm.allTypes.collectAsStateWithLifecycle()
    val halls by vm.halls.collectAsStateWithLifecycle()
    val allHalls by vm.allHalls.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val date by vm.date.collectAsStateWithLifecycle()
    val dateTo by vm.dateTo.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(Screen.Schedule) }
    var lessonToEdit by remember { mutableStateOf<LessonRow?>(null) }
    var showLessonDialog by remember { mutableStateOf(false) }
    var catalogDialog by remember { mutableStateOf<CatalogEdit?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showError by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isAdmin = user?.role == "Администратор"

    if (user == null) {
        LoginScreen(onLogin = { login, password -> scope.launch {
            val error = vm.login(login, password)
            if (error != null) { errorMessage = error; showError = true }
            else { errorMessage = null; showError = false }
        } }, error = if (showError) errorMessage else null)
        return
    }

    Scaffold(
        containerColor = Page,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Column { Text("STUDIO / SPORT", color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp); Text("Расписание зала", color = Dark, fontSize = 21.sp, fontWeight = FontWeight.Bold) } },
                actions = {
                    Text(user!!.role, color = Muted, fontSize = 11.sp)
                    IconButton(onClick = { vm.logout(); screen = Screen.Schedule }) { Icon(Icons.Default.Logout, "Выйти", tint = Green) }
                }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Page)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                val screens = if (isAdmin) Screen.values().toList() else listOf(Screen.Schedule)
                screens.forEach { item ->
                    NavigationBarItem(selected = screen == item, onClick = { screen = item },
                        icon = { Icon(when (item) { Screen.Schedule -> Icons.Default.CalendarMonth; Screen.Trainers -> Icons.Default.Groups; Screen.Types -> Icons.Default.FitnessCenter; Screen.Halls -> Icons.Default.MeetingRoom }, contentDescription = item.title) },
                        label = { Text(item.title, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, indicatorColor = Pale, selectedTextColor = Green))
                }
            }
        }, floatingActionButton = {
            if (isAdmin && screen == Screen.Schedule) FloatingActionButton(onClick = { lessonToEdit = null; showLessonDialog = true }, containerColor = Green, contentColor = Color.White) { Icon(Icons.Default.Add, "Добавить занятие") }
        }
    ) { padding ->
        when (screen) {
            Screen.Schedule -> ScheduleScreen(
                modifier = Modifier.padding(padding), lessons = lessons, query = query, date = date, dateTo = dateTo,
                onQuery = { vm.query.value = it }, onDate = { vm.date.value = it }, onDateTo = { vm.dateTo.value = it }, canEdit = isAdmin,
                onEdit = { lessonToEdit = it; showLessonDialog = true },
                onCancel = { row -> vm.cancelLesson(row.id); scope.launch { snackbar.showSnackbar("Занятие отменено") } }
            )
            Screen.Trainers -> CatalogScreen(Modifier.padding(padding), "Тренеры", trainerAccounts.filter { it.active }.map { CatalogItem(it.id, it.fullName, "${it.specialization} · логин: ${it.login ?: "не задан"}", it.login) },
                onAdd = { catalogDialog = CatalogEdit("Тренеры", null, "", "", "", "") },
                onEdit = { item -> catalogDialog = CatalogEdit("Тренеры", item.id, item.name, item.detail.substringBefore(" · логин:"), item.accountLogin ?: "", "") },
                onDeactivate = { item -> vm.deactivateCatalog("Тренеры", item.id) })
            Screen.Types -> CatalogScreen(Modifier.padding(padding), "Виды тренировок", allTypes.filter { it.active }.map { CatalogItem(it.id, it.name, "${it.durationMinutes} минут") },
                onAdd = { catalogDialog = CatalogEdit("Виды тренировок", null, "", "60", "", "") },
                onEdit = { item -> catalogDialog = CatalogEdit("Виды тренировок", item.id, item.name, item.detail.substringBefore(" "), "", "") },
                onDeactivate = { item -> vm.deactivateCatalog("Виды тренировок", item.id) })
            Screen.Halls -> CatalogScreen(Modifier.padding(padding), "Залы", allHalls.filter { it.active }.map { CatalogItem(it.id, it.name, "${it.capacity}") },
                onAdd = { catalogDialog = CatalogEdit("Залы", null, "", "15", "", "") },
                onEdit = { item -> catalogDialog = CatalogEdit("Залы", item.id, item.name, item.detail.substringAfter(": "), "", "") },
                onDeactivate = { item -> vm.deactivateCatalog("Залы", item.id) })
        }
    }

    if (showLessonDialog) {
        LessonDialog(trainers = trainers, types = types, halls = halls, initial = lessonToEdit,
            onDismiss = { showLessonDialog = false },
            onSave = { trainerId, typeId, hallId, day, start, end, note ->
                vm.saveLesson(lessonToEdit?.id, trainerId, typeId, hallId, day, start, end, note) { error ->
                    if (error == null) { showLessonDialog = false; scope.launch { snackbar.showSnackbar(if (lessonToEdit == null) "Занятие создано" else "Изменения сохранены") } }
                    else { errorMessage = error; showError = true }
                }
            })
    }
    catalogDialog?.let { item ->
        CatalogDialog(item, onDismiss = { catalogDialog = null }, onSave = { name, detail, accountLogin, accountPassword ->
            vm.saveCatalog(item.kind, item.id, name, detail, accountLogin, accountPassword) { error ->
                if (error == null) { catalogDialog = null; scope.launch { snackbar.showSnackbar("Справочник обновлён") } }
                else { errorMessage = error; showError = true }
            }
        })
    }
    if (showError && errorMessage != null) {
        AlertDialog(onDismissRequest = { showError = false }, title = { Text("Не удалось выполнить действие") }, text = { Text(errorMessage!!) }, confirmButton = { TextButton(onClick = { showError = false }) { Text("Понятно") } })
    }
}

@Composable
private fun LoginScreen(onLogin: (String, String) -> Unit, error: String?) {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Page).verticalScroll(rememberScrollState()).padding(horizontal = 26.dp), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(56.dp).background(Pale, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.FitnessCenter, null, tint = Green, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(24.dp))
        Text("STUDIO / SPORT", color = Green, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("Вход в систему", color = Dark, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp)); Text("Управление расписанием спортивного зала", color = Muted)
        Spacer(Modifier.height(26.dp))
        OutlinedTextField(login, { login = it }, Modifier.fillMaxWidth(), label = { Text("Логин") }, singleLine = true, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Пароль") }, singleLine = true, visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(), trailingIcon = { TextButton(onClick = { visible = !visible }) { Text(if (visible) "Скрыть" else "Показать") } }, shape = RoundedCornerShape(14.dp))
        if (error != null) { Spacer(Modifier.height(8.dp)); Text(error, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(20.dp))
        Button(onClick = { onLogin(login, password) }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Войти") }
        Spacer(Modifier.height(20.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Демо-доступ", fontWeight = FontWeight.SemiBold, color = Dark)
                Spacer(Modifier.height(6.dp)); Text("Администратор: admin / admin123", color = Muted, fontSize = 12.sp)
                Text("Анна Смирнова: anna / anna123", color = Muted, fontSize = 12.sp)
                Text("Максим Орлов: maxim / maxim123", color = Muted, fontSize = 12.sp)
                Text("Елена Волкова: elena / elena123", color = Muted, fontSize = 12.sp)
                Text("Пользователь: guest / guest123", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ScheduleScreen(modifier: Modifier, lessons: List<LessonRow>, query: String, date: String, dateTo: String, onQuery: (String) -> Unit, onDate: (String) -> Unit, onDateTo: (String) -> Unit, canEdit: Boolean, onEdit: (LessonRow) -> Unit, onCancel: (LessonRow) -> Unit) {
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = date, onValueChange = onDate, modifier = Modifier.weight(1f), label = { Text("С даты") }, placeholder = { Text("ГГГГ-ММ-ДД") }, singleLine = true, shape = RoundedCornerShape(14.dp))
            OutlinedTextField(value = dateTo, onValueChange = onDateTo, modifier = Modifier.weight(1f), label = { Text("По дату") }, placeholder = { Text("ГГГГ-ММ-ДД") }, singleLine = true, shape = RoundedCornerShape(14.dp))
        }
        if (date.isNotBlank() || dateTo.isNotBlank()) { TextButton(onClick = { onDate(""); onDateTo("") }) { Text("Сбросить период", color = Green) } }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(), label = { Text("Поиск по тренеру, тренировке или залу") }, leadingIcon = { Icon(Icons.Default.Search, null, tint = Green) }, singleLine = true, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text("ПЛАН ЗАНЯТИЙ", color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp); Text("${lessons.count { it.status == "Запланировано" }} активных", color = Dark, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
            Text("${lessons.size} записей", color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        if (lessons.isEmpty()) {
            Card(Modifier.fillMaxWidth().padding(top = 12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.CalendarMonth, null, tint = Green, modifier = Modifier.size(32.dp)); Spacer(Modifier.height(8.dp)); Text("Занятий не найдено", fontWeight = FontWeight.SemiBold); Text("Измените фильтр или добавьте новое занятие", color = Muted, fontSize = 12.sp) } }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(lessons, key = { it.id }) { row ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(15.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(row.lessonDate, color = Green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Surface(color = if (row.status == "Запланировано") Pale else Color(0xFFF1E9E6), shape = RoundedCornerShape(20.dp)) { Text(row.status, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 10.sp, color = if (row.status == "Запланировано") Green else Color(0xFF9A6153)) }
                            }
                            Spacer(Modifier.height(5.dp)); Text("${row.startTime} – ${row.endTime}", color = Dark, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(row.typeName, color = Dark, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp)); Text("${row.trainerName}  ·  ${row.hallName}", color = Muted, fontSize = 12.sp)
                            if (row.note.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(row.note, color = Muted, fontSize = 12.sp) }
                            if (canEdit && row.status == "Запланировано") {
                                Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { onEdit(row) }, shape = RoundedCornerShape(10.dp)) { Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Изменить") }
                                    OutlinedButton(onClick = { onCancel(row) }, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF9A6153))) { Icon(Icons.Default.EventBusy, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Отменить") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class CatalogItem(val id: Long, val name: String, val detail: String, val accountLogin: String? = null)
private data class CatalogEdit(val kind: String, val id: Long?, val name: String, val detail: String, val accountLogin: String = "", val accountPassword: String = "")

@Composable
private fun CatalogScreen(modifier: Modifier, title: String, items: List<CatalogItem>, onAdd: () -> Unit, onEdit: (CatalogItem) -> Unit, onDeactivate: (CatalogItem) -> Unit) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text("СПРАВОЧНИК", color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp); Text(title, color = Dark, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            Button(onClick = onAdd, shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Добавить") }
        }
        Spacer(Modifier.height(14.dp))
        if (items.isEmpty()) Text("Список пока пуст", color = Muted)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(items, key = { it.id }) { item ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(item.name, fontWeight = FontWeight.SemiBold, color = Dark); Text(item.detail, color = Muted, fontSize = 12.sp) }
                        IconButton(onClick = { onEdit(item) }) { Icon(Icons.Default.Edit, "Редактировать", tint = Green) }
                        IconButton(onClick = { onDeactivate(item) }) { Icon(Icons.Default.DeleteOutline, "Деактивировать", tint = Color(0xFF9A6153)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LessonDialog(trainers: List<Trainer>, types: List<TrainingType>, halls: List<Hall>, initial: LessonRow?, onDismiss: () -> Unit, onSave: (Long, Long, Long, String, String, String, String) -> Unit) {
    var trainerId by remember(initial, trainers) { mutableStateOf(initial?.trainerId ?: trainers.firstOrNull()?.id ?: 0L) }
    var typeId by remember(initial, types) { mutableStateOf(initial?.trainingTypeId ?: types.firstOrNull()?.id ?: 0L) }
    var hallId by remember(initial, halls) { mutableStateOf(initial?.hallId ?: halls.firstOrNull()?.id ?: 0L) }
    var day by remember(initial) { mutableStateOf(initial?.lessonDate ?: java.time.LocalDate.now().toString()) }
    var start by remember(initial) { mutableStateOf(initial?.startTime ?: "10:00") }
    var end by remember(initial) { mutableStateOf(initial?.endTime ?: "11:00") }
    var note by remember(initial) { mutableStateOf(initial?.note ?: "") }
    var trainerExpanded by remember { mutableStateOf(false) }; var typeExpanded by remember { mutableStateOf(false) }; var hallExpanded by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "Новое занятие" else "Изменить занятие", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = trainerExpanded, onExpandedChange = { trainerExpanded = !trainerExpanded }) { OutlinedTextField(value = trainers.firstOrNull { it.id == trainerId }?.fullName ?: "Нет тренеров", onValueChange = {}, readOnly = true, label = { Text("Тренер") }, modifier = Modifier.menuAnchor().fillMaxWidth()); ExposedDropdownMenu(expanded = trainerExpanded, onDismissRequest = { trainerExpanded = false }) { trainers.forEach { DropdownMenuItem(text = { Text(it.fullName) }, onClick = { trainerId = it.id; trainerExpanded = false }) } } }
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = !typeExpanded }) { OutlinedTextField(value = types.firstOrNull { it.id == typeId }?.name ?: "Нет видов тренировок", onValueChange = {}, readOnly = true, label = { Text("Вид тренировки") }, modifier = Modifier.menuAnchor().fillMaxWidth()); ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) { types.forEach { DropdownMenuItem(text = { Text(it.name) }, onClick = { typeId = it.id; typeExpanded = false }) } } }
                ExposedDropdownMenuBox(expanded = hallExpanded, onExpandedChange = { hallExpanded = !hallExpanded }) { OutlinedTextField(value = halls.firstOrNull { it.id == hallId }?.name ?: "Нет залов", onValueChange = {}, readOnly = true, label = { Text("Зал") }, modifier = Modifier.menuAnchor().fillMaxWidth()); ExposedDropdownMenu(expanded = hallExpanded, onDismissRequest = { hallExpanded = false }) { halls.forEach { DropdownMenuItem(text = { Text(it.name) }, onClick = { hallId = it.id; hallExpanded = false }) } } }
                OutlinedTextField(day, { day = it }, Modifier.fillMaxWidth(), label = { Text("Дата (ГГГГ-ММ-ДД)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(start, { start = it }, Modifier.weight(1f), label = { Text("Начало") }, singleLine = true); OutlinedTextField(end, { end = it }, Modifier.weight(1f), label = { Text("Окончание") }, singleLine = true) }
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Комментарий (необязательно)") }, minLines = 2)
                if (trainers.isEmpty() || types.isEmpty() || halls.isEmpty()) Text("Сначала добавьте тренера, вид тренировки и зал в справочниках.", color = MaterialTheme.colorScheme.error)
            }
        }, confirmButton = { Button(enabled = trainers.isNotEmpty() && types.isNotEmpty() && halls.isNotEmpty(), onClick = { onSave(trainerId, typeId, hallId, day.trim(), start.trim(), end.trim(), note) }) { Text("Сохранить") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

@Composable
private fun CatalogDialog(item: CatalogEdit, onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var name by remember(item) { mutableStateOf(item.name) }
    var detail by remember(item) { mutableStateOf(item.detail) }
    var accountLogin by remember(item) { mutableStateOf(item.accountLogin) }
    var accountPassword by remember(item) { mutableStateOf("") }
    val detailLabel = when (item.kind) { "Тренеры" -> "Специализация"; "Виды тренировок" -> "Длительность в минутах"; else -> "Вместимость" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item.id == null) "Добавить: ${item.kind}" else "Изменить: ${item.kind}") },
        text = {
            Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(if (item.kind == "Тренеры") "ФИО тренера" else "Название") }, singleLine = true)
                OutlinedTextField(detail, { detail = it }, Modifier.fillMaxWidth(), label = { Text(detailLabel) }, singleLine = true)
                if (item.kind == "Тренеры") {
                    Text("Личная учётная запись", fontWeight = FontWeight.SemiBold, color = Dark)
                    OutlinedTextField(accountLogin, { accountLogin = it.trim() }, Modifier.fillMaxWidth(), label = { Text("Логин тренера") }, singleLine = true)
                    OutlinedTextField(
                        accountPassword, { accountPassword = it }, Modifier.fillMaxWidth(),
                        label = { Text(if (item.id == null) "Пароль (от 6 символов)" else "Новый пароль (необязательно)") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                    if (item.id != null) Text("Если пароль не менять, оставьте поле пустым.", color = Muted, fontSize = 12.sp)
                    else Text("Для каждого тренера создаётся отдельный вход, который открывает только его расписание.", color = Muted, fontSize = 12.sp)
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(name, detail, accountLogin, accountPassword) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
