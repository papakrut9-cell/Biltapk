# Biltapk — Bonsai Studio UI

Кастомный интерфейс (чат, холст, терминал, настройки) для Android-приложения с локальным ИИ на базе llama.cpp (PrismML).

## Структура

```
project/                                  # Конфигурация проекта (наша, не из PrismML)
├── settings.gradle.kts                   # Модули :app и :lib
├── build.gradle.kts                      # Корневой gradle-файл
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml                # Каталог версий (AGP, Kotlin, AndroidX...)
│   └── wrapper/gradle-wrapper.properties # Версия Gradle (8.14.3)
└── app/src/main/
    └── AndroidManifest.xml

ui-overlay/                               # UI-слой приложения
├── app/
│   └── build.gradle.kts                  # Gradle-конфиг приложения (Room, KSP)
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

Workflow ```.github/workflows/build.yml``` при каждом push в ```main```:
1. Клонирует ```PrismML-Eng/llama.cpp``` (ветка ```prism```) — оттуда берётся только нативная библиотека llama.cpp (модуль ```:lib```) и gradle-wrapper.
2. Копирует поверх конфигурацию из ```project/``` и UI из ```ui-overlay/```.
3. Собирает debug-APK (arm64-v8a) и выкладывает артефактом ```Bonsai-Studio-New```.

Запуск сборки вручную: вкладка **Actions** → **Build Bonsai Android APK** → **Run workflow**.
