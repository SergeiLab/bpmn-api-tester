# BPMN API Tester

Тестирование многошаговых бизнес-процессов с AI и ГОСТ-шлюзом.

## Быстрый старт

### Требования
- Java 17+
- Maven 3.8+

### Установка
```bash
git clone <your-repo>
cd bpmn-api-tester

# Настройте credentials
echo "BANKING_API_CLIENT_ID=team112" > .env
echo "BANKING_API_CLIENT_SECRET=TzNr0aYAz5vWT5Dib9l7FNR59NgPrmyR" >> .env

# Запуск
mvn spring-boot:run
```

Откройте http://localhost:8080

### Быстрый тест
```powershell
.\quick-test.ps1
```

## Что умеет

- ✅ Парсинг BPMN 2.0 и Sequence диаграмм
- ✅ Автоматическое выполнение цепочек API вызовов
- ✅ AI генерация тестовых данных
- ✅ ГОСТ-шлюз поддержка
- ✅ Экспорт отчётов (HTML/CSV/JSON)

## API Endpoints

- `POST /api/v1/processes/upload` - загрузить BPMN
- `POST /api/v1/processes/upload-sequence` - загрузить Sequence
- `POST /api/v1/processes/{id}/execute` - запустить тест
- `GET /api/v1/executions/{id}/export/html` - экспорт отчёта

## Пример
```bash
curl -X POST http://localhost:8080/api/v1/processes/upload \
  -F "bpmn=@example-process.bpmn" \
  -F "name=My Process"
```

## Технологии

Java 17 • Spring Boot 3.2 • Camunda BPMN • OpenAI • BouncyCastle

## Авторы

SergeiLab aka team112