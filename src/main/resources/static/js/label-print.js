(() => {
    const form = document.getElementById('label-print-form');
    if (!form) return;
    const connect = document.getElementById('find-printers');
    const print = document.getElementById('print-label');
    const printers = document.getElementById('label-printer');
    const status = document.getElementById('label-status');
    let busy = false;
    const qzConnect = async () => {
        if (!window.qz) throw new Error('Не удалось загрузить библиотеку QZ Tray. Проверьте подключение к интернету и обновите страницу.');
        if (!qz.websocket.isActive()) await qz.websocket.connect();
    };
    connect.addEventListener('click', async () => {
        if (busy) return;
        busy = true; connect.disabled = true; print.disabled = true;
        try {
            status.textContent = 'Подключаемся к QZ Tray…';
            await qzConnect();
            const found = await qz.printers.find();
            printers.replaceChildren(new Option('Выберите принтер', ''));
            found.forEach(name => printers.add(new Option(name, name)));
            printers.value = found.find(name => /TDP.?225/i.test(name)) || '';
            print.disabled = !found.length;
            status.textContent = found.length ? 'Выберите принтер и проверьте параметры этикеток.' : 'Принтеры не найдены. Добавьте принтер в системе.';
        } catch (error) { status.textContent = 'Подключение не выполнено: ' + (error.message || error); }
        finally { busy = false; connect.disabled = false; }
    });
    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (busy || !form.reportValidity()) return;
        busy = true; print.disabled = true; connect.disabled = true;
        try {
            const copies = document.getElementById('label-copies').value;
            const gap = document.getElementById('label-gap').value;
            const response = await fetch(form.dataset.jobUrl + '?' + new URLSearchParams({ copies, gap }), { headers: { Accept: 'application/json' } });
            if (!response.ok || !response.headers.get('content-type')?.includes('application/json')) throw new Error('Не удалось получить этикетку. Проверьте сеанс и обновите страницу.');
            const job = await response.json();
            await qzConnect();
            await qz.print(qz.configs.create(printers.value, { forceRaw: true }), [{ type: 'raw', format: 'command', flavor: 'base64', data: job.data }]);
            status.textContent = 'Задание передано в QZ Tray. Проверьте результат на принтере.';
        } catch (error) { status.textContent = 'Печать не выполнена: ' + (error.message || error) + '. Перед повтором проверьте очередь принтера.'; }
        finally { busy = false; print.disabled = !printers.options.length || !printers.value; connect.disabled = false; }
    });
    printers.addEventListener('change', () => { if (!busy) print.disabled = !printers.value; });
})();
