/**
 * Модуль для отслеживания прогресса импорта файлов через WebSocket
 */
class ImportProgressTracker {
    /**
     * Конструктор для отслеживания прогресса импорта
     * @param {number} operationId - ID операции импорта
     * @param {object} options - Опции для настройки трекера
     */
    constructor(operationId, options = {}) {
        this.operationId = operationId;

        // Настройки по умолчанию
        this.options = Object.assign({
            url: '/ws',
            progressBarSelector: '#progressBar',
            progressTextSelector: '#progressText',
            statusSelector: '#importStatus',
            logContainerSelector: '#logContainer',
            elapsedTimeSelector: '#elapsedTime',
            startTime: new Date(),
            onConnect: null,
            onProgress: null,
            onComplete: null,
            onError: null,
            enableAutoRedirect: true,
            redirectDelay: 3000,
            redirectUrl: null
        }, options);

        this.connected = false;
        this.stompClient = null;
        this.subscription = null;
        this.timer = null;
        this.lastLogTime = null;
    }

    /**
     * Обработка события закрытия страницы - отключение от WebSocket
     */
    setupPageUnloadHandler() {
        // Обработка закрытия страницы
        window.addEventListener('beforeunload', () => {
            console.log('Страница закрывается, отключаемся от WebSocket');
            this.disconnect();
        });

        // Обработка потери видимости страницы
        document.addEventListener('visibilitychange', () => {
            if (document.visibilityState === 'hidden') {
                console.log('Страница скрыта, отключаемся от WebSocket');
                this.disconnect();
            }
        });

        // Обработка ошибок в WebSocket соединении
        this.stompClient.debug = (message) => {
            if (message.includes('Lost connection') || message.includes('error')) {
                console.warn('Ошибка WebSocket соединения:', message);
                this.disconnect();
            }
        };
    }

    /**
     * Подключение к WebSocket для отслеживания прогресса
     */
    connect() {
        console.log("Попытка подключения к WebSocket", this.options.url);
        const socket = new SockJS(this.options.url);
        this.stompClient = Stomp.over(socket);

        // Отключаем логи STOMP
        this.stompClient.debug = () => {
        };

        this.stompClient.connect({},
            this._onConnect.bind(this),
            this._onError.bind(this)
        );
    }

    /**
     * Обработчик успешного подключения
     * @param {string} frame - Информация о фрейме подключения
     * @private
     */
    /**
     * Обработчик успешного подключения
     * @param {string} frame - Информация о фрейме подключения
     * @private
     */
    _onConnect(frame) {
        console.log("WebSocket соединение установлено", frame);
        this.connected = true;

        // Подписываемся на обновления прогресса
        this.subscription = this.stompClient.subscribe(
            '/topic/import-progress/' + this.operationId,
            this._handleProgressUpdate.bind(this)
        );

        // Запускаем таймер обновления времени
        this._startTimer();

        // Настраиваем обработчик закрытия страницы
        this.setupPageUnloadHandler();

        // Вызываем пользовательский обработчик подключения
        if (typeof this.options.onConnect === 'function') {
            this.options.onConnect();
        }
    }

    /**
     * Обработчик ошибки подключения
     * @param {object} error - Объект ошибки
     * @private
     */
    _onError(error) {
        console.error('Ошибка подключения к WebSocket:', error);
        if (typeof this.options.onError === 'function') {
            this.options.onError(error);
        }
    }

    /**
     * Отключение от WebSocket
     */
    disconnect() {
        try {
            if (this.subscription) {
                this.subscription.unsubscribe();
                this.subscription = null;
            }

            if (this.stompClient && this.connected) {
                this.stompClient.disconnect(() => {
                    console.log('Успешно отключено от WebSocket');
                }, {});
                this.connected = false;
            }

            // Останавливаем таймер
            this._stopTimer();

            console.log('Отключено от WebSocket');
        } catch (e) {
            console.error('Ошибка при отключении от WebSocket:', e);
        }
    }

