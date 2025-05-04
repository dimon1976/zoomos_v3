/**
 * JavaScript для работы с формой экспорта данных
 * src/main/resources/static/js/export.js
 */
document.addEventListener('DOMContentLoaded', function() {
    // Инициализация перетаскивания для сортировки полей
    initSortableFields();

    // Инициализация обработчиков для формы
    initFormHandlers();
});

/**
 * Инициализация обработчиков событий формы экспорта
 */
function initFormHandlers() {
    // Формат файла
    const formatSelect = document.getElementById('format');
    if (formatSelect) {
        formatSelect.addEventListener('change', updateFormatSettings);
    }

    // Кнопки выбора/снятия выбора полей
    const selectAllBtn = document.getElementById('selectAllBtn');
    if (selectAllBtn) {
        selectAllBtn.addEventListener('click', function() {
            document.querySelectorAll('input[name="fields"]').forEach(function(checkbox) {
                checkbox.checked = true;
            });
        });
    }

    const deselectAllBtn = document.getElementById('deselectAllBtn');
    if (deselectAllBtn) {
        deselectAllBtn.addEventListener('click', function() {
            document.querySelectorAll('input[name="fields"]').forEach(function(checkbox) {
                checkbox.checked = false;
            });
        });
    }

    // Показ/скрытие поля для названия шаблона
    const saveAsTemplateCheckbox = document.getElementById('saveAsTemplate');
    const templateNameGroup = document.getElementById('templateNameGroup');
    if (saveAsTemplateCheckbox && templateNameGroup) {
        saveAsTemplateCheckbox.addEventListener('change', function() {
            templateNameGroup.style.display = this.checked ? 'block' : 'none';
        });
    }

    // Обработка изменения стратегии
    const strategySelect = document.getElementById('strategyId');
    if (strategySelect) {
        strategySelect.addEventListener('change', updateStrategySettings);
    }

    // Настройка заголовков полей
    const customizeLabelsBtn = document.getElementById('customizeLabelsBtn');
    if (customizeLabelsBtn) {
        customizeLabelsBtn.addEventListener('click', openCustomizeLabelsModal);
    }

    // Сохранение заголовков полей
    const saveHeadersBtn = document.getElementById('saveHeadersBtn');
    if (saveHeadersBtn) {
        saveHeadersBtn.addEventListener('click', saveHeaders);
    }

    // Изменение порядка полей
    const sortFieldsBtn = document.getElementById('sortFieldsBtn');
    if (sortFieldsBtn) {
        sortFieldsBtn.addEventListener('click', openSortFieldsModal);
    }

    // Сохранение порядка полей
    const saveOrderBtn = document.getElementById('saveOrderBtn');
    if (saveOrderBtn) {
        saveOrderBtn.addEventListener('click', saveFieldOrder);
    }
}

/**
 * Инициализация Sortable.js для перетаскивания полей
 */
function initSortableFields() {
    // Проверяем, что Sortable.js загружен
    if (typeof Sortable === 'undefined') {
        console.warn('Sortable.js не загружен! Загружаю...');
        // Динамически загружаем Sortable.js
        const script = document.createElement('script');
        script.src = 'https://cdnjs.cloudflare.com/ajax/libs/Sortable/1.14.0/Sortable.min.js';
        script.onload = function() {
            console.log('Sortable.js успешно загружен');
            enableSortable();
        };
        document.head.appendChild(script);
    } else {
        enableSortable();
    }
}

/**
 * Активирует Sortable.js для списка полей
 */
function enableSortable() {
    const sortableList = document.getElementById('sortableFields');
    if (sortableList) {
        console.log('Инициализация Sortable для списка полей');
        new Sortable(sortableList, {
            animation: 150,
            handle: '.handle',
            ghostClass: 'bg-light'
        });
    } else {
        console.warn('Элемент sortableFields не найден');
    }
}

/**
 * Функция переключения настроек формата файла
 */
function updateFormatSettings() {
    const format = document.getElementById('format').value;
    const csvParams = document.getElementById('csvParams');
    const excelParams = document.getElementById('excelParams');

    if (csvParams && excelParams) {
        csvParams.style.display = format === 'csv' ? 'block' : 'none';
        excelParams.style.display = format === 'xlsx' ? 'block' : 'none';
    }
}

/**
 * Функция переключения настроек стратегии
 */
function updateStrategySettings() {
    const strategyId = document.getElementById('strategyId').value;
    const strategyParams = document.getElementById('strategyParams');

    if (strategyParams) {
        strategyParams.style.display = strategyId && strategyId !== '' ? 'block' : 'none';

        // Отображаем нужные параметры для выбранной стратегии
        const filteredParams = document.getElementById('filteredStrategyParams');
        if (filteredParams) {
            filteredParams.style.display = strategyId === 'filtered' ? 'block' : 'none';
        }
    }
}

