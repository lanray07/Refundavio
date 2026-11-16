package app.refundavio

import android.content.Context

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var reminderDestination by mutableStateOf<ReminderKind?>(null)

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(localizedAppContext(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acceptReminder(intent)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) Dark else Light) {
                Refundavio(reminderDestination, { reminderDestination = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        acceptReminder(intent)
    }

    override fun onResume() {
        super.onResume()
        ReminderScheduler.request(this)
    }

    private fun acceptReminder(intent: Intent) {
        val kind = intent.getStringExtra(REMINDER_DESTINATION) ?: return
        reminderDestination = ReminderKind.entries.firstOrNull { it.name == kind }
        intent.removeExtra(REMINDER_DESTINATION)
    }
}

private val Light = lightColorScheme(primary = Color(0xFF205B45), onPrimary = Color.White,
    background = Color(0xFFF6F4ED), onBackground = Color(0xFF172B23), surface = Color(0xFFFFFDF8),
    onSurface = Color(0xFF172B23), onSurfaceVariant = Color(0xFF506158),
    surfaceVariant = Color(0xFFE8EEE5), primaryContainer = Color(0xFFD8EFDE),
    onPrimaryContainer = Color(0xFF184232), outline = Color(0xFF68796E), outlineVariant = Color(0xFFDDE4DA))
private val Dark = darkColorScheme(primary = Color(0xFFA1DEB9), onPrimary = Color(0xFF123828),
    background = Color(0xFF101B16), onBackground = Color(0xFFE1F0E5), surface = Color(0xFF182821),
    onSurface = Color(0xFFE1F0E5), onSurfaceVariant = Color(0xFFB5C8BC),
    surfaceVariant = Color(0xFF273B30), primaryContainer = Color(0xFF274C38),
    onPrimaryContainer = Color(0xFFD6F4DE), outline = Color(0xFF96AA9D), outlineVariant = Color(0xFF354C3E))

private enum class Tab(val label: Int, val icon: ImageVector) {
    DASHBOARD(R.string.dashboard, Icons.Default.Home),
    PURCHASES(R.string.purchases, Icons.AutoMirrored.Filled.List),
    RETURNS(R.string.returns, Icons.Default.Refresh),
    WARRANTIES(R.string.warranties, Icons.Default.Star),
    MORE(R.string.more, Icons.Default.MoreVert)
}

@Composable
internal fun appLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

internal fun dateLabel(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

internal fun defaultCurrency(locale: Locale): String =
    runCatching { java.util.Currency.getInstance(locale).currencyCode }.getOrNull()?.takeIf(Money::valid) ?: "GBP"

@Composable
private fun Refundavio(reminderDestination: ReminderKind?, consumeReminder: () -> Unit, model: LedgerModel = viewModel()) {
    val state by model.cases.collectAsStateWithLifecycle()
    val data = (state as? LedgerState.Ready)?.cases
    val error by model.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locale = appLocale()
    val preferences = remember { context.getSharedPreferences("preferences", 0) }
    var spoken by remember { mutableStateOf(preferences.getBoolean("spoken", false)) }
    var tab by rememberSaveable { mutableStateOf(Tab.DASHBOARD) }
    var recordId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editorId by rememberSaveable { mutableStateOf<Long?>(null) }
    var filter by rememberSaveable { mutableStateOf(0) }
    var demo by rememberSaveable { mutableStateOf(false) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) { while (true) { today = LocalDate.now(); delay(15000) } }
    val samples = remember(today, locale) { demoCases(context, today) }
    val cases = if (demo) samples else data.orEmpty()
    val snapshot = remember(cases, today) { Dashboard.from(cases, today) }
    val detail = cases.firstOrNull { it.record.id == recordId }
    val snackbar = remember { SnackbarHostState() }
    val saveError = stringResource(R.string.save_error)
    LaunchedEffect(error) { if (error) { snackbar.showSnackbar(saveError); model.clearError() } }

    var voiceOpen by rememberSaveable { mutableStateOf(false) }
    var transcript by rememberSaveable { mutableStateOf("") }
    var voiceError by rememberSaveable { mutableStateOf(false) }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        transcript = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
        voiceError = result.resultCode == android.app.Activity.RESULT_OK && transcript.isBlank()
    }
    var utterance by remember { mutableStateOf("") }
    var engine by remember { mutableStateOf<TextToSpeech?>(null) }
    var engineReady by remember { mutableStateOf(false) }
    val ttsError = stringResource(R.string.tts_error)
    DisposableEffect(spoken) {
        val instance = if (spoken) TextToSpeech(context.applicationContext) { engineReady = it == TextToSpeech.SUCCESS } else null
        engine = instance
        onDispose { instance?.shutdown(); engineReady = false }
    }
    LaunchedEffect(utterance, engineReady, engine, locale) {
        if (utterance.isNotEmpty() && engine != null) {
            val active = engine ?: return@LaunchedEffect
            if (engineReady && active.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE) {
                if (active.speak(utterance, TextToSpeech.QUEUE_FLUSH, null, "dashboard-summary") == TextToSpeech.ERROR) snackbar.showSnackbar(ttsError)
            } else snackbar.showSnackbar(ttsError)
            utterance = ""
        }
    }

    fun openTab(destination: Tab, start: Int = 0) { tab = destination; filter = start }
    fun onVoice() { transcript = ""; voiceError = false; voiceOpen = true }
    LaunchedEffect(reminderDestination) {
        if (reminderDestination != null) {
            demo = false
            recordId = null
            editorId = null
            voiceOpen = false
            openTab(Tab.RETURNS, if (reminderDestination == ReminderKind.DEADLINE) 1 else 2)
            consumeReminder()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (wide) NavigationRail(containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxHeight().windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start)),
                header = { BrandMark(Modifier.padding(vertical = 18.dp)) }) {
                Tab.entries.forEach { destination ->
                    NavigationRailItem(selected = tab == destination, onClick = { openTab(destination) },
                        icon = { Icon(destination.icon, null) }, label = { Text(stringResource(destination.label)) },
                        alwaysShowLabel = true)
                }
            }
            Scaffold(
                modifier = Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (!wide) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        Tab.entries.forEach { destination ->
                            NavigationBarItem(selected = tab == destination, onClick = { openTab(destination) },
                                icon = { Icon(destination.icon, null) }, label = { Text(stringResource(destination.label), maxLines = 1, fontSize = 10.sp) })
                        }
                    }
                }
            ) { insets ->
                Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets)) {
                    AppHeader(today, demo, !demo && data != null, onAdd = { editorId = 0L }, onVoice = ::onVoice)
                    if (data == null && !demo) {
                        Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            if (state == LedgerState.Unavailable) {
                                Text(stringResource(R.string.read_error))
                                TextButton(onClick = model::reload) { Text(stringResource(R.string.retry)) }
                            } else {
                                CircularProgressIndicator(Modifier.size(25.dp))
                                Spacer(Modifier.height(10.dp))
                                Text(stringResource(R.string.loading_ledger))
                            }
                        }
                    } else key(tab, filter) {
                        when (tab) {
                            Tab.DASHBOARD -> RecoveryScreen(snapshot, today, detail = { recordId = it }, add = { editorId = 0L },
                                viewReturns = { openTab(Tab.RETURNS, it) }, viewWarranties = { openTab(Tab.WARRANTIES) })
                            Tab.PURCHASES -> RecordsPage(R.string.purchases, R.string.purchases_hint,
                                cases.filter { it.record.archived == (filter == 1) }, today, { recordId = it }, { editorId = 0L },
                                filter, { filter = it }, filters = listOf(R.string.active_purchases, R.string.archived_purchases))
                            Tab.RETURNS -> RecordsPage(R.string.returns, R.string.returns_hint, when (filter) {
                                1 -> snapshot.returns
                                2 -> snapshot.followUps
                                3 -> snapshot.pending
                                4 -> snapshot.dispatch
                                5 -> snapshot.waitingForResponse
                                6 -> snapshot.recent
                                else -> cases.filter { !it.record.archived && it.stage != Stage.NOT_REQUESTED }
                            }, today, { recordId = it }, { editorId = 0L }, filter, { filter = it })
                            Tab.WARRANTIES -> RecordsPage(R.string.warranties, R.string.warranty_hint,
                                cases.filter { !it.record.archived && it.record.warranty != null }.sortedBy { it.record.warranty }, today,
                                { recordId = it }, { editorId = 0L }, warranty = true)
                            Tab.MORE -> SettingsPage(spoken, changeSpoken = {
                                spoken = it; preferences.edit().putBoolean("spoken", it).apply()
                            }, demo, { demo = !demo; openTab(Tab.DASHBOARD) }, { model.erase() }, ::onVoice,
                                data.orEmpty(), today)
                        }
                    }
                }
            }
        }
    }

    val edit = editorId
    if (edit != null) {
        val existing = cases.firstOrNull { it.record.id == edit }
        if (edit == 0L || existing != null) key(edit) {
            CaseEditor(existing, { editorId = null }) { changed ->
                model.save(changed) { id -> editorId = null; recordId = id }
            }
        }
    } else if (detail != null) key(detail.record.id) {
        CaseDetails(detail, today, demo, model, close = { recordId = null }, edit = { editorId = detail.record.id })
    }

    if (voiceOpen) {
        val commands = listOf(R.string.voice_balance, R.string.voice_deadlines, R.string.voice_followups).map { stringResource(it) }
        val summary = if (snapshot.outstanding.isEmpty()) stringResource(R.string.no_outstanding)
        else stringResource(R.string.voice_summary, snapshot.outstanding.entries.joinToString { Money.format(it.value, it.key, locale) })
        var voiceResult by rememberSaveable { mutableStateOf("") }
        val unknown = stringResource(R.string.voice_unknown)
        val launchSpeech = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            try { speech.launch(intent); voiceError = false } catch (_: android.content.ActivityNotFoundException) { voiceError = true }
            catch (_: SecurityException) { voiceError = true }
        }
        AlertDialog(onDismissRequest = { voiceOpen = false }, title = { Text(stringResource(R.string.voice_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.voice_privacy))
                    OutlinedButton(onClick = launchSpeech) { Text(stringResource(R.string.listen)) }
                    if (voiceError) Text(stringResource(R.string.voice_unavailable), color = MaterialTheme.colorScheme.error)
                    OutlinedTextField(transcript, { transcript = it }, label = { Text(stringResource(R.string.command)) }, modifier = Modifier.fillMaxWidth())
                    commands.forEach { sample -> TextButton(onClick = { transcript = sample }) { Text(sample) } }
                    if (voiceResult.isNotEmpty()) Text(voiceResult)
                }
            },
            confirmButton = {
                Button(onClick = {
                    fun clean(s: String) = s.trim().lowercase(locale).trimEnd('.', '?', '!', '؟')
                    when (commands.map(::clean).indexOf(clean(transcript))) {
                        0 -> { voiceResult = summary; if (spoken) utterance = summary }
                        1 -> { openTab(Tab.RETURNS, 1); voiceOpen = false }
                        2 -> { openTab(Tab.RETURNS, 2); voiceOpen = false }
                        else -> voiceResult = unknown
                    }
                }, enabled = transcript.isNotBlank()) { Text(stringResource(R.string.run_command)) }
            }, dismissButton = { TextButton(onClick = { voiceOpen = false }) { Text(stringResource(R.string.close)) } })
    }
}

