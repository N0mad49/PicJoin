/**
 * android-bridge.js — Capacitor/Android WebView 适配
 */
(function () {
    var isNative = window.Capacitor && window.Capacitor.isNative;
    if (!isNative) return;

    // ── 调试横幅 ──
    var banner = document.createElement('div');
    banner.id = 'android-bridge-status';
    banner.style.cssText = 'position:fixed;top:0;left:0;right:0;z-index:99999;padding:6px 12px;font-size:13px;text-align:center;color:#fff;';

    function setBanner(text, bg) {
        banner.textContent = text;
        banner.style.background = bg || '#333';
        if (!banner.parentNode) document.body.appendChild(banner);
    }
    setBanner('Android Bridge 已加载', '#29499d');

    if (!window.NativeBridge) {
        setBanner('错误: NativeBridge 不可用', '#c00');
        return;
    }
    setBanner('NativeBridge 已连接', '#1a6b3c');

    /* ========== 文件选择 ========== */
    var pendingId = 0;

    window.__nativeFileResult = function (callbackId, files) {
        var cb = window['__fcb_' + callbackId];
        if (cb) { cb(files); delete window['__fcb_' + callbackId]; }
    };

    function openPicker() {
        var id = 'p' + (++pendingId);
        return new Promise(function (resolve) {
            window['__fcb_' + id] = resolve;
            // 超时保护
            var timer = setTimeout(function () {
                delete window['__fcb_' + id];
                resolve(null);
                setBanner('文件选择超时', '#c00');
            }, 120000);
            // 包装 resolve 以清除超时
            var origResolve = resolve;
            window['__fcb_' + id] = function (files) {
                clearTimeout(timer);
                origResolve(files);
            };
            setBanner('正在打开文件选择器...', '#e68a00');
            NativeBridge.pickImages(id);
        });
    }

    async function loadImages(files) {
        if (!files || files.length === 0) {
            toggleLoading(false);
            return;
        }
        setBanner('正在加载 ' + files.length + ' 张图片...', '#29499d');

        var loaded = [];
        for (var i = 0; i < files.length; i++) {
            var img = new Image();
            await new Promise(function (resolve, reject) {
                img.onload = resolve;
                img.onerror = function () { setBanner('图片加载失败: ' + (files[i].name || ''), '#c00'); reject(); };
                img.src = files[i].data;
            });
            loaded.push(img);
        }

        var wrapped = [];
        for (var i = 0; i < loaded.length; i++) {
            var thumb = null;
            try {
                if (window.createImageBitmap) {
                    thumb = await createImageBitmap(loaded[i], 0, 0, loaded[i].width, loaded[i].height, {
                        resizeWidth: 80, resizeHeight: 80, resizeQuality: 'medium'
                    });
                }
            } catch (_) {}
            if (!thumb) {
                var tc = document.createElement('canvas');
                tc.width = 80; tc.height = 80;
                var tctx = tc.getContext('2d');
                var s = Math.min(loaded[i].width, loaded[i].height);
                tctx.drawImage(loaded[i], (loaded[i].width - s) / 2, (loaded[i].height - s) / 2, s, s, 0, 0, 80, 80);
                thumb = tc;
            }
            wrapped.push({
                img: loaded[i],
                thumbnail: thumb,
                width: loaded[i].width,
                height: loaded[i].height,
                edit: { sat: 100, bri: 0, con: 100 },
                free: { x: 0, y: 0, w: 0, h: 0 }
            });
            await new Promise(function (r) { return setTimeout(r, 10); });
        }
        return wrapped;
    }

    async function nativeHandleFiles(replace) {
        toggleLoading(true);
        var files = await openPicker();
        if (!files) { toggleLoading(false); return; }

        var wrapped = await loadImages(files);
        if (!wrapped) { toggleLoading(false); return; }

        if (replace) {
            originalImageObjects = wrapped;
        } else {
            originalImageObjects = [].concat(originalImageObjects, wrapped);
        }

        recalculateDimensions();
        inputs.imgCount.innerText = originalImageObjects.length;
        renderImageList();
        generateStitchedBase();

        if (replace) {
            var home = document.getElementById('home-screen');
            var editor = document.getElementById('editor-screen');
            if (home) home.classList.remove('active');
            if (editor) editor.classList.add('active');
        }

        requestRender();
        toggleLoading(false);
        setBanner('已加载 ' + wrapped.length + ' 张图片', '#1a6b3c');
        setTimeout(function () { if (banner.parentNode) banner.parentNode.removeChild(banner); }, 3000);
    }

    /* ========== 替换按钮 ========== */
    function replaceButton(id, handler) {
        var btn = document.getElementById(id);
        if (!btn) return null;
        var clone = btn.cloneNode(true);
        btn.parentNode.replaceChild(clone, btn);
        clone.addEventListener('click', handler);
        return clone;
    }

    // 等待 DOM 就绪后替换按钮
    var check = setInterval(function () {
        if (document.getElementById('btn-select-images') && document.getElementById('btn-add-images')) {
            clearInterval(check);
            replaceButton('btn-select-images', function () { nativeHandleFiles(true); });
            replaceButton('btn-add-images', function () { nativeHandleFiles(false); });
            replaceButton('btn-back-home', function (e) { e.preventDefault(); nativeHandleFiles(true); });
            var customBg = document.getElementById('btn-select-custom-bg');
            if (customBg) {
                replaceButton('btn-select-custom-bg', function () { nativeHandleFiles(false); });
            }
            setBanner('按钮已替换就绪', '#1a6b3c');
        }
    }, 50);

    /* ========== 保存照片 ========== */
    var saveCheck = setInterval(function () {
        if (window.handleSave && document.getElementById('btn-save')) {
            clearInterval(saveCheck);
            var saveBtn = document.getElementById('btn-save');
            if (saveBtn) {
                var clone5 = saveBtn.cloneNode(true);
                saveBtn.parentNode.replaceChild(clone5, saveBtn);
                clone5.addEventListener('click', async function () {
                    if (!selectedImages.length) return;
                    toggleLoading(true);
                    await new Promise(function (r) { return setTimeout(r, 50); });
                    renderPreview(true);
                    var canvas = inputs.previewCanvas;
                    var quality = Math.max(0.1, Math.min(1.0, appState.exportQuality / 100));
                    var dataUrl = canvas.toDataURL('image/jpeg', quality);
                    var now = new Date();
                    var ts = now.getFullYear() + String(now.getMonth() + 1).padStart(2, '0') + String(now.getDate()).padStart(2, '0') + '_' + String(now.getHours()).padStart(2, '0') + String(now.getMinutes()).padStart(2, '0');
                    var base64 = dataUrl.split(',')[1];
                    var ok = NativeBridge.saveImage(base64, 'PinPhotograph-' + ts + '.jpg');
                    showToast(ok ? '已保存到相册' : '保存失败', !ok);
                    requestRender();
                    toggleLoading(false);
                });
            }
        }
    }, 50);
})();