    /**
     * Запускает таймер обновления прошедшего времени
     * @private
     */
    _startTimer() {
        const elapsedTimeElement = document.querySelector(this.options.elapsedTimeSelector);
        if (!elapsedTimeElement) return;

        this.timer = setInterval(() => {
            const now = new Date();
            const elapsed = now - this.options.startTime;
            elapsedTimeElement.textContent = this._formatElapsedTime(elapsed);
        }, 1000);
    }

    /**
     * Форматирует время в удобочитаемый формат
     * @param {number} elapsed - Прошедшее время в миллисекундах
     * @returns {string} Отформатированное время
     * @private
     */
    _formatElapsedTime(elapsed) {
        const hours = Math.floor(elapsed / 3600000);
        const minutes = Math.floor((elapsed % 3600000) / 60000);
        const seconds = Math.floor((elapsed % 60000) / 1000);

        let timeString = '';
        if (hours > 0) {
            timeString += hours + ' ч ';
        }
        timeString += minutes + ' мин ' + seconds + ' сек';

        return timeString;
    }

    /**
     * Останавливает таймер обновления времени
     * @private
     */
    _stopTimer() {
        if (this.timer) {
            clearInterval(this.timer);
            this.timer = null;
        }
    }

    /**
     * Обработка обновления прогресса
     * @param {object} message - Сообщение с обновлением прогресса
     * @private
     */
    _handleProgressUpdate(message) {
        const update = JSON.parse(message.body);
        console.log('Обновление прогресса:', update);

        this._updateProgressBar(update);
        this._updateProgressText(update);
        this._updateStatus(update);
        this._addLogEntry(update);

        // Вызываем пользовательский обработчик прогресса
        if (typeof this.options.onProgress === 'function') {
            this.options.onProgress(update);
        }

        // Обработка завершения импорта
        if (update.completed) {
            this._handleCompletedImport(update);
        }
    }

    /**
     * Обновляет прогресс-бар
     * @param {object} update - Данные обновления
     * @private
     */
    _updateProgressBar(update) {
        const progressBar = document.querySelector(this.options.progressBarSelector);
        if (progressBar && update.progress !== undefined) {
            progressBar.style.width = update.progress + '%';
            progressBar.setAttribute('aria-valuenow', update.progress);
            progressBar.textContent = update.progress + '%';
        }
    }

    /**
     * Обновляет текстовое описание прогресса
     * @param {object} update - Данные обновления
     * @private
     */
    _updateProgressText(update) {
        const progressText = document.querySelector(this.options.progressTextSelector);
        if (progressText && update.processedRecords !== undefined) {
            progressText.textContent = `Обработано ${update.processedRecords} из ${update.totalRecords} записей (${update.progress}%)`;
        }
    }

    /**
     * Обновляет индикатор статуса
     * @param {object} update - Данные обновления
     * @private
     */
    _updateStatus(update) {
        const statusElement = document.querySelector(this.options.statusSelector);
        if (statusElement) {
            if (update.completed) {
                if (update.successful) {
                    statusElement.textContent = 'Завершено';
                    statusElement.className = 'badge bg-success';
                } else {
                    statusElement.textContent = 'Ошибка';
                    statusElement.className = 'badge bg-danger';
                    this._showErrorMessage(update.errorMessage);
                }
            } else {
                statusElement.textContent = 'Импорт в процессе';
                statusElement.className = 'badge bg-primary';
            }
        }
    }

    /**
     * Отображает сообщение об ошибке
     * @param {string} errorMessage - Текст сообщения
     * @private
     */
    _showErrorMessage(errorMessage) {
        if (!errorMessage) return;

        const errorElement = document.getElementById('errorMessage');
        if (errorElement) {
            errorElement.textContent = errorMessage;
            errorElement.classList.remove('d-none');
        }
    }

    /**
     * Добавляет запись в журнал операции
     * @param {object} update - Данные обновления
     * @private
     */
    _addLogEntry(update) {
        const logContainer = document.querySelector(this.options.logContainerSelector);
        if (!logContainer) return;

        const now = new Date();

        // Контроль частоты записей (не чаще 1 раза в секунду)
        if (this.lastLogTime && now - this.lastLogTime < 1000) {
            return;
        }

        this.lastLogTime = now;
        const timeStr = now.toTimeString().split(' ')[0];
        const logEntry = document.createElement('div');
        logEntry.className = 'log-entry';

        // Определяем класс и сообщение
        const {logClass, logMessage} = this._getLogClassAndMessage(update);

        logEntry.innerHTML = `
            <span class="log-time">${timeStr}</span>
            <span class="${logClass}">${logMessage}</span>
        `;

        // Добавляем запись и прокручиваем вниз
        logContainer.appendChild(logEntry);
        logContainer.scrollTop = logContainer.scrollHeight;
    }

