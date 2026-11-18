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
    └── AndroidManifest.xml               # + configChanges: ротация не выгружает модель

ui-overlay/                               # UI-слой приложения
├── app/
│   └── build.gradle.kts                  # Gradle-конфиг приложения (Room, KSP, abiFilters)
└── src/main/
    ├── java/com/example/llama/
    │   ├── MainActivity.kt               # Главный экран, 4 вкладки, состояние движка
    │   ├── MessageAdapter.kt             # DiffUtil-адаптер чата (пузыри, размышления, время)
    │   ├── ModelAdapter.kt               # Список сохранённых моделей (удаление)
    │   └── ModelDatabase.kt              # Room: my_models + chat_messages (история чата)
    └── res/
        ├── anim/item_fall.xml            # Анимация появления сообщений
        ├── layout/
        │   ├── activity_main.xml
        │   ├── item_message_user.xml
        │   ├── item_message_assistant.xml
        │   └── item_model.xml
        ├── drawable/                     # Фоны: badge, bubbles, card, chip, input, nav
        └── values/colors.xml             # Палитра в одном месте
```

## Сборка

Workflow ``.github/workflows/build.yml`` при каждом push в ``main``:
1. Клонирует ``PrismML-Eng/llama.cpp`` (ветка ``prism``) — оттуда берётся только нативная библиотека llama.cpp (модуль ``:lib``) и gradlew.
2. Копирует поверх конфигурацию из ``project/`` и UI из ``ui-overlay/``.
3. Собирает debug-APK (только arm64-v8a) и выкладывает артефактом ``Bonsai-Studio-New``.

Запуск сборки вручную: вкладка **Actions** → **Build Bonsai Android APK** → **Run workflow**.
