// Функции для работы с экспортом данных

document.addEventListener('DOMContentLoaded', function() {
    // Инициализация экспорта
    initExport();

    // Инициализация отслеживания прогресса экспорта
    initExportProgressTracking();

    // Обработчик для кнопки отмены экспорта
    const cancelExportBtn = document.getElementById('cancelExportBtn');
    if (cancelExportBtn) {
        cancelExportBtn.addEventListener('click', function() {
            if (confirm('Вы уверены, что хотите отменить экспорт? Это действие нельзя отменить.')) {
                cancelExport();
            }
        });
    }
});

// Функция для инициализации экспорта
function initExport() {
    // Обработчики для стандартного экспорта
    const entityTypeSelect = document.getElementById('entityType');
    const addFilterBtn = document.getElementById('addFilterBtn');
    const exportBtn = document.getElementById('exportBtn');
    const asyncExportBtn = document.getElementById('asyncExportBtn');

    if (entityTypeSelect) {
        entityTypeSelect.addEventListener('change', loadEntityFields);
    }

    if (addFilterBtn) {
        addFilterBtn.addEventListener('click', addFilterRow);
    }

    if (exportBtn) {
        exportBtn.addEventListener('click', handleExport);
    }

    if (asyncExportBtn) {
        asyncExportBtn.addEventListener('click', handleAsyncExport);
    }

    // Обработчики для настраиваемого экспорта
    const customEntityTypeSelect = document.getElementById('customEntityType');
    const selectAllFieldsCheckbox = document.getElementById('selectAllFields');
    const customExportBtn = document.getElementById('customExportBtn');
    const saveTemplateBtn = document.getElementById('saveTemplateBtn');

    if (customEntityTypeSelect) {
        customEntityTypeSelect.addEventListener('change', loadFieldsForCustomExport);
    }

    if (selectAllFieldsCheckbox) {
        selectAllFieldsCheckbox.addEventListener('change', toggleAllFields);
    }

    if (customExportBtn) {
        customExportBtn.addEventListener('click', handleCustomExport);
    }

    if (saveTemplateBtn) {
        saveTemplateBtn.addEventListener('click', showSaveTemplateModal);
    }

    // Добавляем начальный фильтр, если контейнер фильтров пуст
    const filtersContainer = document.getElementById('filters');
    if (filtersContainer && filtersContainer.children.length === 0) {
        addFilterRow();
    }
}

// Функция для загрузки полей сущности
function loadEntityFields() {
    const entityType = document.getElementById('entityType').value;
    if (!entityType) return;

    // Очищаем фильтры
    const filtersContainer = document.getElementById('filters');
    if (filtersContainer) {
        filtersContainer.innerHTML = '';
    }

    // Запрашиваем поля для выбранного типа сущности
    fetch(`/api/metadata/fields/${entityType}`)
        .then(response => response.json())
        .then(data => {
            // Создаем datalist для полей
            createFieldsDatalist(data);

            // Добавляем начальный фильтр
            addFilterRow();
        })
        .catch(error => {
            console.error('Ошибка при загрузке полей:', error);
            addFilterRow();
        });
}

// Создание списка полей для полей фильтров
function createFieldsDatalist(fields) {
    let datalist = document.getElementById('fieldsList');
    if (datalist) {
        datalist.innerHTML = '';
    } else {
        datalist = document.createElement('datalist');
        datalist.id = 'fieldsList';
        document.body.appendChild(datalist);
    }

    // Добавляем опции
    fields.forEach(field => {
        const option = document.createElement('option');
        option.value = field.name;
        option.textContent = field.displayName || field.name;
        datalist.appendChild(option);
    });
}

