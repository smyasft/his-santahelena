/* HIS Clínica Santa Helena · comportamiento común de la interfaz */
(function () {
    'use strict';

    // Confirmación antes de enviar formularios sensibles: <form data-confirmar="¿Seguro?">
    document.addEventListener('submit', function (e) {
        var f = e.target;
        var msg = f.getAttribute('data-confirmar');
        var boton = e.submitter;
        if (boton && boton.getAttribute('data-confirmar')) {
            msg = boton.getAttribute('data-confirmar');
        }
        if (msg && !window.confirm(msg)) {
            e.preventDefault();
        }
    });

    // Recarga automática: <body data-recargar="30">
    var rec = document.querySelector('[data-recargar]');
    if (rec) {
        var seg = parseInt(rec.getAttribute('data-recargar'), 10) || 30;
        setTimeout(function () { window.location.reload(); }, seg * 1000);
    }

    // Menú lateral en celulares
    var bm = document.querySelector('.menu-movil');
    if (bm) {
        bm.addEventListener('click', function () { document.body.classList.toggle('menu-abierto'); });
    }
    var velo = document.querySelector('.velo');
    if (velo) {
        velo.addEventListener('click', function () { document.body.classList.remove('menu-abierto'); });
    }

    // Números que "cuentan" al cargar el tablero: <div data-contar="23 %">
    var reducir = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    document.querySelectorAll('[data-contar]').forEach(function (el) {
        var texto = el.getAttribute('data-contar');
        var m = /^(\d+)(.*)$/.exec(texto);
        if (!m || reducir) { return; }
        var fin = parseInt(m[1], 10), sufijo = m[2], inicio = null, dur = 900;
        function paso(t) {
            if (inicio === null) { inicio = t; }
            var p = Math.min(1, (t - inicio) / dur);
            var e = 1 - Math.pow(1 - p, 3);
            el.textContent = Math.round(fin * e) + sufijo;
            if (p < 1) { requestAnimationFrame(paso); }
        }
        el.textContent = '0' + sufijo;
        requestAnimationFrame(paso);
    });

    // Reloj de la barra de estado
    var reloj = document.getElementById('reloj');
    function tic() {
        if (!reloj) { return; }
        var d = new Date();
        reloj.textContent = d.toLocaleDateString('es-CO', { day: '2-digit', month: 'short' }) + ' · ' + d.toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' });
    }
    tic();
    setInterval(tic, 30000);

    // Autocompletado de catálogos: <input class="autocat" data-tipo="CIE10" list="lista-cie10">
    var temporizador;
    document.addEventListener('input', function (e) {
        var el = e.target;
        if (!el.classList || !el.classList.contains('autocat')) { return; }
        var lista = document.getElementById(el.getAttribute('list'));
        var q = el.value.trim();
        if (!lista || q.length < 2 || q.indexOf(' — ') > 0) { return; }
        clearTimeout(temporizador);
        temporizador = setTimeout(function () {
            fetch((window.HIS_BASE || '') + '/api/catalogo/' + el.getAttribute('data-tipo') + '?q=' + encodeURIComponent(q), { credentials: 'same-origin' })
                .then(function (r) { return r.ok ? r.json() : []; })
                .then(function (datos) {
                    lista.innerHTML = '';
                    datos.forEach(function (d) {
                        var o = document.createElement('option');
                        o.value = d.codigo + ' — ' + d.descripcion;
                        lista.appendChild(o);
                    });
                })
                .catch(function () { /* sin conexión: el servidor valida igual */ });
        }, 220);
    });

    // Mostrar u ocultar bloques según un checkbox: <input type="checkbox" data-muestra="id-bloque">
    function sincronizar(chk) {
        var obj = document.getElementById(chk.getAttribute('data-muestra'));
        if (obj) { obj.style.display = chk.checked ? '' : 'none'; }
    }
    document.querySelectorAll('[data-muestra]').forEach(function (chk) {
        sincronizar(chk);
        chk.addEventListener('change', function () { sincronizar(chk); });
    });

    // Agregar filas de diagnóstico: <button data-clonar="plantilla-dx" data-destino="dx-lista">
    document.addEventListener('click', function (e) {
        var b = e.target.closest('[data-clonar]');
        if (!b) { return; }
        e.preventDefault();
        var tpl = document.getElementById(b.getAttribute('data-clonar'));
        var dest = document.getElementById(b.getAttribute('data-destino'));
        if (tpl && dest) { dest.appendChild(tpl.content.cloneNode(true)); }
    });

    // Imprimir
    document.querySelectorAll('[data-imprimir]').forEach(function (b) {
        b.addEventListener('click', function () { window.print(); });
    });

    // Código QR para configurar el autenticador (librería qrcodejs desde CDN)
    var qr = document.getElementById('qr');
    if (qr && window.QRCode) {
        new window.QRCode(qr, { text: qr.getAttribute('data-uri'), width: 180, height: 180, correctLevel: window.QRCode.CorrectLevel.M });
    }

    // Tiempo de espera en la cola de urgencias: <span class="espera" data-desde="2027-02-01T08:00">
    function esperas() {
        document.querySelectorAll('.espera[data-desde]').forEach(function (s) {
            var desde = new Date(s.getAttribute('data-desde'));
            var min = Math.max(0, Math.floor((Date.now() - desde.getTime()) / 60000));
            var h = Math.floor(min / 60);
            s.textContent = (h > 0 ? h + ' h ' : '') + (min % 60) + ' min';
            var limite = parseInt(s.getAttribute('data-limite') || '0', 10);
            s.classList.toggle('tarde', limite > 0 && min > limite);
        });
    }
    esperas();
    setInterval(esperas, 30000);
})();
