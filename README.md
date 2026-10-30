# Notes App

Простое приложение для заметок: Java/Spring Boot backend + PostgreSQL + vanilla JS frontend.

Метрики приложения: [endpoint, бизнес-счётчики и настройки для развёртывания](backend/METRICS.md).

## Стек

- Backend: Java 21, Spring Boot 3, Spring Data JPA, Liquibase, упрощённая токен-аутентификация (без Spring Security)
- Frontend: HTML/CSS/vanilla JS, без сборки, обращается к backend напрямую (fetch + CORS)
- БД: PostgreSQL
- Инфраструктура: Docker Compose (postgres + backend + frontend)

## Структура

```
backend/    — Spring Boot REST API
frontend/   — статические HTML/CSS/JS
docker-compose.yml
.env.example
```

## Запуск

```bash
cp .env.example .env
docker compose up --build
```

## Логирование

Backend и frontend пишут JSON по общей схеме в stdout: одна строка на событие, включая stack trace
в поле ошибки. Формат backend задаёт `JsonLogFormatter`, включая логи Spring и библиотек.

Обязательные поля каждой записи:

| Поле | Значение |
| --- | --- |
| `timestamp` | UTC, ISO 8601 с ровно тремя цифрами миллисекунд: `2026-10-16T16:00:00.123Z` |
| `level`, `severity_number` | TRACE=1, DEBUG=5, INFO=9, WARN=13, ERROR=17 (диапазоны OpenTelemetry) |
| `message` | Описание события |
| `service.name`, `service.version` | Сервис и версия (`APP_VERSION`; backend локально берёт версию из pom.xml, frontend — `dev`) |
| `deployment.environment.name` | `DEPLOYMENT_ENVIRONMENT`, по умолчанию `local` |
| `host.name` | HOSTNAME или системное имя хоста |
| `k8s.pod.name` | Только в Kubernetes: K8S_POD_NAME берётся из Downward API |

Поля, которые добавляются по контексту:

- `request_id` и `trace_id` — в событиях обработки HTTP-запроса, включая ошибки.
  На старте приложения эти поля отсутствуют.
- `duration_ms` — в итогах обработки запроса.
- `error.type`, `error.message`, `error.stack` — только при наличии исключения.
  Ошибка HTTP 404 или 503 сама по себе не создаёт выдуманный stack trace.

Сервисы принимают W3C `traceparent` версии 00: сохраняют корректный trace ID;
при отсутствующем/некорректном заголовке создают новый. В ответе возвращаются
`traceparent` и `X-Request-Id`; backend открывает их через CORS. Корреляция
не включает экспорт spans в Jaeger/Tempo и автоматическую инструментализацию
браузера: для общего trace вызывающая сторона должна передавать `traceparent`.
`request_id` генерируется сервисом независимо для каждого HTTP-запроса.

Compose передаёт `APP_VERSION` и `DEPLOYMENT_ENVIRONMENT` обоим сервисам.
В Kubernetes версия задана рядом с тегом образа, окружение — `minikube`,
имя pod подставляет Kubernetes. При релизе обновляйте тег образа и APP_VERSION вместе.

Уровни backend:

- **ERROR** — непредвиденные исключения со stack trace и завершение HTTP-запросов с 5xx.
- **INFO** — регистрация и вход (включая отказы), изменения заметок/тегов,
  итог HTTP-запроса с методом, шаблоном маршрута, статусом, результатом и длительностью.
- **DEBUG** — количество результатов выборок, отсутствие сущностей, конфликты,
  ошибки валидации (имена полей без значений), автоматическое создание тегов;
  успешные health-check запросы.
- **TRACE** — начало запроса, шаги проверки пользователя, поиск сущностей и разрешение тегов.

`event.action` — стабильное имя события (`note.created`, `api.failed`,
`http.request.completed` и т.д.). `request_id` связывает backend-события одного
запроса; сервер генерирует его сам и возвращает в `X-Request-Id` (доступен через CORS).
`userId` — проверенный пользователь, `noteId`/`tagId` — объект операции.
`duration_ms` измеряется в **миллисекундах** (число с дробной частью), `http.route` содержит шаблон
вроде `/api/notes/{id}`, а не исходный URL. HTTP-запрос на статику и запрос к API
имеют независимые request_id.

Прикладные события не записывают пароли, логины, заголовки, тела запросов,
текст/заголовки заметок, имена тегов и query-параметры. ERROR сохраняет исходное
исключение для диагностики; его сообщение формирует в том числе драйвер БД.
Ошибки 4xx не считаются серверными сбоями; их статус виден в INFO-записи запроса.
Ошибки 5xx дают запись причины и итоговую запись запроса; stack trace выводится
только в записи причины.

По умолчанию `LOG_LEVEL_ROOT=INFO` и `LOG_LEVEL_APP=INFO`.
Для подробной диагностики укажите в `.env` `LOG_LEVEL_APP=DEBUG` или `TRACE`
и пересоздайте backend: `docker compose up -d --force-recreate backend`.
В Kubernetes уровни заданы в `k8s/backend/backend-configmap.yaml`; после изменения
ConfigMap перезапустите Deployment. TRACE рекомендуется включать на время диагностики.
Не включайте глобальный TRACE или логирование SQL-параметров для работы с приватными данными.

Frontend-сервер пишет итоговые HTTP-события (INFO/ERROR), запуск и остановку;
исходные URL и сообщения стандартного HTTP-сервера не выводятся.
Просмотр: `docker compose logs -f backend frontend` или
`kubectl -n notes logs -l app=backend --all-containers=true --prefix=false`.