// Добавление строки фильтра
function addFilterRow() {
    const filtersContainer = document.getElementById('filters');
    if (!filtersContainer) return;

    const filterRow = document.createElement('div');
    filterRow.className = 'filter-row row mb-2 align-items-center';

    const fieldsDatalistId = 'fieldsList';

    filterRow.innerHTML = `
        <div class="col-4">
            <input type="text" class="form-control field-name" placeholder="Поле" 
                   list="${fieldsDatalistId}" required>
        </div>
        <div class="col-3">
            <select class="form-control operator">
                <option value="eq">равно (=)</option>
                <option value="neq">не равно (!=)</option>
                <option value="gt">больше (>)</option>
                <option value="lt">меньше (<)</option>
                <option value="gte">больше или равно (>=)</option>
                <option value="lte">меньше или равно (<=)</option>
                <option value="like">содержит</option>
                <option value="in">в списке</option>
            </select>
        </div>
        <div class="col-4">
            <input type="text" class="form-control filter-value" placeholder="Значение" required>
        </div>
        <div class="col-1">
            <button type="button" class="btn btn-outline-danger btn-sm remove-filter">
                <i class="fas fa-trash"></i>
            </button>
        </div>
    `;

    // Обработчик удаления фильтра
    const removeBtn = filterRow.querySelector('.remove-filter');
    if (removeBtn) {
        removeBtn.addEventListener('click', function() {
            filterRow.remove();
        });
    }

    // Обработчики изменения поля и оператора
    const fieldInput = filterRow.querySelector('.field-name');
    const operatorSelect = filterRow.querySelector('.operator');

    if (fieldInput && operatorSelect) {
        fieldInput.addEventListener('change', function() {
            updateFilterParamName(filterRow);
        });

        operatorSelect.addEventListener('change', function() {
            updateFilterParamName(filterRow);
        });
    }

    // Добавляем строку в контейнер
    filtersContainer.appendChild(filterRow);
}

// Обновление имени параметра фильтра
function updateFilterParamName(filterRow) {
    const fieldInput = filterRow.querySelector('.field-name');
    const operatorSelect = filterRow.querySelector('.operator');
    const valueInput = filterRow.querySelector('.filter-value');

    if (fieldInput && operatorSelect && valueInput) {
        const field = fieldInput.value;
        const operator = operatorSelect.value;

        if (field && operator) {
            // Генерируем имя параметра
            valueInput.setAttribute('data-param-name', `${field}__${operator}`);
        }
    }
}

// Обработчик стандартного экспорта
function handleExport() {
    if (!validateExportForm()) return;

    // Формируем URL для экспорта
    const url = buildExportUrl();

    // Переходим по URL для скачивания файла
    window.location.href = url;
}

// Валидация формы экспорта
function validateExportForm() {
    const entityTypeSelect = document.getElementById('entityType');

    // Проверяем выбор типа сущности
    if (!entityTypeSelect.value) {
        alert('Пожалуйста, выберите тип данных');
        entityTypeSelect.focus();
        return false;
    }

    // Проверяем фильтры
    const filterRows = document.querySelectorAll('#filters .filter-row');
    let valid = true;

    filterRows.forEach(row => {
        const fieldInput = row.querySelector('.field-name');
        const valueInput = row.querySelector('.filter-value');

        if (fieldInput.value && !valueInput.value) {
            alert('Пожалуйста, заполните значение для фильтра: ' + fieldInput.value);
            valueInput.focus();
            valid = false;
            return;
        }
    });

    return valid;
}

// Построение URL для экспорта
function buildExportUrl() {
    const clientId = document.querySelector('input[name="clientId"]').value;
    const entityType = document.getElementById('entityType').value;
    const format = document.getElementById('fileFormat').value;

    const baseUrl = `/export/entity/${entityType}?clientId=${clientId}&format=${format}`;
    const filterParams = collectFilterParams();

    return baseUrl + filterParams;
}

// Сбор параметров фильтров
function collectFilterParams() {
    let params = '';

    const filterRows = document.querySelectorAll('#filters .filter-row');
    filterRows.forEach(row => {
        const fieldInput = row.querySelector('.field-name');
        const operatorSelect = row.querySelector('.operator');
        const valueInput = row.querySelector('.filter-value');

        if (fieldInput.value && valueInput.value) {
            const field = fieldInput.value;
            const operator = operatorSelect.value;
            const value = valueInput.value;

            params += `&${field}__${operator}=${encodeURIComponent(value)}`;
        }
    });

    return params;
}

