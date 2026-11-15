package com.example.llama

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.gguf.GgufMetadataReader
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.onCompletion
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var engine: InferenceEngine
    private var generationJob: Job? = null
    private var isModelReady = false
    private var isGenerating = false
    private var thinkMode = 0

    private val messages = mutableListOf<Message>()
    private val lastAssistantMsg = StringBuilder()
    private lateinit var messageAdapter: MessageAdapter
    private val db by lazy { AppDatabase.getDatabase(applicationContext) }

    private lateinit var statusTitleTv: TextView
    private lateinit var contextBadgeTv: TextView
    private lateinit var speedTv: TextView
    private lateinit var messagesRv: RecyclerView
    private lateinit var userInputEt: EditText
    private lateinit var actionBtn: Button
    private lateinit var thinkModeBtn: Button
    private lateinit var canvasEditorEt: EditText
    private lateinit var canvasWebView: WebView
    private lateinit var terminalOutputTv: TextView
    private lateinit var ramInfoTv: TextView
    private lateinit var systemPromptEt: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        statusTitleTv = findViewById(R.id.statusTitleTv)
        contextBadgeTv = findViewById(R.id.contextBadgeTv)
        speedTv = findViewById(R.id.speedTv)
        messagesRv = findViewById(R.id.messagesRv)
        userInputEt = findViewById(R.id.userInputEt)
        actionBtn = findViewById(R.id.actionBtn)
        thinkModeBtn = findViewById(R.id.thinkModeBtn)
        canvasEditorEt = findViewById(R.id.canvasEditorEt)
        canvasWebView = findViewById(R.id.canvasWebView)
        terminalOutputTv = findViewById(R.id.terminalOutputTv)
        ramInfoTv = findViewById(R.id.ramInfoTv)
        systemPromptEt = findViewById(R.id.systemPromptEt)

        messageAdapter = MessageAdapter(messages)
        messagesRv.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        messagesRv.adapter = messageAdapter

        canvasWebView.settings.javaScriptEnabled = true
        canvasWebView.settings.domStorageEnabled = true

        lifecycleScope.launch(Dispatchers.Default) {
            engine = AiChat.getInferenceEngine(applicationContext)
        }

        updateRamStats()
        setupNavigation()
        setupControls()
    }

    private fun setupNavigation() {
        val tabChat = findViewById<View>(R.id.tabChat)
        val tabCanvas = findViewById<View>(R.id.tabCanvas)
        val tabTerminal = findViewById<View>(R.id.tabTerminal)
        val tabSettings = findViewById<View>(R.id.tabSettings)

        val navChat = findViewById<Button>(R.id.navChatBtn)
        val navCanvas = findViewById<Button>(R.id.navCanvasBtn)
        val navTerminal = findViewById<Button>(R.id.navTerminalBtn)
        val navSettings = findViewById<Button>(R.id.navSettingsBtn)

        fun selectTab(index: Int) {
            tabChat.visibility = if (index == 0) View.VISIBLE else View.GONE
            tabCanvas.visibility = if (index == 1) View.VISIBLE else View.GONE
            tabTerminal.visibility = if (index == 2) View.VISIBLE else View.GONE
            tabSettings.visibility = if (index == 3) View.VISIBLE else View.GONE

            val active = 0xFF2563EB.toInt()
            val inactive = 0xFF151C28.toInt()
            navChat.setBackgroundColor(if (index == 0) active else inactive)
            navCanvas.setBackgroundColor(if (index == 1) active else inactive)
            navTerminal.setBackgroundColor(if (index == 2) active else inactive)
            navSettings.setBackgroundColor(if (index == 3) active else inactive)
            if (index == 3) updateRamStats()
        }

        navChat.setOnClickListener { selectTab(0) }
        navCanvas.setOnClickListener { selectTab(1) }
        navTerminal.setOnClickListener { selectTab(2) }
        navSettings.setOnClickListener { selectTab(3) }
    }

    private fun setupControls() {
        actionBtn.setOnClickListener {
            if (!isModelReady) {
                getContent.launch(arrayOf("*/*"))
            } else if (isGenerating) {
                generationJob?.cancel()
                isGenerating = false
                actionBtn.text = "Отправить"
                actionBtn.setBackgroundColor(0xFF2563EB.toInt())
            } else {
                sendMessage()
            }
        }

        thinkModeBtn.setOnClickListener {
            thinkMode = (thinkMode + 1) % 3
            thinkModeBtn.text = when (thinkMode) {
                0 -> "Думать: ВЫКЛ"
                1 -> "Думать: АВТО"
                else -> "Думать: ВКЛ"
            }
        }

        findViewById<Button>(R.id.openLastCodeBtn).setOnClickListener {
            val lastAi = messages.lastOrNull { !it.isUser }?.content ?: ""
            val codeBlock = Regex("```[a-zA-Z]*\\n([\\s\\S]*?)```").find(lastAi)?.groupValues?.get(1) ?: lastAi
            if (codeBlock.isNotBlank()) {
                canvasEditorEt.setText(codeBlock.trim())
                findViewById<Button>(R.id.navCanvasBtn).performClick()
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
            canvasWebView.loadDataWithBaseURL(null, canvasEditorEt.text.toString(), "text/html", "UTF-8", null)
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
                        val p = ProcessBuilder("sh", "-c", cmd).directory(filesDir).redirectErrorStream(true).start()
                        p.inputStream.bufferedReader().readText()
                    } catch (e: Exception) { "Ошибка: ${e.message}" }
                    withContext(Dispatchers.Main) { terminalOutputTv.append(out + "\n") }
                }
            }
        }

        findViewById<Button>(R.id.selectNewModelBtn).setOnClickListener { getContent.launch(arrayOf("*/*")) }
        findViewById<Button>(R.id.clearChatBtn).setOnClickListener {
            messages.clear()
            messageAdapter.notifyDataSetChanged()
            updateRamStats()
        }
        findViewById<Button>(R.id.applySystemBtn).setOnClickListener {
            if (isModelReady) {
                lifecycleScope.launch(Dispatchers.Default) {
                    engine.setSystemPrompt(systemPromptEt.text.toString())
                    withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "Роль обновлена!", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }

    private fun sendMessage() {
        val rawInput = userInputEt.text.toString().trim()
        if (rawInput.isEmpty()) return

        userInputEt.text.clear()
        messages.add(Message(UUID.randomUUID().toString(), rawInput, true))
        lastAssistantMsg.clear()
        messages.add(Message(UUID.randomUUID().toString(), "", false))
        messageAdapter.notifyDataSetChanged()
        messagesRv.scrollToPosition(messages.size - 1)

        isGenerating = true
        actionBtn.text = "Стоп"
        actionBtn.setBackgroundColor(0xFFDC2626.toInt())

        val promptToSend = when (thinkMode) {
            0 -> "$rawInput /no_think"
            1 -> "$rawInput /think"
            else -> rawInput
        }
        
        val startTime = System.currentTimeMillis()
        var tokenCount = 0
        
        generationJob = lifecycleScope.launch(Dispatchers.Default) {
            engine.sendUserPrompt(promptToSend)
                .onCompletion {
                    withContext(Dispatchers.Main) {
                        isGenerating = false
                        actionBtn.text = "Отправить"
                        actionBtn.setBackgroundColor(0xFF2563EB.toInt())
                        updateRamStats()
                    }
                }
                .collect { token ->
                    tokenCount++
                    lastAssistantMsg.append(token)
                    val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.1)
                    val tps = String.format("%.1f", tokenCount / elapsedSec)
                    withContext(Dispatchers.Main) {
                        speedTv.text = "$tps ток/с"
                        if (messages.isNotEmpty() && !messages.last().isUser) {
                            messages[messages.size - 1] = messages.last().copy(content = lastAssistantMsg.toString())
                            messageAdapter.notifyItemChanged(messages.size - 1)
                            messagesRv.scrollToPosition(messages.size - 1)
                        }
                    }
                }
        }
    }

    private val getContent = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { handleSelectedModel(it) }
    }

    private fun handleSelectedModel(uri: Uri) {
        actionBtn.isEnabled = false
        statusTitleTv.text = "Чтение GGUF..."
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val metadata = contentResolver.openInputStream(uri)?.use {
                    GgufMetadataReader.create().readStructuredMetadata(it)
                }
                val modelName = (metadata?.basic?.name ?: "bonsai-model") + ".gguf"
                val dir = File(filesDir, "models").apply { mkdirs() }
                val file = File(dir, modelName)

                val inputStream = contentResolver.openInputStream(uri)
                val fileSize = inputStream?.available()?.toLong() ?: 0L
                inputStream?.close()

                if (file.exists() && file.length() == fileSize) {
                    withContext(Dispatchers.Main) { statusTitleTv.text = "Файл уже есть. Загрузка..." }
                } else {
                    withContext(Dispatchers.Main) { statusTitleTv.text = "Копирование..." }
                    if (file.exists()) file.delete()
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(file).use { output -> input.copyTo(output) }
                    }
                }

                db.modelDao().insertModel(LocalModel(modelName, file.path, file.length()))

                withContext(Dispatchers.Main) { statusTitleTv.text = "Загрузка в ОЗУ..." }
                engine.loadModel(file.path)
                engine.setSystemPrompt(systemPromptEt.text.toString())
                
                withContext(Dispatchers.Main) {
                    isModelReady = true
                    actionBtn.isEnabled = true
                    actionBtn.text = "Отправить"
                    userInputEt.hint = "Сообщение..."
                    statusTitleTv.text = "Bonsai • ${metadata?.basic?.sizeLabel ?: "Ready"}"
                    updateRamStats()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusTitleTv.text = "Ошибка"
                    Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    actionBtn.isEnabled = true
                }
            }
        }
    }

    private fun updateRamStats() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val freeGb = String.format("%.1f", mi.availMem / (1024.0 * 1024.0 * 1024.0))
        val totalGb = String.format("%.1f", mi.totalMem / (1024.0 * 1024.0 * 1024.0))
        val usedPct = (((mi.totalMem - mi.availMem).toDouble() / mi.totalMem) * 100).toInt()
        contextBadgeTv.text = "$usedPct% ОЗУ"
        ramInfoTv.text = "Свободно: $freeGb ГБ из $totalGb ГБ ($usedPct%)"
    }

    override fun onStop() {
        generationJob?.cancel()
        super.onStop()
    }

    override fun onDestroy() {
        if (::engine.isInitialized) engine.destroy()
        super.onDestroy()
    }
}

