/**
 * Минимальная версия JavaScript для работы с формой экспорта данных
 * src/main/resources/static/js/export.js
 */
document.addEventListener('DOMContentLoaded', function() {
    // Инициализация формы экспорта
    initExportForm();
});

/**
 * Инициализация формы экспорта
 */
function initExportForm() {
    // Переключение параметров формата файла
    const formatSelect = document.getElementById('format');
    if (formatSelect) {
        formatSelect.addEventListener('change', toggleFormatSettings);
        toggleFormatSettings(); // Инициализация при загрузке
    }

    // Сохранение порядка полей перед отправкой формы
    const exportForm = document.getElementById('exportForm');
    if (exportForm) {
        exportForm.addEventListener('submit', function(event) {
            saveFieldOrder();
        });
    }
}

/**
 * Переключает настройки в зависимости от выбранного формата файла
 */
function toggleFormatSettings() {
    const format = document.getElementById('format').value;
    const csvParams = document.getElementById('csvParams');
    const excelParams = document.getElementById('excelParams');

    if (csvParams && excelParams) {
        csvParams.style.display = format === 'csv' ? 'block' : 'none';
        excelParams.style.display = format === 'xlsx' ? 'block' : 'none';
    }
}

/**
 * Сохраняет порядок полей перед отправкой формы
 */
function saveFieldOrder() {
    const selectedFields = Array.from(document.querySelectorAll('input[name="fields"]:checked'))
        .map(checkbox => checkbox.value);

    const orderInput = document.getElementById('fieldsOrder');
    if (orderInput) {
        orderInput.value = JSON.stringify(selectedFields);
    }
}

/**
 * Инициализация сортировки полей в шаблоне
 */
function initSortableFields() {
    const fieldsTable = document.getElementById('selectedFieldsList');
    if (fieldsTable && typeof Sortable !== 'undefined') {
        // Инициализируем сортировку если библиотека Sortable.js подключена
        Sortable.create(fieldsTable, {
            handle: '.drag-handle',
            animation: 150,
            onEnd: function() {
                // После окончания перетаскивания обновляем индексы полей
                updateFieldIndices();
            }
        });
    } else if (fieldsTable && typeof Sortable === 'undefined') {
        // Если библиотека не загружена, загружаем ее
        const script = document.createElement('script');
        script.src = 'https://cdn.jsdelivr.net/npm/sortablejs@1.15.0/Sortable.min.js';
        script.onload = function() {
            initSortableFields();
        };
        document.head.appendChild(script);
    }
}

/**
 * Обновляет индексы полей после перетаскивания
 */
function updateFieldIndices() {
    // Обновляем порядковые номера полей после перетаскивания
    const rows = document.querySelectorAll('#selectedFieldsList tr');

    rows.forEach(function(row, index) {
        // Обновляем номер
        const numCell = row.querySelector('td:first-child span');
        if (numCell) {
            numCell.textContent = (index + 1);
        }

        // Обновляем имена полей для формы
        const inputs = row.querySelectorAll('input[name^="fields["]');
        inputs.forEach(function(input) {
            const name = input.name;
            const newName = name.replace(/fields\[\d+\]/, 'fields[' + index + ']');
            input.name = newName;
        });
    });
}