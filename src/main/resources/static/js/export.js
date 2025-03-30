/**
 * JavaScript для страницы экспорта данных
 * Путь: /resources/static/js/export.js
 */

document.addEventListener('DOMContentLoaded', function() {
    // Элементы формы стандартного экспорта
    const exportForm = document.getElementById('exportForm');
    const clientSelect = document.getElementById('clientSelect');
    const entityTypeSelect = document.getElementById('entityType');
    const fileFormatSelect = document.getElementById('fileFormat');
    const filtersContainer = document.getElementById('filters');
    const addFilterBtn = document.getElementById('addFilterBtn');
    const exportBtn = document.getElementById('exportBtn');
    const asyncExportBtn = document.getElementById('asyncExportBtn');

    // Элементы формы настраиваемого экспорта
    const customExportForm = document.getElementById('customExportForm');
    const customClientSelect = document.getElementById('customClientSelect');
    const customEntityTypeSelect = document.getElementById('customEntityType');
    const customFileFormatSelect = document.getElementById('customFileFormat');
    const fieldsListContainer = document.getElementById('fieldsList');
    const selectAllFieldsCheckbox = document.getElementById('selectAllFields');
    const customExportBtn = document.getElementById('customExportBtn');
    const saveTemplateBtn = document.getElementById('saveTemplateBtn');

    // Элементы модального окна прогресса
    const progressModal = new bootstrap.Modal(document.getElementById('progressModal'));
    const progressBar = document.getElementById('exportProgressBar');
    const progressStatus = document.getElementById('exportProgressStatus');
    const progressDetails = document.getElementById('exportProgressDetails');
    const downloadResultBtn = document.getElementById('downloadResultBtn');

    // Элементы модального окна сохранения шаблона
    const saveTemplateModal = new bootstrap.Modal(document.getElementById('saveTemplateModal'));
    const templateNameInput = document.getElementById('templateName');
    const templateDescriptionInput = document.getElementById('templateDescription');
    const confirmSaveTemplateBtn = document.getElementById('confirmSaveTemplateBtn');

    // WebSocket для отслеживания прогресса
    let stompClient = null;
    let currentOperationId = null;

    // Инициализация
    init();

    // Функция инициализации
    function init() {
        // Обработчики событий для стандартного экспорта
        if (addFilterBtn) addFilterBtn.addEventListener('click', addFilterRow);
        if (exportBtn) exportBtn.addEventListener('click', handleStandardExport);
        if (asyncExportBtn) asyncExportBtn.addEventListener('click', handleAsyncExport);
        if (entityTypeSelect) entityTypeSelect.addEventListener('change', handleEntityTypeChange);

        // Обработчики событий для настраиваемого экспорта
        if (customEntityTypeSelect) customEntityTypeSelect.addEventListener('change', loadEntityFields);
        if (selectAllFieldsCheckbox) selectAllFieldsCheckbox.addEventListener('change', toggleAllFields);
        if (customExportBtn) customExportBtn.addEventListener('click', handleCustomExport);
        if (saveTemplateBtn) saveTemplateBtn.addEventListener('click', showSaveTemplateModal);
        if (confirmSaveTemplateBtn) confirmSaveTemplateBtn.addEventListener('click', saveExportTemplate);

        // Инициализация первого фильтра
        if (filtersContainer && filtersContainer.children.length === 0) {
            addFilterRow();
        }
    }

    // Обработчик изменения типа сущности
    function handleEntityTypeChange() {
        // Очищаем фильтры
        if (filtersContainer) filtersContainer.innerHTML = '';

        // Запрашиваем поля для выбранного типа сущности
        const entityType = entityTypeSelect.value;
        if (entityType) {
            // Показываем индикатор загрузки
            const loadingIndicator = document.createElement('div');
            loadingIndicator.className = 'text-center my-3';
            loadingIndicator.innerHTML = '<div class="spinner-border text-primary" role="status"><span class="visually-hidden">Загрузка...</span></div>';
            filtersContainer.appendChild(loadingIndicator);

            // Запрос доступных полей
            fetch(`/api/metadata/fields/${entityType}`)
                .then(response => {
                    if (!response.ok) {
                        throw new Error(`HTTP error! Status: ${response.status}`);
                    }
                    return response.json();
                })
                .then(data => {
                    // Удаляем индикатор загрузки
                    filtersContainer.innerHTML = '';

                    // Проверяем структуру данных и создаем datalist для полей
                    createFieldsDatalist(data);

                    // Добавляем начальный фильтр
                    addFilterRow();
                })
                .catch(error => {
                    console.error('Ошибка при загрузке полей:', error);

                    // Удаляем индикатор загрузки
                    filtersContainer.innerHTML = '';

                    // Показываем сообщение об ошибке
                    const errorMsg = document.createElement('div');
                    errorMsg.className = 'alert alert-warning mt-2';
                    errorMsg.textContent = `Не удалось загрузить список полей: ${error.message}`;
                    filtersContainer.parentNode.insertBefore(errorMsg, filtersContainer);

                    // Добавляем пустой фильтр
                    addFilterRow();
                });
        }
    }

// Обновленная функция для создания datalist полей
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
            if (typeof field === 'string') {
                option.value = field;
                option.textContent = field;
            } else {
                option.value = field.name;
                option.textContent = field.displayName || field.name;
            }
            datalist.appendChild(option);
        });

        // Отладочный вывод в консоль для проверки
        console.log(`Загружено ${fields.length} полей в datalist`);
    }

    // Добавление строки фильтра
    function addFilterRow() {
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
                    <i class="bi bi-trash"></i>
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
    function handleStandardExport() {
        if (!validateExportForm()) return;

        // Формируем URL для экспорта
        const url = buildExportUrl();

        // Переходим по URL для скачивания файла
        window.location.href = url;
    }

    // Обработчик асинхронного экспорта
    function handleAsyncExport() {
        if (!validateExportForm()) return;

        // Формируем URL для асинхронного экспорта
        const clientId = clientSelect.value;
        const entityType = entityTypeSelect.value;
        const format = fileFormatSelect.value;

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
                if (data.operationId) {
                    // Сохраняем ID операции
                    currentOperationId = data.operationId;

                    // Показываем модальное окно прогресса
                    resetProgressUI();
                    progressModal.show();

                    // Подключаемся к WebSocket для отслеживания прогресса
                    connectToWebSocket(data.operationId);
                } else {
                    alert(data.message || 'Ошибка при запуске экспорта');
                }
            })
            .catch(error => {
                console.error('Ошибка при запуске асинхронного экспорта:', error);
                alert('Ошибка при запуске экспорта');
            });
    }

    // Обработчик настраиваемого экспорта
    function handleCustomExport() {
        if (!validateCustomExportForm()) return;

        // Собираем параметры
        const clientId = customClientSelect.value;
        const entityType = customEntityTypeSelect.value;
        const format = customFileFormatSelect.value;

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
        let url = `/export/entity/${entityType}?clientId=${clientId}&format=${format}`;

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

    // Валидация формы экспорта
    function validateExportForm() {
        // Проверяем выбор клиента и типа сущности
        if (!clientSelect.value) {
            alert('Пожалуйста, выберите клиента');
            clientSelect.focus();
            return false;
        }

        if (!entityTypeSelect.value) {
            alert('Пожалуйста, выберите тип данных');
            entityTypeSelect.focus();
            return false;
        }

        // Проверяем фильтры
        const filterRows = filtersContainer.querySelectorAll('.filter-row');
        let valid = true;

        filterRows.forEach(row => {
            const fieldInput = row.querySelector('.field-name');
            const valueInput = row.querySelector('.filter-value');

            if (fieldInput && fieldInput.value && valueInput && !valueInput.value) {
                alert('Пожалуйста, заполните значение для фильтра: ' + fieldInput.value);
                valueInput.focus();
                valid = false;
                return;
            }
        });

        return valid;
    }

    // Валидация формы настраиваемого экспорта
    function validateCustomExportForm() {
        if (!customClientSelect.value) {
            alert('Пожалуйста, выберите клиента');
            customClientSelect.focus();
            return false;
        }

        if (!customEntityTypeSelect.value) {
            alert('Пожалуйста, выберите тип данных');
            customEntityTypeSelect.focus();
            return false;
        }

        return true;
    }

    // Построение URL для экспорта
    function buildExportUrl() {
        const clientId = clientSelect.value;
        const entityType = entityTypeSelect.value;
        const format = fileFormatSelect.value;

        const baseUrl = `/export/entity/${entityType}?clientId=${clientId}&format=${format}`;
        const filterParams = collectFilterParams();

        return baseUrl + filterParams;
    }

    // Сбор параметров фильтров
    function collectFilterParams() {
        let params = '';

        const filterRows = filtersContainer.querySelectorAll('.filter-row');
        filterRows.forEach(row => {
            const fieldInput = row.querySelector('.field-name');
            const operatorSelect = row.querySelector('.operator');
            const valueInput = row.querySelector('.filter-value');

            if (fieldInput && fieldInput.value && valueInput && valueInput.value) {
                const field = fieldInput.value;
                const operator = operatorSelect.value;
                const value = valueInput.value;

                params += `&${field}__${operator}=${encodeURIComponent(value)}`;
            }
        });

        return params;
    }

    // Загрузка полей сущности для настраиваемого экспорта
    function loadEntityFields() {
        const entityType = customEntityTypeSelect.value;
        if (!entityType) return;

        // Очищаем список полей
        if (fieldsListContainer) fieldsListContainer.innerHTML = '';

        // Запрашиваем поля для выбранного типа сущности
        fetch(`/api/metadata/fields/${entityType}`)
            .then(response => response.json())
            .then(data => {
                // Отображаем поля
                displayEntityFields(data);
            })
            .catch(error => {
                console.error('Ошибка при загрузке полей:', error);
                alert('Не удалось загрузить список полей');
            });
    }

    // Отображение полей сущности
    function displayEntityFields(fields) {
        if (!fieldsListContainer) return;

        fields.forEach(field => {
            // Создаем элемент с чекбоксом для каждого поля
            const fieldCol = document.createElement('div');
            fieldCol.className = 'col-md-4 mb-2';

            fieldCol.innerHTML = `
                <div class="form-check">
                    <input class="form-check-input field-checkbox" type="checkbox" 
                           id="field_${field.name}" value="${field.name}" checked>
                    <label class="form-check-label" for="field_${field.name}">
                        ${field.displayName || field.name}
                    </label>
                </div>
            `;

            fieldsListContainer.appendChild(fieldCol);
        });
    }

    // Переключение всех полей
    function toggleAllFields() {
        const allCheckboxes = fieldsListContainer.querySelectorAll('.field-checkbox');
        const checked = selectAllFieldsCheckbox.checked;

        allCheckboxes.forEach(checkbox => {
            checkbox.checked = checked;
        });
    }

    // Сбор выбранных полей
    function collectSelectedFields() {
        const selectedFields = [];
        const checkboxes = fieldsListContainer.querySelectorAll('.field-checkbox');

        checkboxes.forEach(checkbox => {
            if (checkbox.checked) {
                selectedFields.push(checkbox.value);
            }
        });

        return selectedFields;
    }

    // Показ модального окна для сохранения шаблона
    function showSaveTemplateModal() {
        // Очищаем поля
        templateNameInput.value = '';
        templateDescriptionInput.value = '';

        // Показываем модальное окно
        saveTemplateModal.show();
    }

    // Сохранение шаблона экспорта
    function saveExportTemplate() {
        if (!templateNameInput.value) {
            alert('Пожалуйста, введите название шаблона');
            templateNameInput.focus();
            return;
        }

        // Собираем данные шаблона
        const templateData = {
            name: templateNameInput.value,
            description: templateDescriptionInput.value,
            clientId: customClientSelect.value,
            entityType: customEntityTypeSelect.value,
            format: customFileFormatSelect.value,
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
                    saveTemplateModal.hide();
                } else {
                    alert(data.message || 'Ошибка при сохранении шаблона');
                }
            })
            .catch(error => {
                console.error('Ошибка при сохранении шаблона:', error);
                alert('Ошибка при сохранении шаблона');
            });
    }

    // Подключение к WebSocket для отслеживания прогресса
    function connectToWebSocket(operationId) {
        // Отключаем предыдущее подключение
        if (stompClient && stompClient.connected) {
            stompClient.disconnect();
        }

        // Создаем новое подключение
        const socket = new SockJS('/ws');
        stompClient = Stomp.over(socket);

        stompClient.connect({}, function(frame) {
            console.log('Подключено к WebSocket');

            // Подписываемся на обновления прогресса
            stompClient.subscribe(`/topic/export-progress/${operationId}`, function(message) {
                const progressData = JSON.parse(message.body);
                updateProgressUI(progressData);
            });

            // Отправляем запрос на получение начального статуса
            stompClient.send('/app/export-progress', {}, operationId.toString());

        }, function(error) {
            console.error('Ошибка подключения к WebSocket:', error);
            updateProgressUI({
                status: 'ERROR',
                message: 'Ошибка подключения к серверу',
                progress: 0
            });
        });
    }

    // Обновление интерфейса прогресса
    function updateProgressUI(data) {
        const progress = data.progress || 0;
        const status = data.status || '';
        const message = data.message || '';

        // Обновляем прогресс-бар
        progressBar.style.width = `${progress}%`;
        progressBar.textContent = `${progress}%`;
        progressBar.setAttribute('aria-valuenow', progress);

        // Обновляем статус
        progressStatus.textContent = message || `Статус: ${status}`;

        // Обновляем детали
        let details = '';
        if (data.processed !== undefined && data.total !== undefined) {
            details = `Обработано ${data.processed} из ${data.total} записей`;
        }
        if (data.estimatedTimeRemaining !== undefined) {
            const seconds = Math.floor(data.estimatedTimeRemaining / 1000);
            details += ` • Осталось примерно ${formatTime(seconds)}`;
        }
        progressDetails.textContent = details;

        // Если экспорт завершен, показываем кнопку скачивания
        if (status === 'COMPLETED') {
            downloadResultBtn.href = `/export/result/${data.operationId}`;
            downloadResultBtn.classList.remove('d-none');

            // Останавливаем анимацию прогресс-бара
            progressBar.classList.remove('progress-bar-animated');
            progressBar.classList.remove('progress-bar-striped');
            progressBar.classList.add('bg-success');

            // Отключаем WebSocket
            if (stompClient && stompClient.connected) {
                stompClient.disconnect();
            }
        } else if (status === 'ERROR' || status === 'FAILED') {
            // В случае ошибки меняем цвет прогресс-бара
            progressBar.classList.remove('progress-bar-animated');
            progressBar.classList.remove('progress-bar-striped');
            progressBar.classList.add('bg-danger');

            // Отключаем WebSocket
            if (stompClient && stompClient.connected) {
                stompClient.disconnect();
            }
        }
    }

    // Сброс интерфейса прогресса
    function resetProgressUI() {
        progressBar.style.width = '0%';
        progressBar.textContent = '0%';
        progressBar.setAttribute('aria-valuenow', 0);
        progressBar.classList.remove('bg-success', 'bg-danger');
        progressBar.classList.add('progress-bar-striped', 'progress-bar-animated');

        progressStatus.textContent = 'Подготовка к экспорту...';
        progressDetails.textContent = '';

        downloadResultBtn.classList.add('d-none');
        downloadResultBtn.href = '#';
    }

    // Форматирование времени в виде "чч:мм:сс"
    function formatTime(seconds) {
        const hours = Math.floor(seconds / 3600);
        const minutes = Math.floor((seconds % 3600) / 60);
        const remainingSeconds = seconds % 60;

        if (hours > 0) {
            return `${hours}ч ${minutes}м ${remainingSeconds}с`;
        } else if (minutes > 0) {
            return `${minutes}м ${remainingSeconds}с`;
        } else {
            return `${remainingSeconds}с`;
        }
    }
});

