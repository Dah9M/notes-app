# Notes App

Простое приложение для заметок: Java/Spring Boot backend + PostgreSQL + vanilla JS frontend.

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
