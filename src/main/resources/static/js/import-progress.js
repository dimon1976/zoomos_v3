/**
 * Скрипт для отслеживания прогресса импорта через WebSocket
 * С защитой от автоматического редиректа при завершении импорта
 */

// Инициализация защиты от редиректа
window.importCompleted = false;

// Функция для добавления параметра noRedirect к URL
function addNoRedirectParam(url) {
    if (typeof url !== 'string') return url;

    if (url.includes('?')) {
        return url + '&noRedirect=true';
    } else {
        return url + '?noRedirect=true';
    }
}

// 1. Перехватываем AJAX запросы (XMLHttpRequest)
const originalXHROpen = XMLHttpRequest.prototype.open;
XMLHttpRequest.prototype.open = function(method, url, async, user, pass) {
    // Запоминаем оригинальный URL для логирования
    this._originalUrl = url;

    // Если импорт завершен, добавляем параметр noRedirect
    if (window.importCompleted && typeof url === 'string') {
        url = addNoRedirectParam(url);
        console.log("AJAX запрос изменен:", this._originalUrl, "->", url);
    }

    return originalXHROpen.call(this, method, url, async, user, pass);
};

// 2. Перехватываем запросы Fetch API
const originalFetch = window.fetch;
window.fetch = function(input, init) {
    // Если импорт завершен, модифицируем URL
    if (window.importCompleted) {
        if (typeof input === 'string') {
            input = addNoRedirectParam(input);
            console.log("Fetch запрос изменен:", input);
        } else if (input instanceof Request) {
            // Создаем новый Request с модифицированным URL
            const modifiedUrl = addNoRedirectParam(input.url);
            console.log("Fetch Request изменен:", input.url, "->", modifiedUrl);
            input = new Request(modifiedUrl, input);
        }
    }

    return originalFetch.call(window, input, init);
};