    /**
     * Определяет класс и сообщение для записи в журнал
     * @param {object} update - Данные обновления
     * @returns {object} Объект с классом CSS и текстом сообщения
     * @private
     */
    _getLogClassAndMessage(update) {
        let logClass = 'log-info';
        let logMessage = '';

        if (update.completed) {
            if (update.successful) {
                logClass = 'log-success';
                logMessage = `Импорт успешно завершен. Обработано записей: ${update.processedRecords}`;
            } else {
                logClass = 'log-error';
                logMessage = `Импорт завершился с ошибкой: ${update.errorMessage || 'Неизвестная ошибка'}`;
            }
        } else {
            logMessage = `Обработано ${update.processedRecords} из ${update.totalRecords} записей (${update.progress}%)`;
        }

        return {logClass, logMessage};
    }

    /**
     * Обработка завершения импорта
     * @param {object} update - Данные обновления
     * @private
     */
    _handleCompletedImport(update) {
        // Показываем кнопки действий
        this._showCompletionActions();

        // Останавливаем таймер
        this._stopTimer();

        // Вызываем пользовательский обработчик завершения
        if (typeof this.options.onComplete === 'function') {
            this.options.onComplete(update);
        }

        // Отключаемся от WebSocket
        this.disconnect();

        // Автоматическое перенаправление после успешного завершения
        if (update.successful && this.options.enableAutoRedirect && this.options.redirectUrl) {
            setTimeout(() => {
                window.location.href = this.options.redirectUrl;
            }, this.options.redirectDelay);
        }
    }

    /**
     * Показывает элементы для действий после завершения импорта
     * @private
     */
    _showCompletionActions() {
        const completionActions = document.getElementById('completionActions');
        if (completionActions) {
            completionActions.style.display = 'block';
            completionActions.classList.remove('d-none');
        }
    }
}

/**
 * Инициализация отслеживания прогресса при загрузке страницы
 */
document.addEventListener('DOMContentLoaded', function () {

    const operationIdElement = document.getElementById('operationId');
    if (operationIdElement) {
        const operationId = operationIdElement.value;
        const clientId = document.getElementById('clientId')?.value;

        if (operationId) {
            // Получаем время начала операции
            let startTimeStr = document.querySelector('[data-start-time]')?.dataset?.startTime;
            let startTime = startTimeStr ? new Date(startTimeStr) : new Date();

            // Создаем трекер прогресса
            const tracker = new ImportProgressTracker(operationId, {
                redirectUrl: clientId ? `/clients/${clientId}` : '/import',
                startTime: startTime,
                onConnect: () => {
                    console.log('Подключено к отслеживанию прогресса импорта');
                    document.getElementById('connectionStatus')?.classList.add('d-none');
                },
                onComplete: (update) => {
                    console.log('Импорт завершен:', update);
                    updateCompletionInfo();
                },
                onError: (error) => {
                    console.error('Ошибка отслеживания прогресса импорта:', error);
                    showConnectionError();

                    // Инициализируем запасной метод для получения статуса
                    initStatusPolling(operationId);
                }
            });

            // Подключаемся к WebSocket
            tracker.connect();

            // Сохраняем трекер в глобальной переменной
            window.importProgressTracker = tracker;

            // Инициализируем обработчик кнопки отмены импорта
            initCancelButton(operationId);

            // Инициализируем запасной метод даже если WebSocket работает
            initStatusPolling(operationId);
        }
    }
});

