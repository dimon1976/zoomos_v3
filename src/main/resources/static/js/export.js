/**
 * JavaScript для работы с формой экспорта данных
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

    // Переключение стратегии
    const strategySelect = document.getElementById('strategyId');
    if (strategySelect) {
        strategySelect.addEventListener('change', toggleStrategySettings);
        toggleStrategySettings(); // Инициализация при загрузке
    }

    // Сохранение как шаблон
    const saveAsTemplateCheckbox = document.getElementById('saveAsTemplate');
    const templateNameGroup = document.getElementById('templateNameGroup');
    if (saveAsTemplateCheckbox && templateNameGroup) {
        saveAsTemplateCheckbox.addEventListener('change', function() {
            templateNameGroup.style.display = this.checked ? 'block' : 'none';
        });
    }

    // Кнопки выбора/снятия выбора полей
    const selectAllBtn = document.getElementById('selectAllBtn');
    if (selectAllBtn) {
        selectAllBtn.addEventListener('click', selectAllFields);
    }

    const deselectAllBtn = document.getElementById('deselectAllBtn');
    if (deselectAllBtn) {
        deselectAllBtn.addEventListener('click', deselectAllFields);
    }

    // Обработчик отправки формы
    const exportForm = document.getElementById('exportForm');
    if (exportForm) {
        exportForm.addEventListener('submit', function(event) {
            // Сохраняем порядок полей перед отправкой
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
 * Переключает настройки в зависимости от выбранной стратегии
 */
function toggleStrategySettings() {
    const strategyId = document.getElementById('strategyId').value;
    const strategyParams = document.getElementById('strategyParams');

    if (strategyParams) {
        strategyParams.style.display = strategyId && strategyId !== '' ? 'block' : 'none';
    }
}

/**
 * Выбирает все поля для экспорта
 */
function selectAllFields() {
    document.querySelectorAll('input[name="fields"]').forEach(function(checkbox) {
        checkbox.checked = true;
    });
}

/**
 * Снимает выбор со всех полей
 */
function deselectAllFields() {
    document.querySelectorAll('input[name="fields"]').forEach(function(checkbox) {
        checkbox.checked = false;
    });
}

/**
 * Загружает выбранный шаблон
 */
function loadTemplate() {
    const templateId = document.getElementById('templateId').value;
    const entityType = document.querySelector('input[name="entityType"]').value;

    if (!templateId) {
        // Если шаблон не выбран, обновляем страницу без шаблона
        window.location.href = window.location.pathname + '?entityType=' + entityType;
        return;
    }

    // Перезагружаем страницу с выбранным шаблоном
    window.location.href = window.location.pathname + '?entityType=' + entityType + '&templateId=' + templateId;
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
 * Возвращает отображаемое имя поля по умолчанию
 */
function getDefaultDisplayName(field) {
    // Отделяем префикс сущности, если есть
    const dotIndex = field.lastIndexOf('.');
    const baseName = dotIndex > 0 ? field.substring(dotIndex + 1) : field;

    // Преобразуем camelCase в нормальный текст
    return baseName
        .replace(/([A-Z])/g, ' $1')
        .replace(/^./, str => str.toUpperCase())
        .trim();
}