/**
 * Функция для загрузки шаблона
 */
function loadTemplate() {
    const templateId = document.getElementById('templateId').value;
    if (!templateId) {
        // Если шаблон не выбран, обновляем страницу без шаблона
        window.location.href = window.location.pathname + '?entityType=' +
            document.querySelector('input[name="entityType"]').value;
        return;
    }

    // Перезагружаем страницу с выбранным шаблоном
    window.location.href = window.location.pathname + '?entityType=' +
        document.querySelector('input[name="entityType"]').value + '&templateId=' + templateId;
}

/**
 * Открытие модального окна для настройки заголовков
 */
function openCustomizeLabelsModal() {
    console.log('Открытие модального окна для настройки заголовков');
    const tableBody = document.getElementById('headersTableBody');

    if (!tableBody) {
        console.error('Не найден элемент с id="headersTableBody"');
        return;
    }

    tableBody.innerHTML = '';

    // Получаем выбранные поля
    let checkboxes = document.querySelectorAll('input[name="fields"]:checked');
    console.log(`Найдено ${checkboxes.length} выбранных полей`);

    if (checkboxes.length === 0) {
        // Проверяем альтернативное имя полей (могут быть разные формы)
        checkboxes = document.querySelectorAll('input[name="fields[]"]:checked');
        console.log(`Альтернативный селектор: найдено ${checkboxes.length} выбранных полей`);
    }

    if (checkboxes.length === 0) {
        alert('Пожалуйста, выберите поля для экспорта');
        return;
    }

    const selectedFields = Array.from(checkboxes).map(checkbox => checkbox.value);

    // Для каждого выбранного поля добавляем строку в таблицу
    selectedFields.forEach(field => {
        const row = document.createElement('tr');

        // Ячейка с именем поля
        const fieldCell = document.createElement('td');
        const checkboxId = getCheckboxId(field);
        const fieldLabel = checkboxId ? document.querySelector(`label[for="${checkboxId}"]`) : null;

        if (fieldLabel) {
            fieldCell.textContent = fieldLabel.textContent;
        } else {
            // Если не нашли label, попробуем извлечь имя поля из значения
            const lastDotIndex = field.lastIndexOf('.');
            fieldCell.textContent = lastDotIndex > -1 ? field.substring(lastDotIndex + 1) : field;
        }

        // Ячейка с полем ввода для заголовка
        const headerCell = document.createElement('td');
        const headerInput = document.createElement('input');
        headerInput.type = 'text';
        headerInput.className = 'form-control';
        headerInput.dataset.field = field;

        // Получаем текущее значение заголовка
        const headerKey = 'header_' + field.replace(/\./g, '_');
        const currentHeaderInput = document.getElementById(headerKey);

        if (currentHeaderInput) {
            headerInput.value = currentHeaderInput.value;
        } else if (fieldLabel) {
            headerInput.value = fieldLabel.textContent;
        } else {
            // По умолчанию используем имя поля с преобразованием camelCase в нормальный текст
            const baseName = field.substring(field.lastIndexOf('.') + 1);
            headerInput.value = baseName
                .replace(/([A-Z])/g, ' $1')
                .replace(/^./, (str) => str.toUpperCase())
                .trim();
        }

        headerCell.appendChild(headerInput);
        row.appendChild(fieldCell);
        row.appendChild(headerCell);
        tableBody.appendChild(row);
    });

    // Открываем модальное окно
    try {
        const modalElement = document.getElementById('customizeLabelsModal');
        if (!modalElement) {
            console.error('Не найден элемент модального окна с id="customizeLabelsModal"');
            return;
        }
        const modal = new bootstrap.Modal(modalElement);
        modal.show();
    } catch (e) {
        console.error('Ошибка при открытии модального окна:', e);
        alert('Ошибка при открытии окна настройки заголовков');
    }
}

/**
 * Сохранение настроенных заголовков
 */
