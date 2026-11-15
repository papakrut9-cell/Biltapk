package com.bonusai.studio

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewflipper.ViewFlipper
import com.google.android.material.tabs.TabLayout

class MainActivity : AppCompatActivity() {

    // UI Components
    private lateinit var tabLayout: TabLayout
    private lateinit var viewFlipper: ViewFlipper
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var terminalInput: EditText
    private lateinit var terminalOutput: TextView

    // Adapters & Services (Mocked for now)
    private var chatAdapter: ChatAdapter? = null
    private val engine = AIChatService() // Заглушка для движка ИИ

    // Simulated States
    private var isGenerating = false
    private var currentAiMessage = StringBuilder()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupTabs()
        setupChat()
        setupTerminal()
    }

    private fun initViews() {
        tabLayout = findViewById(R.id.tabLayout)
        viewFlipper = findViewById(R.id.viewFlipper)
        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        terminalInput = findViewById(R.id.terminalInput)
        terminalOutput = findViewById(R.id.terminalOutput)
        
        // Настройка вкладок
        tabLayout.addTab(tabLayout.newTab().setText("Главная"))
        tabLayout.addTab(tabLayout.newTab().setText("IDE"))
        tabLayout.addTab(tabLayout.newTab().setText("Чат"))
        tabLayout.addTab(tabLayout.newTab().setText("Терминал"))
    }

    private fun setupTabs() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let {
                    viewFlipper.displayedChild = it.position
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupChat() {
        // TODO: Тебе нужно создать класс ChatAdapter и макет элемента чата
        // chatAdapter = ChatAdapter(mutableListOf())
        // chatRecyclerView.layoutManager = LinearLayoutManager(this)
        // chatRecyclerView.adapter = chatAdapter

        sendButton.setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                messageInput.setText("")
            }
        }
        
        // Отправка по кнопке Enter на клавиатуре
        messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val text = messageInput.text.toString().trim()
                if (text.isNotEmpty()) {
                    sendMessage(text)
                    messageInput.setText("")
                }
                true
            } else false
        }
    }

    private fun sendMessage(text: String) {
        if (isGenerating) {
            Toast.makeText(this, "Подождите, ИИ печатает...", Toast.LENGTH_SHORT).show()
            return
        }

        // TODO: Добавить сообщение пользователя в адаптер
        // chatAdapter?.addMessage(ChatMessage(text, isUser = true))
        // chatRecyclerView.scrollToPosition(chatAdapter?.itemCount?.minus(1) ?: 0)

        isGenerating = true
        sendButton.isEnabled = false
        
        // Запуск генерации (Имитация)
        generateResponse(text)
    }

    private fun generateResponse(prompt: String) {
        currentAiMessage.clear()
        
        // TODO: Добавить пустое сообщение ИИ в адаптер для стриминга
        // chatAdapter?.addMessage(ChatMessage("", isUser = false))

        // Имитация стриминга (печати по буквам)
        val fullResponse = "Это тестовый ответ от локальной модели на запрос: '$prompt'. Интеграция с GGUF будет здесь."
        
        val handler = Handler(Looper.getMainLooper())
        var index = 0

        val runnable = object : Runnable {
            override fun run() {
                if (index < fullResponse.length) {
                    currentAiMessage.append(fullResponse[index])
                    // TODO: Обновить текст последнего сообщения в адаптере
                    // chatAdapter?.updateLastMessage(currentAiMessage.toString())
                    // chatRecyclerView.scrollToPosition(chatAdapter?.itemCount?.minus(1) ?: 0)
                    index++
                    handler.postDelayed(this, 30) // Скорость печати
                } else {
                    isGenerating = false
                    sendButton.isEnabled = true
                }
            }
        }
        handler.post(runnable)
    }

    private fun setupTerminal() {
        terminalInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEND) {
                val command = terminalInput.text.toString().trim()
                if (command.isNotEmpty()) {
                    executeCommand(command)
                    terminalInput.setText("")
                }
                true
            } else false
        }
    }

    private fun executeCommand(command: String) {
        terminalOutput.append("\n$ $command\n")
        
        // TODO: Здесь будет вызов TerminalService
        when (command) {
            "help" -> terminalOutput.append("Доступные команды: help, clear, status\n")
            "clear" -> terminalOutput.text = "Terminal v1.0\n$ "
            "status" -> terminalOutput.append("Система работает. ОЗУ в норме.\n")
            else -> {
                // Имитация выполнения
                terminalOutput.append("Выполнение: $command...\nГотово.\n")
            }
        }
        terminalOutput.append("$ ")
    }
}

// --- ЗАГЛУШКИ (Тебе нужно будет вынести их в отдельные файлы) ---

class AIChatService {
    // Здесь будет инициализация llama.cpp или вызов API
}

data class ChatMessage(val text: String, val isUser: Boolean)

// Тебе нужно будет реализовать этот адаптер
class ChatAdapter(private val messages: MutableList<ChatMessage>) : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        TODO("Not yet implemented")
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        TODO("Not yet implemented")
    }
    override fun getItemCount(): Int = messages.size
}
