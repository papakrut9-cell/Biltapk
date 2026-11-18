# Biltapk — Bonsai Studio UI

Кастомный интерфейс (чат, холст, терминал, настройки) для Android-приложения с локальным ИИ на базе llama.cpp (PrismML).

## Структура

```
ui-overlay/
├── app/
│   └── build.gradle.kts                  # Gradle-конфиг приложения
└── src/main/
    ├── java/com/example/llama/
    │   ├── MainActivity.kt               # Главный экран, 4 вкладки
    │   ├── MessageAdapter.kt             # Адаптер чата (пузыри, размышления)
    │   └── ModelDatabase.kt              # Room-база локальных GGUF-моделей
    └── res/
        ├── layout/
        │   ├── activity_main.xml
        │   ├── item_message_user.xml
        │   └── item_message_assistant.xml
        └── drawable/
            ├── bg_badge.xml
            ├── bg_bubble_ai.xml
            ├── bg_bubble_user.xml
            ├── bg_card.xml
            ├── bg_chip.xml
            ├── bg_input.xml
            └── bg_nav_selected.xml
```

## Сборка

Workflow ```.github/workflows/build.yml``` клонирует ```PrismML-Eng/llama.cpp``` (ветка ```prism```), копирует файлы из ```ui-overlay/``` в Android-проект и собирает debug-APK (arm64-v8a). Готовый APK появляется в артефактах сборки (```Bonsai-Studio-New```).

Запуск сборки: вкладка **Actions** → **Build Bonsai Android APK** → **Run workflow**.