function saveHeaders() {
    console.log('Сохранение настроенных заголовков');
    const inputs = document.querySelectorAll('#headersTableBody input');

    inputs.forEach(input => {
        const field = input.dataset.field;
        if (!field) {
            console.warn('Поле не имеет dataset.field');
            return;
        }

        const headerKey = 'header_' + field.replace(/\./g, '_');
        const headerInput = document.getElementById(headerKey);

        if (headerInput) {
            console.log(`Обновление заголовка для ${field}: "${input.value}"`);
            headerInput.value = input.value;
        } else {
            console.warn(`Не найден элемент ввода с id=${headerKey}`);

            // Создаем скрытое поле для заголовка, если его нет
            const newHeaderInput = document.createElement('input');
            newHeaderInput.type = 'hidden';
            newHeaderInput.id = headerKey;
            newHeaderInput.name = headerKey;
            newHeaderInput.value = input.value;

            // Находим форму и добавляем в нее элемент
            const form = document.querySelector('form');
            if (form) {
                form.appendChild(newHeaderInput);
                console.log(`Создан новый элемент ввода для заголовка ${field}`);
            } else {
                console.error('Форма не найдена');
            }
        }
    });

    // Закрываем модальное окно
    try {
        const modalElement = document.getElementById('customizeLabelsModal');
        if (modalElement) {
            const modal = bootstrap.Modal.getInstance(modalElement);
            if (modal) {
                modal.hide();
            } else {
                console.warn('Не удалось получить экземпляр модального окна');
                modalElement.style.display = 'none';
            }
        } else {
            console.error('Не найден элемент модального окна');
        }
    } catch (e) {
        console.error('Ошибка при закрытии модального окна:', e);
    }

    console.log('Заголовки успешно сохранены');
}

// Сохранение порядка полей
function saveFieldOrder() {
    console.log('Сохранение порядка полей');

    // Получаем новый порядок полей из списка
    const sortedItems = document.querySelectorAll('#sortableFields li');
    if (sortedItems.length === 0) {
        console.warn('Не найдены элементы для сортировки');
        return;
    }

    // Находим форму
    const exportForm = document.querySelector('form');
    if (!exportForm) {
        console.error('Форма не найдена');
        return;
    }

    // Создаем скрытое поле для сохранения порядка полей при отправке формы
    let orderInput = document.getElementById('fieldsOrder');
    if (!orderInput) {
        orderInput = document.createElement('input');
        orderInput.type = 'hidden';
        orderInput.id = 'fieldsOrder';
        orderInput.name = 'fieldsOrder';
        exportForm.appendChild(orderInput);
        console.log('Создано новое поле для хранения порядка полей');
    }

    // Сохраняем порядок полей как JSON
    const fieldOrder = Array.from(sortedItems).map(item => item.dataset.field);
    orderInput.value = JSON.stringify(fieldOrder);
    console.log('Новый порядок полей:', fieldOrder);

    // Обновляем порядок визуально, если это возможно
    try {
        const fieldsContainer = document.getElementById('fieldsContainer');
        if (fieldsContainer) {
            // Перемещаем элементы в соответствии с новым порядком
            const fieldItems = fieldsContainer.querySelectorAll('.field-item');
            if (fieldItems.length > 0) {
                console.log('Обновление визуального порядка полей');

                const fieldItemsMap = {};
                fieldItems.forEach(item => {
                    const field = item.dataset.field;
                    if (field) {
                        fieldItemsMap[field] = item;
                    }
                });

                // Перемещаем элементы в нужном порядке
                fieldOrder.forEach(field => {
                    const item = fieldItemsMap[field];
                    if (item) {
                        fieldsContainer.appendChild(item);
                    }
                });
            }
        }
    } catch (e) {
        console.warn('Не удалось обновить визуальный порядок полей:', e);
    }

    // Закрываем модальное окно
    try {
        const modalElement = document.getElementById('sortFieldsModal');
        if (modalElement) {
            const modal = bootstrap.Modal.getInstance(modalElement);
            if (modal) {
                modal.hide();
            } else {
                console.warn('Не удалось получить экземпляр модального окна');
                modalElement.style.display = 'none';
            }
        } else {
            console.error('Не найден элемент модального окна');
        }
    } catch (e) {
        console.error('Ошибка при закрытии модального окна:', e);
    }

    console.log('Порядок полей успешно сохранен');
}

/**
 * Открытие модального окна для изменения порядка полей
 */
