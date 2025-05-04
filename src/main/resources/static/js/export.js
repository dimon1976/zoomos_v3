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
    // Проверяем, загружен ли Sortable.js
    if (typeof Sortable === 'undefined') {
        // Динамически загружаем Sortable.js если он не загружен
        const script = document.createElement('script');
        script.src = 'https://cdnjs.cloudflare.com/ajax/libs/Sortable/1.14.0/Sortable.min.js';
        script.onload = enableSortable;
        document.head.appendChild(script);
    } else {
        enableSortable();
    }
}

/**
 * Активирует Sortable.js для списка полей
 */
function enableSortable() {
    // Инициализируем Sortable для списка полей в модальном окне
    if (document.getElementById('sortableFields')) {
        new Sortable(document.getElementById('sortableFields'), {
            handle: '.handle', // Элемент, за который можно перетаскивать
            animation: 150,    // Длительность анимации в мс
            ghostClass: 'bg-light' // Класс для "призрака" при перетаскивании
        });
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
    const tableBody = document.getElementById('headersTableBody');
    if (!tableBody) return;

    tableBody.innerHTML = '';

    // Получаем выбранные поля
    const selectedFields = Array.from(document.querySelectorAll('input[name="fields"]:checked'))
        .map(checkbox => checkbox.value);

    // Для каждого выбранного поля добавляем строку в таблицу
    selectedFields.forEach(field => {
        const row = document.createElement('tr');

        // Ячейка с именем поля
        const fieldCell = document.createElement('td');
        const fieldLabel = document.querySelector(`label[for="${getCheckboxId(field)}"]`);
        fieldCell.textContent = fieldLabel ? fieldLabel.textContent : field;

        // Ячейка с полем ввода для заголовка
        const headerCell = document.createElement('td');
        const headerInput = document.createElement('input');
        headerInput.type = 'text';
        headerInput.className = 'form-control';
        headerInput.dataset.field = field;

        // Получаем текущее значение заголовка
        const headerKey = 'header_' + field.replace('.', '_');
        const currentHeaderInput = document.getElementById(headerKey);
        headerInput.value = currentHeaderInput ? currentHeaderInput.value : fieldLabel.textContent;

        headerCell.appendChild(headerInput);

        row.appendChild(fieldCell);
        row.appendChild(headerCell);
        tableBody.appendChild(row);
    });

    // Открываем модальное окно
    const modal = new bootstrap.Modal(document.getElementById('customizeLabelsModal'));
    modal.show();
}

/**
 * Сохранение настроенных заголовков
 */
function saveHeaders() {
    const inputs = document.querySelectorAll('#headersTableBody input');

    inputs.forEach(input => {
        const field = input.dataset.field;
        const headerKey = 'header_' + field.replace('.', '_');
        const headerInput = document.getElementById(headerKey);

        if (headerInput) {
            headerInput.value = input.value;
        }
    });

    // Закрываем модальное окно
    const modal = bootstrap.Modal.getInstance(document.getElementById('customizeLabelsModal'));
    modal.hide();
}

/**
 * Открытие модального окна для изменения порядка полей
 */
function openSortFieldsModal() {
    const sortableList = document.getElementById('sortableFields');
    if (!sortableList) return;

    sortableList.innerHTML = '';

    // Получаем выбранные поля
    const selectedFields = Array.from(document.querySelectorAll('input[name="fields"]:checked'))
        .map(checkbox => {
            const fieldItem = checkbox.closest('.field-item');
            return {
                value: checkbox.value,
                label: document.querySelector(`label[for="${checkbox.id}"]`).textContent,
                order: fieldItem ? fieldItem.dataset.order : 0
            };
        })
        .sort((a, b) => parseInt(a.order) - parseInt(b.order));

    // Для каждого выбранного поля добавляем элемент в список
    selectedFields.forEach(field => {
        const listItem = document.createElement('li');
        listItem.className = 'list-group-item d-flex align-items-center';
        listItem.dataset.field = field.value;
        listItem.dataset.order = field.order;

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

    // Инициализируем Sortable для перетаскивания элементов
    if (typeof Sortable !== 'undefined') {
        new Sortable(sortableList, {
            handle: '.handle',
            animation: 150,
            ghostClass: 'bg-light'
        });
    }

    // Открываем модальное окно
    const modal = new bootstrap.Modal(document.getElementById('sortFieldsModal'));
    modal.show();
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
    const checkbox = Array.from(document.querySelectorAll('input[name="fields"]'))
        .find(input => input.value === fieldValue);

    return checkbox ? checkbox.id : null;
}