// Обработчик асинхронного экспорта
function handleAsyncExport() {
    if (!validateExportForm()) return;

    // Формируем URL для асинхронного экспорта
    const clientId = document.querySelector('input[name="clientId"]').value;
    const entityType = document.getElementById('entityType').value;
    const format = document.getElementById('fileFormat').value;

    const baseUrl = `/export/api/async/${entityType}?clientId=${clientId}&format=${format}`;
    const filterParams = collectFilterParams();

    const url = baseUrl + filterParams;

    // Отправляем запрос
    fetch(url, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        }
    })
        .then(response => response.json())
        .then(data => {
            if (data.status === 'started') {
                // Перезагружаем страницу, чтобы показать прогресс экспорта
                window.location.reload();
            } else {
                alert(data.message || 'Ошибка при запуске экспорта');
            }
        })
        .catch(error => {
            console.error('Ошибка при запуске асинхронного экспорта:', error);
            alert('Ошибка при запуске экспорта');
        });
}

// Загрузка полей для настраиваемого экспорта
function loadFieldsForCustomExport() {
    const entityType = document.getElementById('customEntityType').value;
    if (!entityType) return;

    // Очищаем список полей
    const fieldsListContainer = document.getElementById('fieldsList');
    if (fieldsListContainer) {
        // Показываем индикатор загрузки
        fieldsListContainer.innerHTML = `
            <div class="col-12 text-center py-3">
                <div class="spinner-border text-primary" role="status">
                    <span class="visually-hidden">Загрузка...</span>
                </div>
                <p class="mt-2">Загрузка доступных полей...</p>
            </div>
        `;
    }

    // Запрашиваем поля для выбранного типа сущности
    fetch(`/api/metadata/fields/${entityType}`)
        .then(response => {
            if (!response.ok) {
                throw new Error(`HTTP error! Status: ${response.status}`);
            }
            return response.json();
        })
        .then(data => {
            // Отображаем поля
            displayEntityFields(data);
        })
        .catch(error => {
            console.error('Ошибка при загрузке полей:', error);

            if (fieldsListContainer) {
                fieldsListContainer.innerHTML = `
                    <div class="col-12">
                        <div class="alert alert-danger">
                            <i class="bi bi-exclamation-triangle-fill me-2"></i>
                            Ошибка при загрузке полей: ${error.message}
                        </div>
                        <p>Пожалуйста, попробуйте выбрать другой тип данных или перезагрузите страницу.</p>
                    </div>
                `;
            }
        });
}

// Отображение полей сущности
function displayEntityFields(fields) {
    const fieldsListContainer = document.getElementById('fieldsList');
    if (!fieldsListContainer) return;

    // Очищаем контейнер
    fieldsListContainer.innerHTML = '';

    // Если полей нет, показываем сообщение
    if (!fields || fields.length === 0) {
        fieldsListContainer.innerHTML = `
            <div class="col-12">
                <div class="alert alert-info">
                    Для данного типа сущности не найдено доступных полей
                </div>
            </div>
        `;
        return;
    }

    // Добавляем поля
    fields.forEach(field => {
        // Проверяем формат поля
        const fieldName = typeof field === 'object' ? field.name : field;
        const displayName = typeof field === 'object' ? (field.displayName || field.name) : field;

        // Создаем элемент с чекбоксом для каждого поля
        const fieldCol = document.createElement('div');
        fieldCol.className = 'col-md-4 mb-2';

        fieldCol.innerHTML = `
            <div class="form-check">
                <input class="form-check-input field-checkbox" type="checkbox" 
                       id="field_${fieldName}" value="${fieldName}" checked>
                <label class="form-check-label" for="field_${fieldName}">
                    ${displayName}
                </label>
            </div>
        `;

        fieldsListContainer.appendChild(fieldCol);
    });

    // Отладочный вывод
    console.log(`Отображено ${fields.length} полей для экспорта`);
}

