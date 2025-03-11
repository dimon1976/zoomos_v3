// src/main/resources/static/js/import-progress.js
/**
 * Клиентский код для отслеживания прогресса импорта через WebSocket
 */
class ImportProgressTracker {
    /**
     * Конструктор для отслеживания прогресса импорта
     * @param {number} operationId - ID операции импорта
     * @param {object} options - Опции для настройки трекера
     */
    constructor(operationId, options = {}) {
        this.operationId = operationId;
        this.options = Object.assign({
            url: '/ws',
            progressBarSelector: '#progressBar',
            progressTextSelector: '#progressText',
            statusSelector: '#importStatus',
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
    }

    /**
     * Подключение к WebSocket для отслеживания прогресса
     */
    connect() {
        const socket = new SockJS(this.options.url);
        this.stompClient = Stomp.over(socket);

        // Отключаем логи STOMP
        this.stompClient.debug = () => {};

        this.stompClient.connect({}, (frame) => {
            console.log('Connected to WebSocket: ' + frame);
            this.connected = true;

            // Подписываемся на обновления прогресса для конкретной операции
            this.subscription = this.stompClient.subscribe(
                '/topic/import-progress/' + this.operationId,
                (message) => this.handleProgressUpdate(message)
            );

            if (typeof this.options.onConnect === 'function') {
                this.options.onConnect();
            }
        }, (error) => {
            console.error('Error connecting to WebSocket:', error);
            if (typeof this.options.onError === 'function') {
                this.options.onError(error);
            }
        });
    }

    /**
     * Отключение от WebSocket
     */
    disconnect() {
        if (this.subscription) {
            this.subscription.unsubscribe();
        }

        if (this.stompClient && this.connected) {
            this.stompClient.disconnect();
            this.connected = false;
            console.log('Disconnected from WebSocket');
        }
    }

    /**
     * Обработка обновления прогресса
     * @param {object} message - Сообщение с обновлением прогресса
     */
    handleProgressUpdate(message) {
        const update = JSON.parse(message.body);
        console.log('Progress update:', update);

        // Обновляем прогресс-бар
        const progressBar = document.querySelector(this.options.progressBarSelector);
        if (progressBar) {
            progressBar.style.width = update.progress + '%';
            progressBar.setAttribute('aria-valuenow', update.progress);
            progressBar.textContent = update.progress + '%';
        }

        // Обновляем текст прогресса
        const progressText = document.querySelector(this.options.progressTextSelector);
        if (progressText) {
            progressText.textContent = `Обработано ${update.processedRecords} из ${update.totalRecords} записей (${update.progress}%)`;
        }

        // Обновляем статус
        const statusElement = document.querySelector(this.options.statusSelector);
        if (statusElement) {
            if (update.completed) {
                if (update.successful) {
                    statusElement.textContent = 'Импорт успешно завершен';
                    statusElement.className = 'badge bg-success';
                } else {
                    statusElement.textContent = 'Ошибка импорта';
                    statusElement.className = 'badge bg-danger';

                    // Отображаем сообщение об ошибке, если есть
                    if (update.errorMessage) {
                        const errorElement = document.getElementById('errorMessage');
                        if (errorElement) {
                            errorElement.textContent = update.errorMessage;
                            errorElement.classList.remove('d-none');
                        }
                    }
                }
            } else {
                statusElement.textContent = 'Импорт в процессе';
                statusElement.className = 'badge bg-primary';
            }
        }

        // Вызываем пользовательский обработчик прогресса
        if (typeof this.options.onProgress === 'function') {
            this.options.onProgress(update);
        }

        // Обработка завершения импорта
        if (update.completed) {
            // Вызываем пользовательский обработчик завершения
            if (typeof this.options.onComplete === 'function') {
                this.options.onComplete(update);
            }

            // Отключаемся от WebSocket
            this.disconnect();

            // Автоматическое перенаправление после завершения, если включено
            if (update.successful && this.options.enableAutoRedirect && this.options.redirectUrl) {
                setTimeout(() => {
                    window.location.href = this.options.redirectUrl;
                }, this.options.redirectDelay);
            }
        }
    }
}

// Инициализация отслеживания прогресса при загрузке страницы
document.addEventListener('DOMContentLoaded', function() {
    const operationIdElement = document.getElementById('operationId');
    if (operationIdElement) {
        const operationId = operationIdElement.value;
        const clientId = document.getElementById('clientId')?.value;

        if (operationId) {
            const tracker = new ImportProgressTracker(operationId, {
                redirectUrl: clientId ? `/clients/${clientId}` : '/import',
                onConnect: () => {
                    console.log('Connected to import progress tracking');
                    document.getElementById('connectionStatus')?.classList.add('d-none');
                },
                onComplete: (update) => {
                    console.log('Import completed:', update);

                    // Отображаем кнопки действий после завершения
                    const actionsElement = document.getElementById('completionActions');
                    if (actionsElement) {
                        actionsElement.classList.remove('d-none');
                    }
                },
                onError: (error) => {
                    console.error('Error tracking import progress:', error);
                    document.getElementById('connectionStatus')?.classList.remove('d-none');
                    document.getElementById('connectionStatus')?.textContent = 'Ошибка подключения для отслеживания прогресса';
                }
            });

            tracker.connect();

            // Сохраняем трекер в глобальной переменной для доступа из других частей приложения
            window.importProgressTracker = tracker;
        }
    }
});