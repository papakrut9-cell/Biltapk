package com.example.llama

import android.Manifest
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.HapticFeedbackConstants
import android.view.View
import android.webkit.WebView
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.gguf.GgufMetadataReader
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.onCompletion
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var engine: InferenceEngine
    private var generationJob: Job? = null
    private var engineReady = false      // движок инициализирован
    private var isModelReady = false     // модель загружена в ОЗУ
    private var isGenerating = false
    private var thinkMode = 0

    private var generationStart = 0L
    private var tokenCount = 0
    private var lastUiTs = 0L

    private val lastAssistantMsg = StringBuilder()
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var modelAdapter: ModelAdapter
    private val db by lazy { AppDatabase.getDatabase(applicationContext) }

    private lateinit var statusTitleTv: TextView
    private lateinit var contextBadgeTv: TextView
    private lateinit var speedTv: TextView
    private lateinit var messagesRv: RecyclerView
    private lateinit var modelsRv: RecyclerView
    private lateinit var userInputEt: EditText
    private lateinit var actionBtn: Button
    private lateinit var thinkModeBtn: TextView
    private lateinit var canvasEditorEt: EditText
    private lateinit var canvasWebView: WebView
    private lateinit var terminalOutputTv: TextView
    private lateinit var ramInfoTv: TextView
    private lateinit var systemPromptEt: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        statusTitleTv = findViewById(R.id.statusTitleTv)
        contextBadgeTv = findViewById(R.id.contextBadgeTv)
        speedTv = findViewById(R.id.speedTv)
        messagesRv = findViewById(R.id.messagesRv)
        modelsRv = findViewById(R.id.modelsRv)
        userInputEt = findViewById(R.id.userInputEt)
        actionBtn = findViewById(R.id.actionBtn)
        thinkModeBtn = findViewById(R.id.thinkModeBtn)
        canvasEditorEt = findViewById(R.id.canvasEditorEt)
        canvasWebView = findViewById(R.id.canvasWebView)
        terminalOutputTv = findViewById(R.id.terminalOutputTv)
        ramInfoTv = findViewById(R.id.ramInfoTv)
        systemPromptEt = findViewById(R.id.systemPromptEt)

        messageAdapter = MessageAdapter()
        messagesRv.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        (messagesRv.itemAnimator as? DefaultItemAnimator)?.supportsChangeAnimations = false
        messagesRv.adapter = messageAdapter

        modelAdapter = ModelAdapter { model -> confirmDeleteModel(model) }
        modelsRv.layoutManager = LinearLayoutManager(this)
        (modelsRv.itemAnimator as? DefaultItemAnimator)?.supportsChangeAnimations = false
        modelsRv.adapter = modelAdapter

        canvasWebView.settings.javaScriptEnabled = true
        canvasWebView.settings.domStorageEnabled = true

        // Edge-to-edge без двойных отступов: сами применяем insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootLayout)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                engine = AiChat.getInferenceEngine(applicationContext)
            } catch (e: Throwable) {
                showStartupError("Ошибка движка: ${e.javaClass.simpleName}: ${e.message}")
                return@launch
            }
            withContext(Dispatchers.Main) {
                engineReady = true
                if (!isModelReady) actionBtn.text = "📁"
            }
        }

        loadChatHistory()
        showSavedCrashLog()
        updateRamStats()
        setupNavigation()
        setupControls()
    }

    private fun showStartupError(message: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            statusTitleTv.text = "Ошибка"
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun showSavedCrashLog() {
        val file = BonsaiApp.crashFile
        if (!file.exists() || file.length() == 0L) return
        val text = try {
            file.readText().lines().takeLast(60).joinToString("\n")
        } catch (_: Exception) {
            return
        }
        lifecycleScope.launch(Dispatchers.Main) {
            terminalOutputTv.append("\n=== ЛОГ ПРЕДЫДУЩЕГО ПАДЕНИЯ ===\n$text\n=== КОНЕЦ ЛОГА ===\n")
            Toast.makeText(
                this@MainActivity,
                "Обнаружен лог падения — вкладка «Консоль»",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun loadChatHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            val history = try {
                db.chatDao().getAllMessages()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusTitleTv.text = "Ошибка БД"
                    Toast.makeText(
                        this@MainActivity,
                        "Ошибка базы чата: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                if (history.isNotEmpty()) {
                    messageAdapter.submitList(history.map { Message(it.id, it.content, it.isUser, it.timestamp) }) {
                        messagesRv.scrollToPosition(messageAdapter.itemCount - 1)
                    }
                }
            }
        }
    }

    private fun setupNavigation() {
        val tabChat = findViewById<View>(R.id.tabChat)
        val tabCanvas = findViewById<View>(R.id.tabCanvas)
        val tabTerminal = findViewById<View>(R.id.tabTerminal)
        val tabSettings = findViewById<View>(R.id.tabSettings)

        val navChat = findViewById<View>(R.id.navChatBtn)
        val navCanvas = findViewById<View>(R.id.navCanvasBtn)
        val navTerminal = findViewById<View>(R.id.navTerminalBtn)
        val navSettings = findViewById<View>(R.id.navSettingsBtn)

        fun selectTab(index: Int) {
            tabChat.visibility = if (index == 0) View.VISIBLE else View.GONE
            tabCanvas.visibility = if (index == 1) View.VISIBLE else View.GONE
            tabTerminal.visibility = if (index == 2) View.VISIBLE else View.GONE
            tabSettings.visibility = if (index == 3) View.VISIBLE else View.GONE

            navChat.setBackgroundResource(if (index == 0) R.drawable.bg_nav_selected else android.R.color.transparent)
            navCanvas.setBackgroundResource(if (index == 1) R.drawable.bg_nav_selected else android.R.color.transparent)
            navTerminal.setBackgroundResource(if (index == 2) R.drawable.bg_nav_selected else android.R.color.transparent)
            navSettings.setBackgroundResource(if (index == 3) R.drawable.bg_nav_selected else android.R.color.transparent)

            if (index == 3) {
                updateRamStats()
                loadModels()
            }
        }

        navChat.setOnClickListener { selectTab(0) }
        navCanvas.setOnClickListener { selectTab(1) }
        navTerminal.setOnClickListener { selectTab(2) }
        navSettings.setOnClickListener { selectTab(3) }
    }

    private fun setupControls() {
        actionBtn.setOnClickListener { v ->
            when {
                !engineReady -> {
                    v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    Toast.makeText(this, "Движок ещё запускается, секунду...", Toast.LENGTH_SHORT).show()
                }
                isGenerating -> generationJob?.cancel()
                else -> {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    if (!isModelReady) {
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        getContent.launch(arrayOf("*/*"))
                    } else {
                        sendMessage()
                    }
                }
            }
        }

        thinkModeBtn.setOnClickListener {
            thinkMode = (thinkMode + 1) % 3
            thinkModeBtn.text = when (thinkMode) {
                0 -> "💭 Думать: ВЫКЛ"
                1 -> "💭 Думать: АВТО"
                else -> "💭 Думать: ВКЛ"
            }
        }

        findViewById<TextView>(R.id.openLastCodeBtn).setOnClickListener {
            val lastAi = messageAdapter.currentList.lastOrNull { !it.isUser }?.content ?: ""
            val cleanAi = lastAi.replace(Regex("</think>"), "").trim()

            val fence = "```"
            val startIndex = cleanAi.indexOf(fence)
            val codeBlock = if (startIndex != -1) {
                var code = cleanAi.substring(startIndex)
                code = code.replaceFirst(Regex("^" + fence + "[a-zA-Z]*\\n?"), "")
                if (code.contains(fence)) {
                    code = code.substringBefore(fence)
                }
                code.trim()
            } else {
                cleanAi
            }

            if (codeBlock.isNotBlank()) {
                canvasEditorEt.setText(codeBlock)
                findViewById<View>(R.id.navCanvasBtn).performClick()
            } else {
                Toast.makeText(this, "Нет кода", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.canvasCodeModeBtn).setOnClickListener {
            canvasEditorEt.visibility = View.VISIBLE
            canvasWebView.visibility = View.GONE
        }
        findViewById<Button>(R.id.canvasPreviewModeBtn).setOnClickListener {
            canvasEditorEt.visibility = View.GONE
            canvasWebView.visibility = View.VISIBLE
            canvasWebView.loadDataWithBaseURL(
                null,
                canvasPreviewHtml(canvasEditorEt.text.toString()),
                "text/html",
                "UTF-8",
                null
            )
        }
        findViewById<Button>(R.id.canvasCopyBtn).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("code", canvasEditorEt.text.toString()))
            Toast.makeText(this, "Код скопирован", Toast.LENGTH_SHORT).show()
        }

        val termInput = findViewById<EditText>(R.id.terminalInputEt)
        findViewById<Button>(R.id.terminalRunBtn).setOnClickListener {
            val cmd = termInput.text.toString().trim()
            if (cmd.isNotEmpty()) {
                termInput.text.clear()
                terminalOutputTv.append("\n$ $cmd\n")
                lifecycleScope.launch(Dispatchers.IO) {
                    val out = try {
                        val p = ProcessBuilder("sh", "-c", cmd)
                            .directory(filesDir)
                            .redirectErrorStream(true)
                            .start()
                        if (p.waitFor(15, TimeUnit.SECONDS)) {
                            p.inputStream.bufferedReader().readText().ifBlank { "(пусто)" }
                        } else {
                            p.destroy()
                            "⏱ Команда превысила 15 секунд и была остановлена"
                        }
                    } catch (e: Exception) {
                        "Ошибка: ${e.message}"
                    }
                    withContext(Dispatchers.Main) {
                        terminalOutputTv.append(out + "\n")
                        (terminalOutputTv.parent as? ScrollView)?.fullScroll(View.FOCUS_DOWN)
                    }
                }
            }
        }

        findViewById<Button>(R.id.selectNewModelBtn).setOnClickListener {
            when {
                isGenerating -> Toast.makeText(this, "Дождитесь завершения ответа", Toast.LENGTH_SHORT).show()
                isModelReady -> confirmReloadModel()
                else -> getContent.launch(arrayOf("*/*"))
            }
        }

        findViewById<Button>(R.id.clearChatBtn).setOnClickListener { confirmClearChat() }

        findViewById<Button>(R.id.applySystemBtn).setOnClickListener {
            when {
                !engineReady -> Toast.makeText(this, "Движок ещё запускается, секунду...", Toast.LENGTH_SHORT).show()
                !isModelReady -> Toast.makeText(this, "Модель ещё не загружена. Роль применится при выборе модели.", Toast.LENGTH_SHORT).show()
                else -> {
                    lifecycleScope.launch {
                        try {
                            engine.setSystemPrompt(systemPromptEt.text.toString())
                            Toast.makeText(this@MainActivity, "Роль применена", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun confirmReloadModel() {
        AlertDialog.Builder(this)
            .setTitle("Загрузить другую модель?")
            .setMessage("Текущая модель будет выгружена из памяти.")
            .setPositiveButton("Загрузить") { _, _ -> getContent.launch(arrayOf("*/*")) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun confirmClearChat() {
        AlertDialog.Builder(this)
            .setTitle("Очистить чат?")
            .setMessage("История сообщений будет удалена безвозвратно.")
            .setPositiveButton("Очистить") { _, _ -> doClearChat() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun doClearChat() {
        lastAssistantMsg.clear()
        messageAdapter.submitList(emptyList())
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { db.chatDao().clearAll() }
            // Пробуем сбросить контекст модели любым доступным способом API движка
            var contextReset = false
            try {
                val resetMethod = engine.javaClass.methods.firstOrNull {
                    it.parameterCount == 0 &&
                        it.name.lowercase() in setOf("resetcontext", "clearcontext", "clearhistory", "reset", "newsession")
                }
                resetMethod?.invoke(engine)
                contextReset = resetMethod != null
            } catch (e: Exception) {
                // ignore
            }
            Toast.makeText(
                this@MainActivity,
                if (contextReset) "Чат и контекст модели очищены" else "Чат очищен (контекст модели сбросится при перезапуске)",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun confirmDeleteModel(model: LocalModel) {
        AlertDialog.Builder(this)
            .setTitle("Удалить модель?")
            .setMessage(model.name + "\n\nФайл с диска тоже будет удалён.")
            .setPositiveButton("Удалить") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        db.modelDao().deleteModel(model)
                        try { File(model.path).delete() } catch (e: Exception) { /* ignore */ }
                    }
                    loadModels()
                    updateRamStats()
                    Toast.makeText(this@MainActivity, "Модель удалена", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun loadModels() {
        lifecycleScope.launch(Dispatchers.IO) {
            val list = db.modelDao().getAllModels()
            withContext(Dispatchers.Main) { modelAdapter.submitList(list) }
        }
    }

    private fun sendMessage() {
        val rawInput = userInputEt.text.toString().trim()
        if (rawInput.isEmpty()) return

        userInputEt.text.clear()
        lastAssistantMsg.clear()
        tokenCount = 0
        lastUiTs = 0
        generationStart = System.currentTimeMillis()

        val userMsg = Message(UUID.randomUUID().toString(), rawInput, true)
        val aiMsg = Message(UUID.randomUUID().toString(), "", false)

        messageAdapter.isStreaming = true
        messageAdapter.submitList(messageAdapter.currentList + userMsg + aiMsg) {
            messagesRv.scrollToPosition(messageAdapter.itemCount - 1)
        }

        lifecycleScope.launch(Dispatchers.IO) {
            db.chatDao().insertMessage(ChatMessage(userMsg.id, rawInput, true, userMsg.timestamp))
        }

        isGenerating = true
        if (!hasNotificationPermission()) {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        GenerationService.start(this)
        actionBtn.text = "⏹"
        actionBtn.setBackgroundColor(getColor(R.color.stop_red))

        // Нативные токены режима размышлений для Qwen/DeepSeek
        val promptToSend = when (thinkMode) {
            0 -> "$rawInput /no_think"
            2 -> "$rawInput /think"
            else -> rawInput
        }

        generationJob = lifecycleScope.launch(Dispatchers.Default) {
            engine.sendUserPrompt(promptToSend)
                .onCompletion {
                    withContext(Dispatchers.Main) { finishGeneration() }
                }
                .collect { token ->
                    tokenCount++
                    lastAssistantMsg.append(token)
                    val now = System.currentTimeMillis()
                    if (now - lastUiTs >= 150) {
                        lastUiTs = now
                        withContext(Dispatchers.Main) { updateStreamingMessage() }
                    }
                }
        }
    }

    private fun updateStreamingMessage() {
        val elapsedSec = ((System.currentTimeMillis() - generationStart) / 1000.0).coerceAtLeast(0.1)
        speedTv.text = String.format(Locale.US, "%.1f т/с", tokenCount / elapsedSec)
        refreshLastAssistant(thinkMs = 0L)
    }

    private fun finishGeneration() {
        isGenerating = false
        GenerationService.stop(this)
        messageAdapter.isStreaming = false
        actionBtn.text = "➤"
        actionBtn.setBackgroundColor(getColor(R.color.accent_blue))

        val total = ((System.currentTimeMillis() - generationStart) / 1000.0).coerceAtLeast(0.1)
        speedTv.text = String.format(Locale.US, "%.1f т/с", tokenCount / total)
        refreshLastAssistant(thinkMs = System.currentTimeMillis() - generationStart)
        updateRamStats()

        val last = messageAdapter.currentList.lastOrNull()
        if (last != null && !last.isUser) {
            val content = lastAssistantMsg.toString()
            lifecycleScope.launch(Dispatchers.IO) {
                db.chatDao().insertMessage(ChatMessage(last.id, content, false, System.currentTimeMillis()))
            }
        }
    }

    private fun refreshLastAssistant(thinkMs: Long) {
        val last = messageAdapter.currentList.lastOrNull() ?: return
        if (!last.isUser) {
            messageAdapter.submitList(
                messageAdapter.currentList.dropLast(1) + last.copy(content = lastAssistantMsg.toString(), thinkMs = thinkMs)
            ) {
                messagesRv.scrollToPosition(messageAdapter.itemCount - 1)
            }
        }
    }

    private val getContent = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { handleSelectedModel(it) }
    }

    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && isGenerating) GenerationService.start(this)
        }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun canvasPreviewHtml(code: String): String {
        val esc = code
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        val marked = esc
            .replace(Regex("\"[^\"\\n]*\"|'[^'\\n]*'|`[^`]*`"), "\u0001s\u0002$0\u0003")
            .replace(Regex("/\\*[\\s\\S]*?\\*/|//[^\\n]*|&lt;!--[\\s\\S]*?--&gt;"), "\u0001c\u0002$0\u0003")
            .replace(Regex("\\b(fun|val|var|class|object|data|suspend|return|if|else|when|for|while|import|export|package|function|const|let|def|async|await|true|false|null|void|int|float|str|struct)\\b"), "\u0001k\u0002$0\u0003")
            .replace(Regex("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?\\b"), "\u0001n\u0002$0\u0003")
            .replace(Regex("(&lt;/?)([a-zA-Z][a-zA-Z0-9-]*)"), "$1\u0001t\u0002$2\u0003")

        val html = marked
            .replace("\u0001s\u0002", "<span class='tok-s'>")
            .replace("\u0001c\u0002", "<span class='tok-c'>")
            .replace("\u0001k\u0002", "<span class='tok-k'>")
            .replace("\u0001n\u0002", "<span class='tok-n'>")
            .replace("\u0001t\u0002", "<span class='tok-t'>")
            .replace("\u0003", "</span>")

        return """<!DOCTYPE html><html><head><meta charset='utf-8'/>""" +
            "<style>body{background:#0b0e14;color:#e6e9ef;margin:0}" +
            "pre{white-space:pre-wrap;word-break:break-word;padding:16px;margin:0;font-family:monospace;font-size:13px}" +
            ".tok-s{color:#4ade80}" +
            ".tok-c{color:#6b7280;font-style:italic}" +
            ".tok-k{color:#60a5fa}" +
            ".tok-n{color:#fbbf24}" +
            ".tok-t{color:#22d3ee}" +
            "</style></head><body><pre>$html</pre></body></html>"
    }

    private fun handleSelectedModel(uri: Uri) {
        if (!engineReady) {
            Toast.makeText(this, "Движок ещё запускается, секунду...", Toast.LENGTH_SHORT).show()
            return
        }
        statusTitleTv.text = "Чтение GGUF..."
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val metadata = contentResolver.openInputStream(uri)?.use {
                    GgufMetadataReader.create().readStructuredMetadata(it)
                }
                val modelName = (metadata?.basic?.name ?: "bonsai-model") + ".gguf"
                val dir = File(filesDir, "models").apply { mkdirs() }
                val file = File(dir, modelName)
                val fileSize = queryFileSize(uri)

                if (file.exists() && fileSize > 0 && file.length() == fileSize) {
                    withContext(Dispatchers.Main) { statusTitleTv.text = "Файл уже есть. Загрузка..." }
                } else {
                    if (file.exists()) file.delete()
                    withContext(Dispatchers.Main) { statusTitleTv.text = "Копирование 0.0 ГБ..." }
                    // Копируем кусками с прогрессом: файлы GGUF бывают по 4+ ГБ
                    val buf = ByteArray(8 * 1024 * 1024)
                    var copied = 0L
                    var lastReport = 0L
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(file).use { output ->
                            while (true) {
                                val r = input.read(buf)
                                if (r <= 0) break
                                output.write(buf, 0, r)
                                copied += r
                                if (copied - lastReport >= 512L * 1024 * 1024) {
                                    lastReport = copied
                                    val g = copied / 1e9
                                    withContext(Dispatchers.Main) {
                                        statusTitleTv.text = String.format(Locale.US, "Копирование %.1f ГБ...", g)
                                    }
                                }
                            }
                        }
                    }
                }

                db.modelDao().insertModel(
                    LocalModel(modelName, file.path, if (fileSize > 0) fileSize else file.length())
                )

                withContext(Dispatchers.Main) { statusTitleTv.text = "Загрузка в ОЗУ..." }
                engine.loadModel(file.path)
                engine.setSystemPrompt(systemPromptEt.text.toString())

                withContext(Dispatchers.Main) {
                    isModelReady = true
                    actionBtn.isEnabled = true
                    actionBtn.text = "➤"
                    actionBtn.setBackgroundColor(getColor(R.color.accent_blue))
                    userInputEt.hint = "Спросите Bonsai..."
                    statusTitleTv.text = "Bonsai • ${metadata?.basic?.sizeLabel ?: "Ready"}"
                    updateRamStats()
                    loadModels()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusTitleTv.text = "Ошибка"
                    Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun queryFileSize(uri: Uri): Long {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && c.moveToFirst()) c.getLong(idx) else 0L
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun updateRamStats() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val freeGb = String.format(Locale.US, "%.1f", mi.availMem / (1024.0 * 1024.0 * 1024.0))
        val totalGb = String.format(Locale.US, "%.1f", mi.totalMem / (1024.0 * 1024.0 * 1024.0))
        val usedPct = (((mi.totalMem - mi.availMem).toDouble() / mi.totalMem) * 100).toInt()
        contextBadgeTv.text = "$usedPct% ОЗУ"
        ramInfoTv.text = "Свободно: $freeGb ГБ из $totalGb ГБ ($usedPct%)"
    }

    override fun onDestroy() {
        GenerationService.stop(this)
        if (::engine.isInitialized) engine.destroy()
        super.onDestroy()
    }
}
