document.querySelectorAll('[data-print]').forEach(button => {
    button.addEventListener('click', async () => {
        button.disabled = true;
        try {
            await Promise.all([...document.images].map(image => image.decode()));
            await document.fonts.ready;
            window.print();
        } catch {
            window.alert('Не удалось загрузить изображение для печати. Обновите страницу и повторите попытку.');
        } finally { button.disabled = false; }
    });
});