// Переключение всех полей
function toggleAllFields() {
    const selectAllFieldsCheckbox = document.getElementById('selectAllFields');
    const allCheckboxes = document.querySelectorAll('#fieldsList .field-checkbox');

    if (!selectAllFieldsCheckbox) return;

    const checked = selectAllFieldsCheckbox.checked;

    allCheckboxes.forEach(checkbox => {
        checkbox.checked = checked;
    });
}

// Сбор выбранных полей
function collectSelectedFields() {
    const selectedFields = [];
    const checkboxes = document.querySelectorAll('#fieldsList .field-checkbox');

    checkboxes.forEach(checkbox => {
        if (checkbox.checked) {
            selectedFields.push(checkbox.value);
        }
    });

    return selectedFields;
}

// Обработчик настраиваемого экспорта
function handleCustomExport() {
    const customEntityTypeSelect = document.getElementById('customEntityType');
    const customFileFormatSelect = document.getElementById('customFileFormat');
    const clientId = document.querySelector('input[name="clientId"]').value;

    // Проверяем выбор типа сущности
    if (!customEntityTypeSelect.value) {
        alert('Пожалуйста, выберите тип данных');
        customEntityTypeSelect.focus();
        return false;
    }

    // Собираем выбранные поля
    const selectedFields = collectSelectedFields();

    // Собираем настройки форматирования
    const formatSettings = {
        dateFormat: document.getElementById('dateFormat')?.value,
        numberFormat: document.getElementById('numberFormat')?.value,
        csvDelimiter: document.getElementById('csvDelimiter')?.value,
        includeHeader: document.getElementById('includeHeader')?.checked
    };

    // Формируем URL
    let url = `/export/entity/${customEntityTypeSelect.value}?clientId=${clientId}&format=${customFileFormatSelect.value}`;

    // Добавляем параметры для полей и форматирования
    if (selectedFields.length > 0) {
        url += `&fields=${selectedFields.join(',')}`;
    }

    // Добавляем параметры форматирования
    for (const [key, value] of Object.entries(formatSettings)) {
        if (value !== undefined) {
            url += `&${key}=${encodeURIComponent(value)}`;
        }
    }

    // Переходим по URL для скачивания
    window.location.href = url;
}

// Показ модального окна для сохранения шаблона
function showSaveTemplateModal() {
    // Проверяем необходимые поля перед показом
    const customEntityTypeSelect = document.getElementById('customEntityType');

    if (!customEntityTypeSelect.value) {
        alert('Пожалуйста, выберите тип данных перед сохранением шаблона');
        customEntityTypeSelect.focus();
        return;
    }

    // Показываем модальное окно (используя Bootstrap)
    const saveTemplateModal = new bootstrap.Modal(document.getElementById('saveTemplateModal'));
    saveTemplateModal.show();
}

// Инициализация отслеживания прогресса экспорта
function initExportProgressTracking() {
    // Получаем id операции экспорта, если есть
    const operationElement = document.querySelector('[data-operation-id]');
    if (!operationElement) return;

    const operationId = operationElement.dataset.operationId;
    if (!operationId) return;

    // Проверяем, что это операция экспорта
    const operationType = operationElement.dataset.operationType;
    if (operationType !== 'EXPORT') return;

    // Подключаемся к WebSocket
    connectToExportProgressWebSocket(operationId);
}

// Подключение к WebSocket для отслеживания прогресса экспорта
function connectToExportProgressWebSocket(operationId) {
    const socket = new SockJS('/ws');
    const stompClient = Stomp.over(socket);

    // Отключаем логи
    stompClient.debug = null;

    stompClient.connect({}, function (frame) {
        console.log('Connected to WebSocket for export progress tracking');

        // Подписываемся на обновления
        stompClient.subscribe('/topic/export-progress/' + operationId, function (message) {
            updateExportProgress(JSON.parse(message.body));
        });
    }, function (error) {
        console.error('Error connecting to WebSocket:', error);
    });

    // Сохраняем клиент в глобальной переменной для доступа
    window.exportStompClient = stompClient;
}

