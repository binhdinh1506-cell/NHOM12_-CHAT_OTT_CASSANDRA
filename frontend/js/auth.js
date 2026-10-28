/* ===== Dùng chung cho index.html và chat.html ===== */

// Nếu mở trang từ IntelliJ built-in server (cổng 63342) thì gọi API sang cổng 8080.
// Nếu truy cập qua Spring Boot (http://host:8080) thì dùng đường dẫn tương đối.
const API_BASE = (location.port === '63342' || location.protocol === 'file:')
    ? 'http://localhost:8080' : '';

function getAuth() {
    try { return JSON.parse(localStorage.getItem('chat_auth')); } catch (e) { return null; }
}
function saveAuth(a) { localStorage.setItem('chat_auth', JSON.stringify(a)); }
function logout() {
    localStorage.removeItem('chat_auth');
    location.href = 'index.html';
}

/** Gọi REST API, tự gắn Bearer token, tự xử lý 401 và lỗi JSON {error}. */
async function apiFetch(path, options = {}) {
    const auth = getAuth();
    const headers = Object.assign({ 'Content-Type': 'application/json' }, options.headers || {});
    if (auth && auth.token) headers['Authorization'] = 'Bearer ' + auth.token;

    const res = await fetch(API_BASE + path, Object.assign({}, options, { headers }));
    let body = null;
    try { body = await res.json(); } catch (e) { /* không có body */ }

    if (res.status === 401 && !path.startsWith('/api/auth/')) { logout(); throw new Error('Phiên đăng nhập hết hạn'); }
    if (!res.ok) throw new Error((body && body.error) || ('Lỗi HTTP ' + res.status));
    return body;
}

/* ===== Logic riêng của trang đăng nhập ===== */
(function initLoginPage() {
    const btn = document.getElementById('btnSubmit');
    if (!btn) return;                                   // đang ở chat.html
    if (getAuth()) { location.href = 'chat.html'; return; }

    let mode = 'login';
    const $ = id => document.getElementById(id);

    function setMode(m) {
        mode = m;
        $('tabLogin').classList.toggle('active', m === 'login');
        $('tabRegister').classList.toggle('active', m === 'register');
        $('displayName').classList.toggle('hidden', m === 'login');
        btn.textContent = m === 'login' ? 'Đăng nhập' : 'Đăng ký';
        $('authError').textContent = '';
    }
    $('tabLogin').onclick = () => setMode('login');
    $('tabRegister').onclick = () => setMode('register');

    async function submit() {
        $('authError').textContent = '';
        const body = { username: $('username').value, password: $('password').value };
        if (mode === 'register') body.displayName = $('displayName').value;
        try {
            const data = await apiFetch('/api/auth/' + mode, { method: 'POST', body: JSON.stringify(body) });
            saveAuth(data);
            location.href = 'chat.html';
        } catch (e) { $('authError').textContent = e.message; }
    }
    btn.onclick = submit;
    ['username', 'password', 'displayName'].forEach(id =>
        $(id).addEventListener('keydown', e => { if (e.key === 'Enter') submit(); }));
})();