// Добавим функцию для периодической проверки статуса импорта через REST API
function initStatusPolling(operationId) {
    // Устанавливаем интервал проверки (каждые 5 секунд)
    const interval = setInterval(() => {
        if (!window.importProgressTracker || !window.importProgressTracker.connected) {
            fetch(`/import/api/status/${operationId}`)
                .then(response => response.json())
                .then(data => {
                    // Обновляем интерфейс на основе полученных данных
                    updateProgressUI(data);

                    // Если импорт завершен, останавливаем опрос
                    if (data.status === 'COMPLETED' || data.status === 'FAILED') {
                        clearInterval(interval);
                    }
                })
                .catch(error => {
                    console.error('Ошибка при получении статуса импорта:', error);
                });
        }
    }, 5000);

    // Сохраняем ID интервала для возможной очистки
    window.statusPollingInterval = interval;
}

// Функция для обновления UI на основе данных о статусе
function updateProgressUI(data) {
    const progressBar = document.querySelector('#progressBar');
    const progressText = document.querySelector('#progressText');
    const statusElement = document.querySelector('#importStatus');

    if (progressBar && data.processingProgress !== undefined) {
        progressBar.style.width = data.processingProgress + '%';
        progressBar.setAttribute('aria-valuenow', data.processingProgress);
        progressBar.textContent = data.processingProgress + '%';
    }

    if (progressText && data.processedRecords !== undefined) {
        progressText.textContent = `Обработано ${data.processedRecords} из ${data.totalRecords} записей (${data.processingProgress}%)`;
    }

    if (statusElement) {
        if (data.status === 'COMPLETED') {
            statusElement.textContent = 'Завершено';
            statusElement.className = 'badge bg-success';
        } else if (data.status === 'FAILED') {
            statusElement.textContent = 'Ошибка';
            statusElement.className = 'badge bg-danger';
        } else {
            statusElement.textContent = 'Импорт в процессе';
            statusElement.className = 'badge bg-primary';
        }
    }
}

// Установим глобальный обработчик ошибок WebSocket
window.onerror = function(message, source, lineno, colno, error) {
    if (message.includes('WebSocket') || (error && error.message && error.message.includes('WebSocket'))) {
        console.error('Ошибка WebSocket:', message);

        // Если есть активный трекер, отключаем его
        if (window.importProgressTracker && window.importProgressTracker.connected) {
            window.importProgressTracker.disconnect();
        }

        return true; // Предотвращаем всплытие ошибки
    }
    return false;
};

/**
 * Инициализирует обработчик кнопки отмены импорта
 * @param {number} operationId - ID операции импорта
 */
function initCancelButton(operationId) {
    const cancelImportBtn = document.getElementById('cancelImportBtn');
    if (cancelImportBtn) {
        cancelImportBtn.addEventListener('click', function () {
            if (confirm('Вы уверены, что хотите отменить импорт? Это действие нельзя отменить.')) {
                cancelImport(operationId);
            }
        });
    }
}

/**
 * Отправляет запрос на отмену импорта
 * @param {number} operationId - ID операции импорта
 */
function cancelImport(operationId) {
    fetch(`/import/api/cancel/${operationId}`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        }
    })
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                // Операция успешно отменена, перезагружаем страницу
                window.location.reload();
            } else {
                alert(`Не удалось отменить импорт: ${data.message}`);
            }
        })
        .catch(error => {
            console.error('Ошибка при отмене импорта:', error);
            alert('Произошла ошибка при отмене импорта');
        });
}

/**
 * Обновляет информацию о завершении импорта
 */
function updateCompletionInfo() {
    // Отображаем кнопки действий после завершения
    const actionsElement = document.getElementById('completionActions');
    if (actionsElement) {
        actionsElement.classList.remove('d-none');
    }

    // Обновляем информацию о времени завершения
    const processingTimeElement = document.getElementById('processingTime');
    if (processingTimeElement) {
        const completionTime = new Date().toLocaleTimeString();
        processingTimeElement.textContent = completionTime;
    }
}

/**
 * Показывает ошибку подключения
 */
function showConnectionError() {
    const connectionStatus = document.getElementById('connectionStatus');
    if (connectionStatus) {
        connectionStatus.classList.remove('d-none');
        connectionStatus.textContent = 'Ошибка подключения для отслеживания прогресса';
        connectionStatus.classList.remove('alert-warning');
        connectionStatus.classList.add('alert-danger');
    }
}