@Composable
private fun BrandMark(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.refundavio_icon), null,
        modifier.size(38.dp).background(Color(0xFF15382E), RoundedCornerShape(11.dp))
            .padding(4.dp))
}

@Composable
private fun AppHeader(today: LocalDate, demo: Boolean, addEnabled: Boolean, onAdd: () -> Unit, onVoice: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BrandMark()
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(if (demo) stringResource(R.string.demo_label) else dateLabel(today, appLocale()),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onVoice) { Icon(painterResource(R.drawable.ic_mic), stringResource(R.string.voice_title), tint = MaterialTheme.colorScheme.primary) }
        FilledIconButton(onClick = onAdd, enabled = addEnabled) { Icon(Icons.Default.Add, stringResource(R.string.add_purchase)) }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun PageTitle(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun RecoveryScreen(s: Dashboard, today: LocalDate, detail: (Long) -> Unit, add: () -> Unit,
                           viewReturns: (Int) -> Unit, viewWarranties: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = maxWidth >= 670.dp
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = if (columns) 28.dp else 16.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.recovery_eyebrow), color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 2.sp)
                PageTitle(stringResource(R.string.recovery_title), stringResource(R.string.recovery_subtitle))
            }
            BalanceCard(s, { viewReturns(3) }, columns)
            val dueCount = s.followUps.count { !it.record.followUp!!.isAfter(today) }
            val deadlineCount = s.returns.count { !it.record.deadline!!.isBefore(today) && !it.record.deadline.isAfter(today.plusDays(7)) }
            if (!columns) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val nextReturn = s.returns.firstOrNull()?.record
                val nextFollowUp = s.followUps.firstOrNull()?.record
                AtAGlance(R.string.deadlines, Icons.Default.DateRange, stringResource(R.string.due_week, deadlineCount),
                    nextReturn?.item, nextReturn?.deadline?.let { urgencyLabel(it, today, true) }, Modifier.weight(1f)) { viewReturns(1) }
                AtAGlance(R.string.followups, Icons.Default.Email, stringResource(R.string.due_now, dueCount),
                    nextFollowUp?.merchant, nextFollowUp?.followUp?.let { urgencyLabel(it, today, false) }, Modifier.weight(1f)) { viewReturns(2) }
            }
            if (columns) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.weight(1f)) { QueueCard(R.string.deadlines, Icons.Default.DateRange, deadlineCount, R.string.due_week,
                    s.returns.take(3), today, 1, detail) { viewReturns(1) } }
                Box(Modifier.weight(1f)) { QueueCard(R.string.followups, Icons.Default.Email, dueCount, R.string.due_now,
                    s.followUps.take(3), today, 2, detail) { viewReturns(2) } }
            } else {
                QueueCard(R.string.deadlines, Icons.Default.DateRange, deadlineCount, R.string.due_week, s.returns.take(3), today, 1, detail) { viewReturns(1) }
                QueueCard(R.string.followups, Icons.Default.Email, dueCount, R.string.due_now, s.followUps.take(3), today, 2, detail) { viewReturns(2) }
            }
            if (s.pending.isEmpty() && s.returns.isEmpty() && s.followUps.isEmpty() && s.recent.isEmpty()) {
                if (s.dispatch.isEmpty() && s.waitingForResponse.isEmpty()) OutlinedButton(onClick = add, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.add_purchase))
                }
            }
            if (s.pending.isNotEmpty()) QueueCard(R.string.pending, Icons.AutoMirrored.Filled.List, s.pending.size, R.string.tracked_count,
                s.pending.take(4), today, 3, detail) { viewReturns(3) }
            if (s.dispatch.isNotEmpty()) QueueCard(R.string.awaiting_dispatch, Icons.Default.PlayArrow, s.dispatch.size, R.string.tracked_count,
                s.dispatch.take(3), today, 4, detail) { viewReturns(4) }
            if (s.waitingForResponse.isNotEmpty()) QueueCard(R.string.awaiting_response, Icons.Default.Email, s.waitingForResponse.size,
                R.string.tracked_count, s.waitingForResponse.take(3), today, 5, detail) { viewReturns(5) }
            if (s.recent.isNotEmpty()) QueueCard(R.string.recent, Icons.Default.Check, s.recent.size, R.string.tracked_count,
                s.recent.take(3), today, 6, detail) { viewReturns(6) }
            if (s.warranties.isNotEmpty()) QueueCard(R.string.warranty_alerts, Icons.Default.Star, s.warranties.size, R.string.tracked_count,
                s.warranties.take(3), today, 7, detail, viewWarranties)
        }
    }
}

