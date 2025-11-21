# BPMN API Tester

**Автоматизация тестирования многошаговых бизнес-процессов с AI**

VTB API Hackathon 2025 | Team: team112

##  Возможности

- ✅ Парсинг BPMN 2.0 и Sequence диаграмм (PlantUML)
- ✅ AI генерация тестовых данных (Ollama + fallback)
- ✅ Автоматическое выполнение цепочек API вызовов
- ✅ Передача данных между шагами процесса
- ✅ Валидация контрактов API
- ✅ ГОСТ-шлюз поддержка
- ✅ Экспорт отчётов (HTML/CSV/JSON)

##  Требования

- Java 17+
- Maven 3.8+
- (Опционально) Ollama для AI генерации

##  Быстрый старт

### 1. Настройка credentials

Создайте файл `.env` в корне проекта:
```env
BANKING_API_CLIENT_ID=team112
BANKING_API_CLIENT_SECRET=TzNr0aYAz5vWT5Dib9l7FNR59NgPrmyR
```

### 2. Запуск приложения
```powershell
# Сборка
mvn clean install -DskipTests

# Запуск
mvn spring-boot:run
```

Приложение запустится на http://localhost:8080

### 3. Быстрая проверка
```powershell
.\quick-test.ps1
```

### 4. Полное демо
```powershell
.\demo-test.ps1
```

## 📁 Структура проекта
```
bpmn-api-tester/
├── src/main/java/ru/bankingapi/bpmntester/
│   ├── config/          # Конфигурация (GOST, ENV)
│   ├── controller/      # REST API контроллеры
│   ├── domain/          # Entity и DTO
│   ├── repository/      # JPA репозитории
│   └── service/         # Бизнес-логика
│       ├── BpmnParserService.java
│       ├── SequenceDiagramParser.java
│       ├── TestOrchestrator.java
│       ├── AiTestDataGenerator.java
│       ├── ValidationService.java
│       └── ReportExportService.java
├── src/main/resources/
│   ├── application.yml
│   └── static/index.html
├── 01_bonus_payment.bpmn  # Пример BPMN процесса
├── 01_bonus_payment.puml  # Пример Sequence диаграммы
└── README.md
```

## 🔌 API Endpoints

### Управление процессами
```http
POST /api/v1/processes/upload
POST /api/v1/processes/upload-sequence
GET  /api/v1/processes
GET  /api/v1/processes/{id}
```

### Выполнение тестов
```http
POST /api/v1/processes/{id}/execute
GET  /api/v1/executions/{id}
GET  /api/v1/processes/{id}/executions
```

### Экспорт отчётов
```http
GET /api/v1/executions/{id}/export/html
GET /api/v1/executions/{id}/export/csv
GET /api/v1/executions/{id}/export/json
```

### Утилиты
```http
GET /api/v1/health
GET /api/v1/ai/status
GET /api/v1/test-data/templates
```

##  Пример использования

### 1. Загрузка BPMN процесса
```powershell
curl -X POST http://localhost:8080/api/v1/processes/upload `
  -F "bpmn=@01_bonus_payment.bpmn" `
  -F "name=Bonus Payment Process"
```

### 2. Запуск теста
```powershell
curl -X POST http://localhost:8080/api/v1/processes/1/execute `
  -H "Content-Type: application/json" `
  -d '{
    "mode": "STANDARD",
    "generateTestData": true,
    "initialContext": {}
  }'
```

### 3. Экспорт отчёта
```powershell
curl http://localhost:8080/api/v1/executions/1/export/html -o report.html
start report.html
```

##  AI Генерация данных

Приложение использует Ollama для генерации реалистичных тестовых данных.

### Установка Ollama
```powershell
# Windows: скачать с https://ollama.ai
# Linux/Mac:
curl -fsSL https://ollama.com/install.sh | sh

# Загрузить модель
ollama pull llama3.2:3b
```

Если Ollama недоступна, используется fallback на дефолтные данные.

##  ГОСТ-шлюз

Для работы с ГОСТ-шифрованием установите сертификаты:
```yaml
banking-api:
  gost:
    enabled: true
    certificate-path: ./certs/gost-cert.pem
    certificate-password: ${GOST_CERT_PASSWORD}
```

Запуск в ГОСТ режиме:
```json
{
  "mode": "GOST",
  "generateTestData": true
}
```

##  Технологии

- **Backend**: Java 17, Spring Boot 3.2
- **BPMN**: Camunda BPMN Model API
- **OpenAPI**: Swagger Parser 2.1
- **AI**: Ollama (Llama 3.2)
- **Security**: BouncyCastle (ГОСТ)
- **Database**: H2 (dev), PostgreSQL (prod)
- **Frontend**: HTML5, Vanilla JS

##  Архитектура
```
┌─────────────┐
│   Browser   │
│  (Vue/HTML) │
└──────┬──────┘
       │ REST API
┌──────▼──────────────────────────┐
│     BpmnApiTesterController     │
└──────┬──────────────────────────┘
       │
┌──────▼──────────────────────────┐
│      TestOrchestrator           │
│  (Координация выполнения)       │
└─┬─────┬─────┬─────┬─────┬──────┘
  │     │     │     │     │
  │     │     │     │     └─► ReportExportService
  │     │     │     └───────► ValidationService
  │     │     └─────────────► AiTestDataGenerator
  │     └───────────────────► OAuth2Service
  └─────────────────────────► BpmnParserService
```

## 🧪 Тестирование
```powershell
# Unit тесты
mvn test

# Integration тесты
mvn verify

# Быстрая проверка
.\quick-test.ps1

# Полное демо
.\demo-test.ps1
```

## 📖 Документация API

После запуска приложения доступна Swagger UI:
```
http://localhost:8080/swagger-ui.html
```

##  Troubleshooting

### Backend не запускается
```powershell
# Проверить Java версию
java -version  # Должна быть 17+

# Очистить Maven кеш
mvn clean install -U
```

### OAuth2 ошибки

Проверьте credentials в `.env`:
```powershell
Get-Content .env
```

### AI не работает

Это нормально - используется fallback:
```powershell
curl http://localhost:8080/api/v1/ai/status
```

## 👥 Комманда Связь

**Team team112** - VTB API Hackathon 2025

## 📄 Лицензия

MIT License