// Обновление UI прогресса экспорта
function updateExportProgress(update) {
    console.log('Export progress update:', update);

    // Обновляем прогресс-бар
    const progressBar = document.getElementById('exportProgressBar');
    if (progressBar) {
        progressBar.style.width = update.progress + '%';
        progressBar.setAttribute('aria-valuenow', update.progress);
        progressBar.textContent = update.progress + '%';
    }

    // Обновляем текст прогресса
    const progressText = document.getElementById('exportProgressText');
    if (progressText) {
        if (update.processed !== undefined && update.total !== undefined) {
            progressText.textContent = `Обработано ${update.processed} из ${update.total} записей (${update.progress}%)`;
        }
    }

    // Добавляем запись в журнал
    addExportLogEntry(update);

    // Если операция завершена, обновляем страницу через некоторое время
    if (update.completed) {
        setTimeout(function () {
            window.location.reload();
        }, 3000);
    }
}

// Добавляет запись в журнал операции экспорта
function addExportLogEntry(update) {
    const logContainer = document.getElementById('exportLogContainer');
    if (!logContainer) return;

    const now = new Date();

    // Форматируем время
    const timeStr = now.toTimeString().split(' ')[0];

    // Создаем запись журнала
    const logEntry = document.createElement('div');
    logEntry.className = 'log-entry';

    let logClass = 'log-info';
    let logMessage = '';

    if (update.completed) {
        if (update.successful) {
            logClass = 'log-success';
            logMessage = `Экспорт успешно завершен. Экспортировано записей: ${update.processed}`;
        } else {
            logClass = 'log-error';
            logMessage = `Экспорт завершился с ошибкой: ${update.errorMessage || 'Неизвестная ошибка'}`;
        }
    } else {
        logMessage = `Обработано ${update.processed} из ${update.total} записей (${update.progress}%)`;
    }

    logEntry.innerHTML = `
        <span class="log-time">${timeStr}</span>
        <span class="${logClass}">${logMessage}</span>
    `;

    // Добавляем запись в контейнер
    logContainer.appendChild(logEntry);

    // Прокручиваем контейнер вниз
    logContainer.scrollTop = logContainer.scrollHeight;
}

// Функция для отмены экспорта
function cancelExport() {
    const operationElement = document.querySelector('[data-operation-id]');
    if (!operationElement) return;

    const operationId = operationElement.dataset.operationId;
    if (!operationId) return;

    fetch(`/export/api/cancel/${operationId}`, {
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
                alert(`Не удалось отменить экспорт: ${data.message}`);
            }
        })
        .catch(error => {
            console.error('Error cancelling export:', error);
            alert('Произошла ошибка при отмене экспорта');
        });
}

// Функция для обновления вкладки экспорта
function refreshExportTab() {
    // Простой способ - перезагрузить страницу
    window.location.reload();
}

// Функция для сохранения шаблона экспорта
function saveExportTemplate() {
    const templateName = document.getElementById('templateName').value;
    const templateDescription = document.getElementById('templateDescription').value;

    if (!templateName) {
        alert('Пожалуйста, введите название шаблона');
        document.getElementById('templateName').focus();
        return;
    }

    // Собираем данные шаблона
    const templateData = {
        name: templateName,
        description: templateDescription,
        clientId: document.querySelector('input[name="clientId"]').value,
        entityType: document.getElementById('customEntityType').value,
        format: document.getElementById('customFileFormat').value,
        fields: collectSelectedFields(),
        formatSettings: {
            dateFormat: document.getElementById('dateFormat')?.value,
            numberFormat: document.getElementById('numberFormat')?.value,
            csvDelimiter: document.getElementById('csvDelimiter')?.value,
            includeHeader: document.getElementById('includeHeader')?.checked
        }
    };

    // Отправляем запрос на сохранение
    fetch('/export/api/templates', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(templateData)
    })
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                alert('Шаблон успешно сохранен');
                // Скрываем модальное окно
                bootstrap.Modal.getInstance(document.getElementById('saveTemplateModal')).hide();
            } else {
                alert(data.message || 'Ошибка при сохранении шаблона');
            }
        })
        .catch(error => {
            console.error('Ошибка при сохранении шаблона:', error);
            alert('Ошибка при сохранении шаблона');
        });
}