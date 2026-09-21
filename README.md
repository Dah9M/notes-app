# Notes App

Простое приложение для заметок: Java/Spring Boot backend + PostgreSQL + vanilla JS frontend.
Первое домашнее задание курса SRE — база для последующих ДЗ (логи, метрики, деплой, балансировка трафика).

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
CHECKLIST.md — план реализации
Отчёт.md      — появится после реализации
```

## Запуск (после реализации)

```bash
cp .env.example .env
docker compose up --build
```

Backend: http://localhost:8080
Frontend: http://localhost:8081

## Статус

См. [CHECKLIST.md](./CHECKLIST.md).
