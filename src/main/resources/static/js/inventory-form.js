(() => {
    const form = document.getElementById('inventory-form');
    if (!form) return;
    const lines = document.getElementById('inventory-lines');
    const template = document.getElementById('inventory-line-template');
    const add = document.getElementById('add-line');
    const warehouse = document.getElementById('warehouseId');
    const filterBatches = () => {
        lines.querySelectorAll('select[data-stock]').forEach(select => {
            select.querySelectorAll('option[data-warehouse]').forEach(option => {
                if (option.dataset.originalDisabled === undefined) option.dataset.originalDisabled = String(option.disabled);
                const wrongWarehouse = option.dataset.warehouse !== warehouse.value;
                option.hidden = wrongWarehouse;
                option.disabled = wrongWarehouse || option.dataset.originalDisabled === 'true';
                if (option.selected && option.disabled) select.value = '';
            });
        });
    };
    const update = () => {
        const rows = [...lines.querySelectorAll('.inventory-line')];
        rows.forEach((row, index) => {
            row.querySelector('legend').textContent = `Позиция ${index + 1}`;
            row.querySelectorAll('[name]').forEach(input => {
                input.name = input.name.replace(/lines\[(?:\d+|__index__)\]/, `lines[${index}]`);
            });
            row.querySelector('.remove-line').disabled = rows.length === 1;
        });
        add.disabled = rows.length >= 100;
        filterBatches();
    };
    add.addEventListener('click', () => {
        if (lines.children.length >= 100) return;
        lines.appendChild(template.content.cloneNode(true));
        update();
    });
    lines.addEventListener('click', event => {
        if (!event.target.matches('.remove-line') || lines.querySelectorAll('.inventory-line').length <= 1) return;
        event.target.closest('.inventory-line').remove();
        update();
    });
    warehouse.addEventListener('change', filterBatches);
    let submitted = false;
    form.addEventListener('submit', event => {
        if (submitted) event.preventDefault();
        else submitted = true;
    });
    const scan = document.getElementById('batch-scan');
    const scanStatus = document.getElementById('scan-status');
    const scanBatch = () => {
        const code = scan.value.trim();
        if (!warehouse.value) { scanStatus.textContent = 'Сначала выберите склад.'; return; }
        if (!/^[0-9]{12}$/.test(code)) { scanStatus.textContent = 'Ожидается внутренний штрихкод партии с этикетки.'; return; }
        filterBatches();
        const option = [...lines.querySelectorAll('option[data-barcode]')].find(o => o.dataset.barcode === code && o.dataset.warehouse === warehouse.value);
        if (!option) { scanStatus.textContent = 'Партия не найдена среди остатков выбранного склада.'; return; }
        if (option.disabled) { scanStatus.textContent = 'Эта партия недоступна для выдачи: проверьте срок годности.'; return; }
        const selects = [...lines.querySelectorAll('select[data-stock]')];
        let select = selects.find(s => s.value === option.value) || selects.find(s => !s.value);
        if (!select) {
            if (lines.querySelectorAll('.inventory-line').length >= 100) { scanStatus.textContent = 'В документе уже 100 строк.'; return; }
            add.click(); select = lines.lastElementChild.querySelector('select[data-stock]');
        }
        select.value = option.value;
        const quantity = select.closest('.inventory-line').querySelector('input[name$=".quantity"]');
        quantity.focus(); quantity.select();
        scan.value = '';
        scanStatus.textContent = 'Партия выбрана. Укажите количество; повторное сканирование не увеличивает его автоматически.';
    };
    if (scan) {
        document.getElementById('scan-batch').addEventListener('click', scanBatch);
        scan.addEventListener('keydown', event => { if (event.key === 'Enter') { event.preventDefault(); scanBatch(); } });
    }
    update();
})();