function openSortFieldsModal() {
    console.log('Открытие модального окна для изменения порядка полей');
    const sortableList = document.getElementById('sortableFields');

    if (!sortableList) {
        console.error('Не найден элемент с id="sortableFields"');
        return;
    }

    sortableList.innerHTML = '';

    // Получаем выбранные поля
    let checkboxes = document.querySelectorAll('input[name="fields"]:checked');
    console.log(`Найдено ${checkboxes.length} выбранных полей`);

    if (checkboxes.length === 0) {
        // Проверяем альтернативное имя полей (могут быть разные формы)
        checkboxes = document.querySelectorAll('input[name="fields[]"]:checked');
        console.log(`Альтернативный селектор: найдено ${checkboxes.length} выбранных полей`);
    }

    if (checkboxes.length === 0) {
        alert('Пожалуйста, выберите поля для экспорта');
        return;
    }

    const selectedFields = Array.from(checkboxes)
        .map(checkbox => {
            const fieldItem = checkbox.closest('.field-item');
            const label = document.querySelector(`label[for="${checkbox.id}"]`);
            return {
                value: checkbox.value,
                label: label ? label.textContent : checkbox.value,
                order: fieldItem && fieldItem.dataset.order ? parseInt(fieldItem.dataset.order) : 0
            };
        })
        .sort((a, b) => a.order - b.order);

    console.log(`Подготовлено ${selectedFields.length} полей для сортировки`);

    // Проверяем, есть ли уже сохраненный порядок
    const fieldsOrderInput = document.getElementById('fieldsOrder');
    if (fieldsOrderInput && fieldsOrderInput.value) {
        try {
            const savedOrder = JSON.parse(fieldsOrderInput.value);
            console.log('Найден сохраненный порядок полей:', savedOrder);

            // Сортируем поля по сохраненному порядку
            selectedFields.sort((a, b) => {
                const indexA = savedOrder.indexOf(a.value);
                const indexB = savedOrder.indexOf(b.value);
                if (indexA === -1) return 1;
                if (indexB === -1) return -1;
                return indexA - indexB;
            });
        } catch (e) {
            console.warn('Ошибка при разборе сохраненного порядка полей:', e);
        }
    }

    // Для каждого выбранного поля добавляем элемент в список
    selectedFields.forEach((field, index) => {
        const listItem = document.createElement('li');
        listItem.className = 'list-group-item d-flex align-items-center';
        listItem.dataset.field = field.value;
        listItem.dataset.order = index;

        // Добавляем "ручку" для перетаскивания
        const handle = document.createElement('span');
        handle.className = 'me-2 handle';
        handle.innerHTML = '&#9776;'; // Unicode символ "burger" меню
        handle.style.cursor = 'grab';

        // Добавляем название поля
        const label = document.createElement('span');
        label.textContent = field.label;

        listItem.appendChild(handle);
        listItem.appendChild(label);
        sortableList.appendChild(listItem);
    });

    // Инициализируем Sortable для списка
    if (typeof Sortable !== 'undefined') {
        new Sortable(sortableList, {
            animation: 150,
            handle: '.handle',
            ghostClass: 'bg-light'
        });
    } else {
        console.warn('Sortable.js не загружен, перетаскивание не будет работать');
    }

    // Открываем модальное окно
    try {
        const modalElement = document.getElementById('sortFieldsModal');
        if (!modalElement) {
            console.error('Не найден элемент модального окна с id="sortFieldsModal"');
            return;
        }
        const modal = new bootstrap.Modal(modalElement);
        modal.show();
    } catch (e) {
        console.error('Ошибка при открытии модального окна:', e);
        alert('Ошибка при открытии окна сортировки полей');
    }
}

/**
 * Сохранение порядка полей
 */
function saveFieldOrder() {
    // Получаем новый порядок полей из списка
    const sortedItems = document.querySelectorAll('#sortableFields li');

    // Создаем скрытое поле для сохранения порядка полей при отправке формы
    let orderInput = document.getElementById('fieldsOrder');
    if (!orderInput) {
        orderInput = document.createElement('input');
        orderInput.type = 'hidden';
        orderInput.id = 'fieldsOrder';
        orderInput.name = 'fieldsOrder';
        document.getElementById('exportForm').appendChild(orderInput);
    }

    // Сохраняем порядок полей как JSON
    const fieldOrder = Array.from(sortedItems).map(item => item.dataset.field);
    orderInput.value = JSON.stringify(fieldOrder);

    // Закрываем модальное окно
    const modal = bootstrap.Modal.getInstance(document.getElementById('sortFieldsModal'));
    modal.hide();
}

/**
 * Вспомогательная функция для получения ID чекбокса по значению поля
 */
function getCheckboxId(fieldValue) {
    if (!fieldValue) return null;

    // Сначала пробуем найти по value
    const checkbox = Array.from(document.querySelectorAll('input[type="checkbox"][name="fields"], input[type="checkbox"][name="fields[]"]'))
        .find(input => input.value === fieldValue);

    if (checkbox) return checkbox.id;

    // Если не нашли, пробуем создать ID в разных форматах, которые могут использоваться
    const fieldKey = fieldValue.replace(/\./g, '_');
    const possibleIds = [
        `field_${fieldKey}`,
        `field_main_${fieldKey}`,
        `field_${fieldKey.split('_')[0]}_${fieldKey}`
    ];

    for (const id of possibleIds) {
        if (document.getElementById(id)) {
            return id;
        }
    }

    return null;
}