// --- МОДЕЛЬ И АДАПТЕР (Встроены для удобства сборки) ---

data class Message(val id: String, val content: String, val isUser: Boolean)

class MessageAdapter(private val messages: List<Message>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_ASSISTANT = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_ASSISTANT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_USER) {
            UserMessageViewHolder(inflater.inflate(R.layout.item_message_user, parent, false))
        } else {
            AssistantMessageViewHolder(inflater.inflate(R.layout.item_message_assistant, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        if (holder is UserMessageViewHolder) {
            holder.textView.text = message.content
        } else if (holder is AssistantMessageViewHolder) {
            val rawText = message.content
            val thinkStart = rawText.indexOf("<think>")
            val thinkEnd = rawText.indexOf("</think>")

            if (thinkStart != -1 && thinkEnd != -1) {
                val thought = rawText.substring(thinkStart + 7, thinkEnd).trim()
                val answer = rawText.substring(thinkEnd + 8).trim()
                holder.thinkContainer.visibility = View.VISIBLE
                holder.thinkContent.text = thought
                holder.thinkHeader.text = "💭 Показать размышления ˅"
                holder.textView.text = answer
                holder.thinkHeader.setOnClickListener {
                    if (holder.thinkContent.visibility == View.GONE) {
                        holder.thinkContent.visibility = View.VISIBLE
                        holder.thinkHeader.text = "💭 Скрыть размышления ˄"
                    } else {
                        holder.thinkContent.visibility = View.GONE
                        holder.thinkHeader.text = "💭 Показать размышления ˅"
                    }
                }
            } else if (thinkStart != -1) {
                holder.thinkContainer.visibility = View.VISIBLE
                holder.thinkContent.visibility = View.VISIBLE
                holder.thinkHeader.text = "⏳ Модель размышляет..."
                holder.thinkContent.text = rawText.substring(thinkStart + 7).trim()
                holder.textView.text = ""
            } else {
                holder.thinkContainer.visibility = View.GONE
                holder.textView.text = rawText
            }
        }
    }

    override fun getItemCount(): Int = messages.size

    class UserMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.msg_content)
    }

    class AssistantMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.msg_content)
        val thinkContainer: LinearLayout = view.findViewById(R.id.thinkContainer)
        val thinkHeader: TextView = view.findViewById(R.id.thinkHeader)
        val thinkContent: TextView = view.findViewById(R.id.thinkContent)
    }
}
