/**
 * import-progress.js - Скрипт для отслеживания прогресса импорта
 * Путь: /static/js/import-progress.js
 */

document.addEventListener('DOMContentLoaded', function() {
    // Получаем ID операции и клиента из скрытых полей
    const operationId = document.getElementById('operationId')?.value;
    const clientId = document.getElementById('clientId')?.value;

    if (!operationId || !clientId) {
        console.error('Не найдены идентификаторы операции или клиента');
        return;
    }

    // Ищем элементы интерфейса
    const progressBar = document.getElementById('progressBar');
    const progressText = document.getElementById('progressText');
    const importStatus = document.getElementById('importStatus');
    const logContainer = document.getElementById('logContainer');
    const connectionStatus = document.getElementById('connectionStatus');
    const errorMessage = document.getElementById('errorMessage');
    const cancelImportBtn = document.getElementById('cancelImportBtn');
    const completionActions = document.getElementById('completionActions');
    const elapsedTimeElement = document.getElementById('elapsedTime');
    const processingTimeElement = document.getElementById('processingTime');
    const totalRecordsElement = document.getElementById('totalRecords');

    // Расчет прошедшего времени
    const startTimeElement = document.querySelector('[data-start-time]');
    let startTime = startTimeElement ? new Date(startTimeElement.dataset.startTime) : new Date();

    // Устанавливаем начальное время
    updateElapsedTime();

    // Обновляем время каждую секунду
    const elapsedTimeInterval = setInterval(updateElapsedTime, 1000);

    // Инициализируем WebSocket соединение для отслеживания прогресса
    const websocket = WebSocketUtils.initImportProgress({
        operationId: operationId,
        onProgress: handleProgressUpdate,
        onConnectionOpen: function() {
            if (connectionStatus) {
                connectionStatus.classList.remove('alert-warning');
                connectionStatus.classList.add('alert-success');
                connectionStatus.innerHTML = '<i class="fas fa-check-circle me-2"></i>Соединение установлено';

                // Скрываем сообщение через 3 секунды
                setTimeout(function() {
                    connectionStatus.style.display = 'none';
                }, 3000);
            }
        },
        onConnectionError: function(error) {
            if (connectionStatus) {
                connectionStatus.classList.remove('alert-warning');
                connectionStatus.classList.add('alert-danger');
                connectionStatus.innerHTML = '<i class="fas fa-exclamation-circle me-2"></i>Не удалось установить соединение с сервером. Обновите страницу.';
            }

            // Добавляем инофрмацию об ошибке в лог
            addLogEntry({
                time: new Date(),
                message: 'Не удалось установить соединение с сервером. Обновите страницу.',
                type: 'error'
            });
        },
        onConnectionClose: function() {
            // Добавляем информацию о закрытии соединения в лог
            addLogEntry({
                time: new Date(),
                message: 'Соединение с сервером разорвано.',
                type: 'warning'
            });
        }
    });

    // Обработчик кнопки отмены импорта
    if (cancelImportBtn) {
        cancelImportBtn.addEventListener('click', function() {
            if (confirm('Вы уверены, что хотите отменить импорт? Это действие нельзя отменить.')) {
                cancelImport();
            }
        });
    }

    /**
     * Обработчик обновления прогресса
     * @param {Object} update - Данные обновления
     */
    function handleProgressUpdate(update) {
        console.log('Progress update:', update);

        // Обновляем прогресс-бар
        if (progressBar && update.progress !== undefined) {
            progressBar.style.width = update.progress + '%';
            progressBar.setAttribute('aria-valuenow', update.progress);
            progressBar.textContent = update.progress + '%';
        }

        // Обновляем текст прогресса
        if (progressText) {
            if (update.processedRecords !== undefined && update.totalRecords !== undefined) {
                progressText.textContent = `Обработано ${update.processedRecords} из ${update.totalRecords} записей (${update.progress}%)`;

                // Обновляем общее количество записей, если есть соответствующий элемент
                if (totalRecordsElement && totalRecordsElement.textContent === 'Определяется...') {
                    totalRecordsElement.textContent = update.totalRecords;
                }
            }
        }

        // Обновляем статус импорта
        if (importStatus && update.status) {
            let statusText = 'Импорт в процессе';
            let statusClass = 'bg-primary';

            switch (update.status) {
                case 'PENDING':
                    statusText = 'Ожидание начала';
                    statusClass = 'bg-warning';
                    break;
                case 'PROCESSING':
                    statusText = 'Импорт в процессе';
                    statusClass = 'bg-primary';
                    break;
                case 'COMPLETED':
                    statusText = 'Импорт завершен';
                    statusClass = 'bg-success';
                    break;
                case 'FAILED':
                    statusText = 'Ошибка импорта';
                    statusClass = 'bg-danger';
                    break;
            }

            importStatus.textContent = statusText;
            importStatus.className = 'badge ' + statusClass;
        }

        // Добавляем запись в журнал
        addLogEntry({
            time: new Date(),
            message: getLogMessageFromUpdate(update),
            type: getLogTypeFromUpdate(update)
        });

        // Обрабатываем ошибку
        if (update.errorMessage && errorMessage) {
            errorMessage.textContent = update.errorMessage;
            errorMessage.classList.remove('d-none');
        }

        // Обрабатываем завершение операции
        if (update.completed) {
            // Останавливаем таймер
            clearInterval(elapsedTimeInterval);

            // Показываем действия после завершения
            if (completionActions) {
                completionActions.style.display = 'block';

                // Вычисляем и отображаем время обработки
                if (processingTimeElement) {
                    const endTime = new Date();
                    const duration = endTime - startTime;
                    processingTimeElement.textContent = `(Время обработки: ${formatDuration(duration)})`;
                }
            }

            // Скрываем кнопку отмены
            if (cancelImportBtn) {
                cancelImportBtn.style.display = 'none';
            }

            // Отключаем WebSocket соединение
            if (websocket) {
                websocket.disconnect();
            }
        }
    }

    /**
     * Добавляет запись в журнал операции
     * @param {Object} entry - Запись журнала
     */
    function addLogEntry(entry) {
        if (!logContainer) return;

        const time = entry.time || new Date();
        const timeStr = time.toTimeString().split(' ')[0];

        const logEntry = document.createElement('div');
        logEntry.className = 'log-entry';

        logEntry.innerHTML = `
            <span class="log-time">${timeStr}</span>
            <span class="log-${entry.type || 'info'}">${entry.message}</span>
        `;

        logContainer.appendChild(logEntry);

        // Прокручиваем контейнер вниз
        logContainer.scrollTop = logContainer.scrollHeight;
    }

    /**
     * Получает тип сообщения для журнала на основе данных обновления
     * @param {Object} update - Данные обновления
     * @returns {string} - Тип сообщения
     */
    function getLogTypeFromUpdate(update) {
        if (update.completed) {
            return update.successful ? 'success' : 'error';
        }

        if (update.errorMessage) {
            return 'error';
        }

        if (update.warningMessage) {
            return 'warning';
        }

        return 'info';
    }

    /**
     * Получает текст сообщения для журнала на основе данных обновления
     * @param {Object} update - Данные обновления
     * @returns {string} - Текст сообщения
     */
    function getLogMessageFromUpdate(update) {
        if (update.completed) {
            if (update.successful) {
                return `Импорт успешно завершен. Обработано записей: ${update.processedRecords}`;
            } else {
                return `Импорт завершился с ошибкой: ${update.errorMessage || 'Неизвестная ошибка'}`;
            }
        }

        if (update.errorMessage) {
            return `Ошибка: ${update.errorMessage}`;
        }

        if (update.warningMessage) {
            return `Предупреждение: ${update.warningMessage}`;
        }

        if (update.processedRecords !== undefined && update.totalRecords !== undefined) {
            return `Обработано ${update.processedRecords} из ${update.totalRecords} записей (${update.progress}%)`;
        }

        if (update.message) {
            return update.message;
        }

        return `Прогресс: ${update.progress}%`;
    }

    /**
     * Обновляет отображение прошедшего времени
     */
    function updateElapsedTime() {
        if (!elapsedTimeElement) return;

        const now = new Date();
        const elapsed = now - startTime;

        elapsedTimeElement.textContent = formatDuration(elapsed);
    }

    /**
     * Форматирует длительность в человекочитаемый формат
     * @param {number} duration - Длительность в миллисекундах
     * @returns {string} - Форматированная строка времени
     */
    function formatDuration(duration) {
        const seconds = Math.floor((duration / 1000) % 60);
        const minutes = Math.floor((duration / (1000 * 60)) % 60);
        const hours = Math.floor((duration / (1000 * 60 * 60)) % 24);

        let result = '';

        if (hours > 0) {
            result += `${hours} ч `;
        }

        return result + `${minutes} мин ${seconds} сек`;
    }

    /**
     * Отменяет импорт
     */
    function cancelImport() {
        fetch(`/import/api/cancel/${operationId}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    // Добавляем информацию об отмене в лог
                    addLogEntry({
                        time: new Date(),
                        message: 'Операция импорта отменена пользователем.',
                        type: 'warning'
                    });

                    // Перенаправляем на страницу клиента через 2 секунды
                    setTimeout(function() {
                        window.location.href = `/clients/${clientId}`;
                    }, 2000);
                } else {
                    alert(`Не удалось отменить импорт: ${data.message}`);
                }
            })
            .catch(error => {
                console.error('Error cancelling import:', error);
                alert('Произошла ошибка при отмене импорта');
            });
    }
});