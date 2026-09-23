# Чек-лист реализации Notes App

## 0. Скелет проекта
- [x] Структура папок backend/ + frontend/
- [x] pom.xml (Spring Boot 3.3.5, Java 21, Web/JPA/Validation/Actuator/Liquibase/postgresql/Lombok 1.18.42)
- [x] NotesApplication.java
- [x] application.yml (конфиг только через env)
- [x] Заготовка db.changelog-master.yaml
- [x] .gitignore, .env.example, README.md

## 1. Backend — данные и миграции
- [x] Liquibase changeset: таблица `users` (id, username unique, password (plain text), created_at)
- [x] Liquibase changeset: таблица `tags` (id, name unique)
- [x] Liquibase changeset: таблица `notes` (id, title, content, owner_id FK, created_at, updated_at)
- [x] Liquibase changeset: таблица `note_tags` (note_id FK, tag_id FK, PK составной)
- [x] Подключить changeset'ы в db.changelog-master.yaml
- [x] Entity-классы: User, Note, Tag (JPA + Lombok `@Getter/@Setter/@NoArgsConstructor`)
- [x] Repository-интерфейсы: UserRepository, NoteRepository, TagRepository

## 2. Backend — максимально упрощённая идентификация (без токенов и хеширования)
- [x] DTO: RegisterRequest, LoginRequest, UserResponse {id, username}
- [x] AuthController: POST /api/auth/register (создаёт User), POST /api/auth/login (сверяет username+password строкой, возвращает UserResponse). Logout не нужен — нечего инвалидировать на сервере
- [x] AuthInterceptor: читает заголовок `X-User-Id`, находит User по id, кладёт userId в request attribute; 401 если заголовка нет или пользователь не найден
- [x] WebConfig: регистрация интерцептора на /api/notes/**, /api/tags/**; CORS из FRONTEND_ORIGIN
- [x] Базовая инфраструктура ошибок: ApiException + @RestControllerAdvice (переиспользуется в CRUD)

## 3. Backend — CRUD
- [x] DTO: NoteRequest, NoteResponse, TagRequest, TagResponse
- [x] NoteController: GET (список текущего пользователя, фильтр ?tag=), GET/{id}, POST, PUT/{id}, DELETE/{id}
- [x] TagController: GET, GET/{id}, POST, PUT/{id}, DELETE/{id}
- [x] Проверка владения заметкой (findByIdAndOwnerId → 404 если не своя, чтобы не палить существование чужих)
- [x] Глобальный обработчик ошибок (@RestControllerAdvice): 400/401/404/409 с понятным телом ответа

## 4. Backend — упаковка и проверка
- [x] `mvn clean compile` локально проходит без ошибок
- [x] Dockerfile backend (multi-stage: maven build → jre-slim run)
- [x] Локальная сборка и запуск в Docker Compose с реальным Postgres — Liquibase применил 5 changeset'ов, старт чистый
- [x] Полный прогон через curl: register → login (+ неверный пароль → 401) → notes без заголовка → 401 → CRUD notes/tags с X-User-Id → фильтр по тегу → чужая заметка → 404 → delete → 204.
  Найден и исправлен баг: список заметок падал с 500 (LazyInitializationException на note.tags при сериализации вне транзакции) — поставил `@ManyToMany(fetch = EAGER)` на Note.tags

## 5. Frontend
- [x] index.html, login.html, register.html, notes.html
- [x] css/style.css — единый простой стиль
- [x] js/config.template.js (+ js/config.js как дефолт для запуска без Docker)
- [x] js/api.js — обёртка fetch (X-User-Id из localStorage, 401 → редирект на login)
- [x] js/auth.js — логика register/login/logout (logout — просто очистка localStorage)
- [x] js/notes.js — список/создание/редактирование/удаление заметок, фильтр по тегу
- [x] Dockerfile frontend (python:3.12-alpine + http.server, без nginx)
- [x] entrypoint.sh (envsubst config.template.js → config.js по API_BASE_URL)
- [x] Ручная проверка UI в браузере — пройдена пользователем после фикса CORS preflight (см. коммит "Пропускать CORS preflight (OPTIONS) в AuthInterceptor")

## 6. Инфраструктура
- [x] docker-compose.yml (postgres, backend, frontend)
- [x] Проверка `docker compose up --build` с нуля — все три контейнера поднялись, backend healthcheck на postgres отработал
- [x] Сквозной сценарий через curl на итоговом compose-окружении (см. блок 4). Браузерный E2E — см. пункт выше в блоке 5

## 7. Документация и сдача
- [x] Отчёт.md: доменная область
- [x] Отчёт.md: стек реализации
- [x] Отчёт.md: основные сущности
- [x] Отчёт.md: разбор всех 12 факторов (9 закрыто полностью, 3 частично с обоснованием)
- [x] Финальная проверка README.md (инструкция запуска актуальна)
- [x] Lombok починен и возвращён по всему backend (причина падения на JDK 25: (1) с JDK 23+ Maven не находит annotation processor'ы в classpath неявно — нужна явная регистрация через `annotationProcessorPaths`; (2) версии Lombok < 1.18.38 физически несовместимы с внутренним API javac в JDK 24/25 — исправлено обновлением до 1.18.42). Полный CRUD-прогон через curl после возврата — без регрессий
- [ ] Упаковка проекта в архив