// Основной код скрипта
document.addEventListener('DOMContentLoaded', function() {
    console.log("Инициализация скрипта import-progress.js");

    // Получаем ID операции и клиента из скрытых полей
    const operationId = document.getElementById('operationId').value;
    const clientId = document.getElementById('clientId').value;

    console.log("Операция ID:", operationId);
    console.log("Клиент ID:", clientId);

    // Получаем элементы UI
    const progressBar = document.getElementById('progressBar');
    const progressText = document.getElementById('progressText');
    const connectionStatus = document.getElementById('connectionStatus');
    const errorMessage = document.getElementById('errorMessage');
    const importStatus = document.getElementById('importStatus');
    const logContainer = document.getElementById('logContainer');
    const completionActions = document.getElementById('completionActions');
    const processingTime = document.getElementById('processingTime');
    const cancelImportBtn = document.getElementById('cancelImportBtn');
    const totalRecordsEl = document.getElementById('totalRecords');

    // Переменные для отслеживания времени
    let startTime = null;
    const startTimeEl = document.querySelector('[data-start-time]');
    if (startTimeEl) {
        startTime = new Date(startTimeEl.getAttribute('data-start-time'));
    } else {
        startTime = new Date();
    }

    // Переменные для WebSocket
    let stompClient = null;
    let retryCount = 0;
    const maxRetries = 5;
    let connected = false;

    // Инициализация WebSocket соединения
    initWebSocket();

    // Обновляем таймер каждую секунду
    setInterval(updateElapsedTime, 1000);

    // Функция инициализации WebSocket
    function initWebSocket() {
        console.log("Инициализация WebSocket соединения...");

        connectionStatus.classList.remove('d-none');
        connectionStatus.innerHTML = '<i class="fas fa-spinner fa-spin me-2"></i>Подключение к серверу для отслеживания прогресса...';

        try {
            // Создаем SockJS соединение
            const socket = new SockJS('/ws');
            stompClient = Stomp.over(socket);

            // Отключаем логи STOMP клиента в продакшне
            // stompClient.debug = null;

            // Настраиваем обработчики для WebSocket
            socket.onopen = function() {
                console.log("SockJS соединение открыто");
            };

            socket.onclose = function() {
                console.log("SockJS соединение закрыто");
                handleConnectionClosed();
            };

            socket.onerror = function(error) {
                console.error("SockJS ошибка:", error);
                handleConnectionError(error);
            };

            // Подключаемся к серверу
            stompClient.connect({}, onConnected, onError);

        } catch (e) {
            console.error("Ошибка при инициализации WebSocket:", e);
            handleConnectionError(e);
        }
    }

    // Функция для обработки успешного подключения
    function onConnected() {
        console.log("WebSocket соединение установлено");
        connected = true;
        connectionStatus.classList.add('d-none');

        // Подписываемся на обновления для конкретной операции
        stompClient.subscribe(`/topic/import-progress/${operationId}`, onProgressUpdate);
        console.log(`Подписка на /topic/import-progress/${operationId} установлена`);

        // Подписываемся на общие обновления
        stompClient.subscribe('/topic/import-progress', onProgressUpdate);
        console.log("Подписка на /topic/import-progress установлена");

        // Отправляем запрос на получение текущего статуса
        stompClient.send("/app/import-progress", {}, operationId);
        console.log(`Отправлен запрос статуса для операции ${operationId}`);

        // Добавляем запись в лог
        addLogEntry('Подключение к серверу установлено. Отслеживание прогресса...', 'info');

        // Сбрасываем счетчик попыток
        retryCount = 0;
    }

    // Функция для обработки ошибки подключения
    function onError(error) {
        console.error('Ошибка WebSocket соединения:', error);
        handleConnectionError(error);
    }

    // Обработка закрытия соединения
    function handleConnectionClosed() {
        if (!connected) return;

        connected = false;
        connectionStatus.classList.remove('d-none', 'alert-warning');
        connectionStatus.classList.add('alert-info');
        connectionStatus.innerHTML = `
            <i class="fas fa-info-circle me-2"></i>
            Соединение с сервером прервано. 
            <button onclick="location.reload()" class="btn btn-outline-primary btn-sm ms-2">Переподключиться</button>
        `;
    }

    // Обработка ошибки соединения
    function handleConnectionError(error) {
        // Если количество попыток не превышено, пробуем переподключиться
        if (retryCount < maxRetries) {
            retryCount++;
            connectionStatus.classList.remove('d-none');
            connectionStatus.innerHTML = `<i class="fas fa-exclamation-triangle me-2"></i>Ошибка подключения. Повторная попытка ${retryCount}/${maxRetries}...`;

            // Пробуем переподключиться через 3 секунды
            setTimeout(initWebSocket, 3000);
        } else {
            // Если превышено количество попыток, показываем сообщение и предлагаем перезагрузить страницу
            connectionStatus.classList.remove('alert-warning');
            connectionStatus.classList.add('alert-danger');
            connectionStatus.innerHTML = `
                <i class="fas fa-exclamation-circle me-2"></i>
                Не удалось установить соединение с сервером. 
                <button onclick="location.reload()" class="btn btn-outline-light btn-sm ms-2">Перезагрузить страницу</button>
                <a href="/import/status/${operationId}" class="btn btn-outline-light btn-sm ms-2">Посмотреть статус</a>
            `;
        }
    }

    // Функция для обработки обновлений о прогрессе
    function onProgressUpdate(message) {
        console.log("Получено WebSocket-сообщение:", message);
        try {
            const data = JSON.parse(message.body);
            console.log("Данные сообщения:", data);

            // Проверяем, что обновление относится к нашей операции
            if (data.operationId && data.operationId != operationId) {
                console.log("Сообщение для другой операции, игнорируем");
                return;
            }

            // Обновляем прогресс
            if (data.progress !== undefined) {
                updateProgress(data.progress);
            }

            // Обновляем текст прогресса
            if (data.processedRecords !== undefined && data.totalRecords !== undefined) {
                updateProgressText(data.processedRecords, data.totalRecords);

                // Обновляем ожидаемое количество записей
                if (totalRecordsEl && data.totalRecords > 0) {
                    totalRecordsEl.textContent = data.totalRecords;
                }
            }

            // Проверяем статус операции
            if (data.status) {
                updateStatus(data.status);
            }

            // Проверяем, завершена ли операция
            if (data.completed) {
                handleCompletion(data.successful, data.errorMessage);
            }

            // Если есть сообщение, добавляем его в лог
            if (data.message) {
                addLogEntry(data.message, data.successful ? 'success' : data.error ? 'error' : 'info');
            }

        } catch (e) {
            console.error('Ошибка обработки сообщения:', e);
            addLogEntry('Ошибка обработки сообщения от сервера: ' + e.message, 'error');
        }
    }

    // Функция для обновления прогресс-бара
    function updateProgress(progress) {
        console.log("Обновление прогресса:", progress);
        if (progressBar) {
            progressBar.style.width = `${progress}%`;
            progressBar.textContent = `${progress}%`;
            progressBar.setAttribute('aria-valuenow', progress);
        }
    }

    // Функция для обновления текста прогресса
    function updateProgressText(processed, total) {
        console.log(`Обновление текста прогресса: ${processed}/${total}`);
        if (progressText) {
            progressText.textContent = `Обработано ${processed} из ${total} записей`;

            // Пытаемся оценить оставшееся время
            if (processed > 0 && total > 0) {
                const progress = processed / total;
                if (progress > 0 && progress < 1) {
                    const elapsed = new Date() - startTime;
                    const estimated = elapsed / progress;
                    const remaining = estimated - elapsed;

                    const minutes = Math.floor(remaining / 60000);
                    const seconds = Math.floor((remaining % 60000) / 1000);

                    progressText.textContent += ` (осталось примерно ${minutes} мин ${seconds} сек)`;
                }
            }
        }
    }

    // Функция для обновления статуса операции
    function updateStatus(status) {
        console.log("Обновление статуса:", status);
        if (importStatus) {
            // Удаляем существующие классы
            importStatus.classList.remove('bg-primary', 'bg-warning', 'bg-success', 'bg-danger');

            switch (status) {
                case 'PENDING':
                    importStatus.classList.add('bg-warning');
                    importStatus.textContent = 'Ожидание';
                    break;
                case 'PROCESSING':
                    importStatus.classList.add('bg-primary');
                    importStatus.textContent = 'Импорт в процессе';
                    break;
                case 'COMPLETED':
                    importStatus.classList.add('bg-success');
                    importStatus.textContent = 'Импорт завершен';
                    break;
                case 'FAILED':
                    importStatus.classList.add('bg-danger');
                    importStatus.textContent = 'Ошибка импорта';
                    break;
                default:
                    importStatus.classList.add('bg-secondary');
                    importStatus.textContent = 'Неизвестный статус';
            }
        }
    }

    // Функция для обработки завершения операции
    function handleCompletion(successful, errorMsg) {
        console.log("Обработка завершения операции:", successful, errorMsg);

        // ВАЖНО: Перехватываем все GET-запросы после завершения импорта
        // и предотвращаем редиректы сразу в родительском окне

        // Создаем HTMLAnchorElement для работы с URL
        const urlParser = document.createElement('a');

        // Сохраняем оригинальный метод
        const originalOpen = XMLHttpRequest.prototype.open;

        // Переопределяем метод open
        XMLHttpRequest.prototype.open = function(method, url, async, user, pass) {
            // Разбираем URL
            urlParser.href = url;
            const path = urlParser.pathname;

            // Если это запрос статуса импорта - блокируем его полностью
            if (method === 'GET' && path.includes('/import/api/status/')) {
                console.log('БЛОКИРОВКА запроса:', method, url);

                // Вместо настоящего запроса создаем фиктивный ответ
                this.abort = function() {};
                this.getAllResponseHeaders = function() { return ''; };
                this.getResponseHeader = function() { return null; };
                this.open = function() {};
                this.overrideMimeType = function() {};
                this.send = function() {
                    // Эмулируем успешный ответ
                    this.readyState = 4;
                    this.status = 200;
                    this.statusText = 'OK';
                    this.responseText = JSON.stringify({
                        status: "COMPLETED",
                        message: "Операция завершена успешно (ответ эмулирован)"
                    });

                    // Вызываем onreadystatechange, если определен
                    if (typeof this.onreadystatechange === 'function') {
                        this.onreadystatechange();
                    }
                };
                return;
            }

            // Вызываем оригинальный метод для остальных запросов
            return originalOpen.apply(this, arguments);
        };

        // Устанавливаем флаг завершения импорта
        window.importCompleted = true;

        // Предотвращаем потенциальные редиректы через таймеры
        const maxTimerId = setTimeout(function(){}, 0);
        for(let i = 0; i < maxTimerId; i++) {
            clearTimeout(i);
            clearInterval(i);
        }

        // Скрываем кнопку отмены
        if (cancelImportBtn) {
            cancelImportBtn.style.display = 'none';
        }

        // Прерываем обновление, если операция завершена
        if (stompClient !== null && connected) {
            stompClient.disconnect();
            connected = false;
        }

        // Обновляем UI в зависимости от результата
        if (successful) {
            // Показываем кнопки для завершения
            if (completionActions) {
                // Модифицируем все ссылки, чтобы предотвратить автоматический переход
                const links = completionActions.querySelectorAll('a');
                links.forEach(link => {
                    const originalHref = link.getAttribute('href');
                    if (originalHref) {
                        // Заменяем href на javascript:void(0) и сохраняем оригинальный URL
                        link.setAttribute('data-original-href', originalHref);
                        link.setAttribute('href', 'javascript:void(0)');

                        // Добавляем обработчик клика, который будет открывать страницу в новой вкладке
                        link.addEventListener('click', function(e) {
                            e.preventDefault();
                            console.log("Переход по ссылке:", originalHref);
                            window.open(originalHref, '_blank');
                        });
                    }

                    // Устанавливаем tabindex=-1 чтобы предотвратить автофокус
                    link.setAttribute('tabindex', '-1');
                });

                // Добавляем уведомление о предотвращении редиректа
                const successNotice = document.createElement('div');
                successNotice.className = 'alert alert-info mt-3 mb-3';
                successNotice.innerHTML = `
                <i class="fas fa-info-circle me-2"></i>
                <strong>Импорт успешно завершен!</strong> 
                Вы останетесь на этой странице. Используйте кнопки ниже для перехода к другим разделам.
            `;

                // Вставляем уведомление перед кнопками действий
                if (completionActions.parentNode) {
                    completionActions.parentNode.insertBefore(successNotice, completionActions);
                }

                // Показываем блок с действиями с небольшой задержкой,
                // чтобы дать время модифицировать ссылки
                setTimeout(() => {
                    completionActions.style.display = 'block';

                    // Снимаем фокус с любого активного элемента
                    if (document.activeElement) {
                        document.activeElement.blur();
                    }
                }, 100);
            }

            // Добавляем время обработки
            if (processingTime) {
                const elapsed = new Date() - startTime;
                const minutes = Math.floor(elapsed / 60000);
                const seconds = Math.floor((elapsed % 60000) / 1000);
                processingTime.textContent = `(завершено за ${minutes} мин ${seconds} сек)`;
            }

            // Добавляем финальную запись в лог
            addLogEntry('Импорт успешно завершен!', 'success');
        } else {
            // Отображаем сообщение об ошибке
            if (errorMessage) {
                errorMessage.classList.remove('d-none');
                errorMessage.innerHTML = `
                <i class="fas fa-exclamation-circle me-2"></i>
                ${errorMsg || 'Произошла ошибка при импорте файла.'}
            `;
            }

            // Добавляем финальную запись в лог
            addLogEntry(`Импорт завершился с ошибкой: ${errorMsg || 'Неизвестная ошибка'}`, 'error');
        }

        // Блокируем попытки закрыть страницу
        window.onbeforeunload = function(e) {
            // Отменяем событие
            e.preventDefault();
            // Для Chrome требуется задать возвратное значение
            e.returnValue = '';
            return '';
        };
    }

    // Функция для добавления записи в лог
    function addLogEntry(message, type = 'info') {
        console.log(`Добавление записи в лог: ${message} (${type})`);
        if (logContainer) {
            const now = new Date();
            const timeStr = now.toTimeString().split(' ')[0];

            const logEntry = document.createElement('div');
            logEntry.className = 'log-entry';
            logEntry.innerHTML = `
                <span class="log-time">${timeStr}</span>
                <span class="log-${type}">${message}</span>
            `;

            logContainer.appendChild(logEntry);
            logContainer.scrollTop = logContainer.scrollHeight;
        }
    }

    // Функция для обновления прошедшего времени
    function updateElapsedTime() {
        const elapsedTimeEl = document.getElementById('elapsedTime');
        if (elapsedTimeEl && startTime) {
            const elapsed = new Date() - startTime;
            const hours = Math.floor(elapsed / 3600000);
            const minutes = Math.floor((elapsed % 3600000) / 60000);
            const seconds = Math.floor((elapsed % 60000) / 1000);

            let timeStr = '';
            if (hours > 0) {
                timeStr += `${hours} ч `;
            }
            timeStr += `${minutes} мин ${seconds} сек`;

            elapsedTimeEl.textContent = timeStr;
        }
    }

    // Обработчик кнопки отмены импорта
    if (cancelImportBtn) {
        cancelImportBtn.addEventListener('click', function() {
            if (confirm('Вы уверены, что хотите отменить импорт? Это действие нельзя отменить.')) {
                console.log("Пользователь инициировал отмену импорта");

                // Отправляем запрос на отмену импорта
                fetch(`/import/api/cancel/${operationId}`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    }
                })
                    .then(response => response.json())
                    .then(data => {
                        console.log("Ответ сервера на запрос отмены:", data);
                        if (data.success) {
                            addLogEntry('Запрос на отмену импорта отправлен', 'warning');
                            // Отключаем кнопку отмены
                            cancelImportBtn.disabled = true;
                            cancelImportBtn.textContent = 'Отмена в процессе...';
                        } else {
                            addLogEntry(`Ошибка при отмене импорта: ${data.message}`, 'error');
                        }
                    })
                    .catch(error => {
                        console.error("Ошибка при отправке запроса отмены:", error);
                        addLogEntry('Ошибка при отправке запроса на отмену', 'error');
                    });
            }
        });
    }

    // НОВЫЙ КОД: Блокируем закрытие страницы
    window.addEventListener('beforeunload', function(event) {
        if (window.importCompleted) {
            console.log("Попытка покинуть страницу после завершения импорта");
            event.preventDefault();
            event.returnValue = 'Импорт успешно завершен. Вы уверены, что хотите покинуть страницу?';
            return 'Импорт успешно завершен. Вы уверены, что хотите покинуть страницу?';
        }
    });

    // Добавляем первую запись в лог
    addLogEntry('Инициализация отслеживания прогресса импорта...', 'info');
});