@Composable
private fun AtAGlance(title: Int, glyph: ImageVector, count: String, first: String?, urgency: String?, modifier: Modifier, open: () -> Unit) {
    Surface(onClick = open, modifier = modifier.heightIn(min = 120.dp), color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(glyph, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(stringResource(title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(count, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (first != null) Text(first, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (urgency != null) Text(urgency, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BalanceCard(s: Dashboard, viewPending: () -> Unit, wide: Boolean) {
    val locale = appLocale()
    Surface(color = Color(0xFF15382E), contentColor = Color(0xFFF4FFF5), shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
        Box {
            if (wide) Canvas(Modifier.align(Alignment.TopEnd).size(186.dp, 130.dp)) {
                val center = Offset(size.width * .77f, size.height * .36f)
                for (i in 1..5) drawCircle(Color(0xFF457560), radius = size.width * (.15f + i * .10f),
                    center = center, style = Stroke(width = 1.dp.toPx()))
                drawLine(Color(0xFF9CDEB5), Offset(center.x - 14.dp.toPx(), center.y),
                    Offset(center.x + 14.dp.toPx(), center.y), 2.dp.toPx(), StrokeCap.Round)
                drawLine(Color(0xFF9CDEB5), Offset(center.x, center.y - 14.dp.toPx()),
                    Offset(center.x, center.y + 14.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 17.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(7.dp).background(Color(0xFFA3E8B8), CircleShape))
                    Text(stringResource(R.string.waiting_for), color = Color(0xFFB5E9C6), fontWeight = FontWeight.SemiBold)
                }
                if (s.outstanding.isEmpty()) Text(stringResource(R.string.no_outstanding), fontSize = 26.sp, lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
                s.outstanding.forEach { (code, amount) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(Money.format(amount, code, locale), modifier = Modifier.weight(1f, fill = false),
                            fontSize = 36.sp, lineHeight = 43.sp, fontWeight = FontWeight.Bold)
                        Text(code, style = MaterialTheme.typography.labelMedium, color = Color(0xFFB5E9C6))
                    }
                }
                Text(pluralStringResource(R.plurals.pending_cases, s.pending.size, s.pending.size), style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFBCD9C8))
                if (s.pending.isNotEmpty()) TextButton(onClick = viewPending, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFBAF0CA)),
                    contentPadding = PaddingValues(horizontal = 0.dp)) { Text(stringResource(R.string.open_pending)); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                HorizontalDivider(Modifier.padding(vertical = 7.dp), color = Color(0xFF4D6D5E))
                Text(stringResource(R.string.received), color = Color(0xFFB5E9C6), style = MaterialTheme.typography.labelLarge)
                if (s.received.isEmpty()) Text(stringResource(R.string.no_received), style = MaterialTheme.typography.bodySmall)
                s.received.forEach { (code, amount) ->
                    Text("${Money.format(amount, code, locale)}  ·  $code", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
internal fun QueueCard(title: Int, icon: ImageVector, count: Int, countLabel: Int, rows: List<CaseSnapshot>, today: LocalDate,
                       mode: Int, detail: (Long) -> Unit, viewAll: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val warning = mode == 1 || mode == 2
                val iconBackground = if (warning && !isSystemInDarkTheme()) Color(0xFFF8EACE) else MaterialTheme.colorScheme.primaryContainer
                val iconInk = if (warning && !isSystemInDarkTheme()) Color(0xFF704716) else MaterialTheme.colorScheme.onPrimaryContainer
                Box(Modifier.size(42.dp).background(iconBackground, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = iconInk, modifier = Modifier.size(21.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() })
                    Text(stringResource(countLabel, count), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (rows.isNotEmpty()) IconButton(onClick = viewAll) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.view_all, stringResource(title))) }
            }
            if (rows.isEmpty()) {
                Text(stringResource(if (mode == 1) R.string.no_deadlines else R.string.no_followups),
                    Modifier.padding(top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            rows.forEach {
                HorizontalDivider(Modifier.padding(top = 3.dp), color = MaterialTheme.colorScheme.outlineVariant)
                RecordLine(it, today, mode, detail)
            }
        }
    }
}

@Composable
internal fun RecordLine(item: CaseSnapshot, today: LocalDate, mode: Int, detail: (Long) -> Unit) {
    val c = item.record
    val locale = appLocale()
    val date = when (mode) { 1 -> c.deadline; 2 -> c.followUp; 7 -> c.warranty; else -> null }
    Row(Modifier.fillMaxWidth().clickable { detail(c.id) }.heightIn(min = 60.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(if (mode == 2) c.merchant else c.item, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
            Text(if (mode == 2) c.item else c.merchant,
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if (date != null) {
            val days = ChronoUnit.DAYS.between(today, date)
            val badge = urgencyLabel(date, today, mode == 1)
            Surface(color = when {
                days < 0 -> MaterialTheme.colorScheme.errorContainer
                days <= 3 && !isSystemInDarkTheme() -> Color(0xFFF8EACE)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }, contentColor = when {
                days < 0 -> MaterialTheme.colorScheme.onErrorContainer
                days <= 3 && !isSystemInDarkTheme() -> Color(0xFF704716)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }, shape = RoundedCornerShape(8.dp)) {
                Text(badge, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
            }
        } else {
            Column(Modifier.widthIn(max = 138.dp), horizontalAlignment = Alignment.End) {
                if (mode == 3 || mode == 6) Text(Money.format(if (mode == 6) item.received else item.outstanding, c.currency, locale),
                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(item.stage.label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun urgencyLabel(date: LocalDate, today: LocalDate, returnDeadline: Boolean): String {
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days < 0 -> stringResource(if (returnDeadline) R.string.missed else R.string.overdue)
        days == 0L -> stringResource(R.string.today)
        days == 1L -> stringResource(R.string.tomorrow)
        days <= 7L -> pluralStringResource(R.plurals.days_away, days.toInt(), days.toInt())
        else -> dateLabel(date, appLocale())
    }
}

private fun demoCases(context: Context, now: LocalDate): List<CaseSnapshot> {
    fun sample(id: Long, item: Int, merchant: String, expected: Long, stage: Stage,
               deadline: Long? = null, follow: Long? = null, received: Long? = null, currency: String = "GBP") =
        CaseSnapshot(RecoveryCase(id, context.getString(item), merchant, currency, expected, stage, deadline?.let(now::plusDays), follow?.let(now::plusDays)),
            received?.let { listOf(Receipt(id, it, now.minusDays(2))) }.orEmpty())
    return listOf(
        sample(1, R.string.sample_bag, "North & Coast", 4800, Stage.AWAITING_DISPATCH, 0),
        sample(2, R.string.sample_headphones, "Arc Workshop", 12900, Stage.PROCESSING, follow = -2, received = 3000),
        sample(3, R.string.sample_shoes, "Field Goods", 8500, Stage.REQUESTED, 3, 0),
        sample(4, R.string.sample_lamp, "Juniper House", 4200, Stage.MERCHANT_RECEIVED, follow = 4),
        sample(5, R.string.sample_mug, "West & Clay", 2400, Stage.PROCESSING, received = 2400),
        sample(6, R.string.sample_gloves, "Velodrome Atelier", 3850, Stage.REFUND_APPROVED, follow = 8, currency = "EUR"),
        sample(7, R.string.sample_scanner, "Paper Loft", 0, Stage.NOT_REQUESTED).let { it.copy(record = it.record.copy(warranty = now.plusDays(24))) }
    )
}
