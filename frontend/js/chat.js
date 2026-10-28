/* =====================================================================
- chat.js - Khung chat + Phân trang Cassandra + Menu 3 chấm + KẾT BẠN UID
- ===================================================================== */

const auth = getAuth();
if (!auth) { location.href = 'index.html'; }

const $ = id => document.getElementById(id);
const state = {
    convs: [], currentId: null, nextPageToken: null, loadingOlder: false,
    reachedStart: false, pagesLoaded: 0, loadedIds: new Set(), lastQueryTime: 0
};

/* ---------- Tiện ích ---------- */
function fmtTime(iso) {
    const d = new Date(iso), now = new Date();
    const hm = d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
    if (d.toDateString() === now.toDateString()) return hm;
    return d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' }) + ' ' + hm;
}

const getScrollBox = () => $('messages').parentElement;
function isNearBottom() { return getScrollBox().scrollHeight - getScrollBox().scrollTop - getScrollBox().clientHeight < 120; }
function scrollToBottom() { getScrollBox().scrollTop = getScrollBox().scrollHeight; }

/* ---------- Load danh sách nhóm ---------- */
async function loadConversations() {
    state.convs = await apiFetch('/api/conversations');
    renderConversations();
}

function renderConversations() {
    const ul = $('convList');
    ul.innerHTML = '';
    for (const c of state.convs) {
        const li = document.createElement('li');
        li.className = 'conv-item' + (c.conversationId === state.currentId ? ' active' : '');
        li.dataset.id = c.conversationId;

        const top = document.createElement('div');
        top.className = 'conv-top';
        top.innerHTML = `<span class="conv-name">${c.name}</span><span class="conv-time">${fmtTime(c.lastUpdated)}</span>`;

        const last = document.createElement('div');
        last.className = 'conv-last';
        last.textContent = c.lastMessage || 'Chưa có tin nhắn';

        li.append(top, last);
        li.onclick = () => {
            openConversation(c.conversationId);
            const app = document.querySelector('.app');
            if(app) app.classList.add('mobile-chat-active');
        };
        ul.appendChild(li);
    }
}

/* ---------- Vẽ Bong Bóng Tin Nhắn ---------- */
function buildMessageEl(m) {
    const mine = m.senderId === auth.userId;
    const row = document.createElement('div');
    row.className = 'msg-row ' + (mine ? 'mine' : 'other') + (m.deleted ? ' is-deleted' : '');
    row.dataset.id = m.messageId;

    const bubble = document.createElement('div');
    bubble.className = 'msg ' + (mine ? 'mine' : 'other');

    if (!mine) {
        const s = document.createElement('div');
        s.className = 'sender';
        s.textContent = m.senderName;
        bubble.appendChild(s);
    }
    const t = document.createElement('div');
    t.className = 'text';
    t.textContent = m.deleted ? 'Tin nhắn này đã bị xóa' : m.content;
    bubble.appendChild(t);

    const meta = document.createElement('div');
    meta.className = 'meta';
    meta.textContent = fmtTime(m.createdAt);
    bubble.appendChild(meta);

    const actionsWrapper = document.createElement('div');
    actionsWrapper.className = 'msg-actions-container';

    if (m.deleted) {
        actionsWrapper.style.display = 'none';
    } else {
        const actionBtn = document.createElement('button');
        actionBtn.className = 'msg-action-btn';
        actionBtn.innerHTML = '<i data-lucide="more-vertical" class="w-4 h-4"></i>';

        const dropdown = document.createElement('div');
        dropdown.className = 'msg-dropdown';

        const btnReply = document.createElement('button');
        btnReply.className = 'dropdown-item';
        btnReply.innerHTML = '<i data-lucide="reply" class="w-3.5 h-3.5"></i> Nhắc lại';
        btnReply.onclick = (e) => { e.stopPropagation(); dropdown.classList.remove('show'); replyMessage(m.senderName, m.content); };

        const btnForward = document.createElement('button');
        btnForward.className = 'dropdown-item';
        btnForward.innerHTML = '<i data-lucide="forward" class="w-3.5 h-3.5"></i> Chuyển tiếp';
        btnForward.onclick = (e) => { e.stopPropagation(); dropdown.classList.remove('show'); openForwardDialog(m.content); };

        dropdown.appendChild(btnReply);
        dropdown.appendChild(btnForward);

        if (mine) {
            const btnDelete = document.createElement('button');
            btnDelete.className = 'dropdown-item text-red-400';
            btnDelete.innerHTML = '<i data-lucide="trash-2" class="w-3.5 h-3.5"></i> Xóa';
            btnDelete.onclick = (e) => { e.stopPropagation(); dropdown.classList.remove('show'); deleteMessage(m.messageId); };
            dropdown.appendChild(btnDelete);
        }

        actionBtn.onclick = (e) => {
            e.stopPropagation();
            document.querySelectorAll('.msg-dropdown.show').forEach(el => { if (el !== dropdown) el.classList.remove('show'); });
            dropdown.classList.toggle('show');
        };
        actionsWrapper.appendChild(actionBtn);
        actionsWrapper.appendChild(dropdown);
    }
    row.appendChild(bubble);
    row.appendChild(actionsWrapper);
    return row;
}

document.addEventListener('click', () => {
    document.querySelectorAll('.msg-dropdown.show').forEach(el => el.classList.remove('show'));
});

/* ---------- Tính năng Nhắc lại & Chuyển tiếp ---------- */
function replyMessage(senderName, content) {
    const input = $('msgInput');
    const quote = content.length > 50 ? content.substring(0, 50) + '...' : content;
    input.value = `> Đang trả lời ${senderName}: "${quote}"\n`;
    input.focus();
}

let pendingForwardContent = "";
function openForwardDialog(content) {
    pendingForwardContent = content;
    const list = $('forwardConvList');
    list.innerHTML = '';
    for (const c of state.convs) {
        const btn = document.createElement('button');
        btn.className = 'w-full text-left p-3 hover:bg-slate-800 rounded-xl transition flex items-center justify-between group';
        btn.innerHTML = `<span class="font-medium text-sm text-slate-200">${c.name}</span><span class="text-xs bg-brand-600 text-white px-3 py-1 rounded-full opacity-0 group-hover:opacity-100 transition shadow">Gửi</span>`;
        btn.onclick = () => executeForward(c.conversationId);
        list.appendChild(btn);
    }
    $('forwardDialog').showModal();
}

async function executeForward(targetConvId) {
    if (!pendingForwardContent) return;
    try {
        await apiFetch('/api/messages', { method: 'POST', body: JSON.stringify({ conversationId: targetConvId, senderId: auth.userId, content: pendingForwardContent }) });
        $('forwardDialog').close(); alert('Đã chuyển tiếp tin nhắn thành công!');
    } catch (e) { alert('Chuyển tiếp thất bại: ' + e.message); }
}
if ($('btnForwardCancel')) $('btnForwardCancel').onclick = () => $('forwardDialog').close();

/* ---------- Tải phòng chat & Cuộn trang ---------- */
async function openConversation(id) {
    state.currentId = id; state.nextPageToken = null; state.reachedStart = false;
    state.pagesLoaded = 0; state.loadedIds = new Set();
    $('messages').innerHTML = ''; $('metrics').classList.remove('show');
    const conv = state.convs.find(c => c.conversationId === id);
    $('chatTitle').textContent = conv ? conv.name : '';
    $('msgInput').disabled = false; $('btnSend').disabled = false;
    renderConversations();

    const data = await apiFetch(`/api/conversations/${id}/messages`);
    if (state.currentId !== id) return;
    const box = $('messages');
    for (const m of [...data.messages].reverse()) {
        state.loadedIds.add(m.messageId); box.appendChild(buildMessageEl(m));
    }
    state.nextPageToken = data.nextPageToken; state.reachedStart = !data.hasMore;
    state.pagesLoaded = 1; if (state.reachedStart) addHistoryStartMarker();
    updateMetricsUI(data.queryTimeMs);
    scrollToBottom();
    if (window.lucide) window.lucide.createIcons();
}

async function loadOlder() {
    if (state.loadingOlder || state.reachedStart || !state.nextPageToken || !state.currentId) return;
    state.loadingOlder = true; $('loadingOlder').classList.remove('hidden');
    const convId = state.currentId; const scrollBox = getScrollBox();
    try {
        const url = `/api/conversations/${convId}/messages?pageToken=${encodeURIComponent(state.nextPageToken)}`;
        const data = await apiFetch(url);
        if (state.currentId !== convId) return;

        const prevHeight = scrollBox.scrollHeight; const prevTop = scrollBox.scrollTop;
        const frag = document.createDocumentFragment();
        for (const m of [...data.messages].reverse()) {
            if (state.loadedIds.has(m.messageId)) continue;
            state.loadedIds.add(m.messageId); frag.appendChild(buildMessageEl(m));
        }
        const msgBox = $('messages');
        const marker = msgBox.querySelector('.history-start');
        msgBox.insertBefore(frag, msgBox.firstChild);
        if (marker) marker.remove();

        state.nextPageToken = data.nextPageToken; state.reachedStart = !data.hasMore;
        state.pagesLoaded++; if (state.reachedStart) addHistoryStartMarker();

        scrollBox.scrollTop = scrollBox.scrollHeight - prevHeight + prevTop;
        updateMetricsUI(data.queryTimeMs);
        if (window.lucide) window.lucide.createIcons();
    } catch (e) { console.error(e); } finally { state.loadingOlder = false; $('loadingOlder').classList.add('hidden'); }
}

function addHistoryStartMarker() {
    const box = $('messages');
    if (box.querySelector('.history-start')) return;
    const d = document.createElement('div');
    d.className = 'history-start text-center text-slate-500 text-xs py-3 font-medium';
    d.textContent = '— Đầu cuộc hội thoại —';
    box.insertBefore(d, box.firstChild);
}

function updateMetricsUI(newQueryTimeMs = null) {
    if (newQueryTimeMs !== null) state.lastQueryTime = newQueryTimeMs;
    const el = $('metrics'); const liveMessages = state.loadedIds.size;
    const totalHistory = Math.max(200, liveMessages); const totalPages = Math.ceil(totalHistory / 20);
    el.innerHTML = `
        <span class="badge-partition px-2 py-1 rounded bg-slate-800 border border-slate-700" title="Trang hiện tại / Tổng số trang">Trang ${state.pagesLoaded} / ${totalPages}</span>
        <span class="badge-partition px-2 py-1 rounded bg-slate-800 border border-slate-700 text-emerald-400 font-bold" title="Tin đang hiển thị / Tổng lịch sử">Đã tải ${liveMessages}/${totalHistory} tin</span>
        <span class="badge-partition px-2 py-1 rounded bg-indigo-900/50 border border-indigo-700 text-indigo-300">Cassandra: ${state.lastQueryTime} ms</span>
    `;
    el.classList.add('show');
}

const scrollTarget = $('messages').parentElement;
scrollTarget.addEventListener('scroll', () => { if (scrollTarget.scrollTop === 0) loadOlder(); });
scrollTarget.addEventListener('wheel', (e) => { if (e.deltaY < 0 && scrollTarget.scrollTop === 0) loadOlder(); }, { passive: true });

/* ---------- Gửi & Xóa tin nhắn ---------- */
async function sendMessage() {
    const input = $('msgInput'); const content = input.value.trim();
    if (!content || !state.currentId) return;
    input.value = '';
    try {
        const m = await apiFetch('/api/messages', { method: 'POST', body: JSON.stringify({ conversationId: state.currentId, senderId: auth.userId, content }) });
        appendIncoming(m, true);
    } catch (e) { alert('Gửi thất bại: ' + e.message); input.value = content; }
}

async function deleteMessage(messageId) {
    if (!confirm('Xóa tin nhắn này?')) return;
    try {
        await apiFetch(`/api/messages/${messageId}?conversationId=${state.currentId}`, { method: 'DELETE' });
        markDeleted(messageId);
    } catch (e) { alert('Xóa thất bại: ' + e.message); }
}

function markDeleted(messageId) {
    const row = $('messages').querySelector(`.msg-row[data-id="${messageId}"]`);
    if (!row) return;
    row.classList.add('is-deleted');
    const txt = row.querySelector('.text');
    if (txt) txt.textContent = 'Tin nhắn này đã bị xóa';
    const actions = row.querySelector('.msg-actions-container');
    if (actions) actions.style.display = 'none';
}

function appendIncoming(m, force) {
    if (m.conversationId !== state.currentId || state.loadedIds.has(m.messageId)) return;
    const box = $('messages');
    const stick = force || isNearBottom() || m.senderId === auth.userId;
    state.loadedIds.add(m.messageId);
    box.appendChild(buildMessageEl(m));
    if (stick) scrollToBottom();
    updateMetricsUI();
    if (window.lucide) window.lucide.createIcons();
}

function handleSocketEvent(ev) {
    if (ev.type === 'NEW_MESSAGE') {
        const m = ev.data; appendIncoming(m, false);
        const c = state.convs.find(x => x.conversationId === m.conversationId);
        if (c) { c.lastMessage = m.content; c.lastUpdated = m.createdAt; state.convs.sort((a, b) => new Date(b.lastUpdated) - new Date(a.lastUpdated)); renderConversations(); }
        else { loadConversations(); }
    } else if (ev.type === 'MESSAGE_DELETED') {
        if (ev.data.conversationId === state.currentId) markDeleted(ev.data.messageId);
    } else if (ev.type === 'CONVERSATION_CREATED') { loadConversations(); }
}

/* =====================================================================
   TÍNH NĂNG QUẢN LÝ BẠN BÈ BẰNG UID
   ===================================================================== */
const fTabs = { search: $('paneSearchFriend'), reqs: $('paneFriendRequests'), list: $('paneFriendList') };
const fBtns = { search: $('tabSearchFriend'), reqs: $('tabFriendRequests'), list: $('tabFriendList') };

function switchFriendTab(tabName) {
    Object.values(fTabs).forEach(el => el.classList.add('hidden'));
    Object.values(fBtns).forEach(el => {
        el.classList.remove('bg-brand-600', 'text-white', 'shadow-md');
        el.classList.add('text-slate-400');
    });
    fTabs[tabName].classList.remove('hidden');
    fBtns[tabName].classList.add('bg-brand-600', 'text-white', 'shadow-md');
    fBtns[tabName].classList.remove('text-slate-400');

    if (tabName === 'reqs') loadFriendRequests();
    if (tabName === 'list') loadFriendList();
}

$('tabSearchFriend').onclick = () => switchFriendTab('search');
$('tabFriendRequests').onclick = () => switchFriendTab('reqs');
$('tabFriendList').onclick = () => switchFriendTab('list');

$('btnManageFriends').onclick = async () => {
    switchFriendTab('search');
    $('friendDialog').showModal();
    updateFriendBadge();
    try {
        const myInfo = await apiFetch(`/api/friends/me/${auth.userId}`);
        const display = $('myUidDisplay');
        display.textContent = myInfo.uid || 'Chưa có UID';
        display.onclick = () => {
            navigator.clipboard.writeText(myInfo.uid);
            alert('Đã copy mã UID: ' + myInfo.uid);
        };
    } catch (e) { $('myUidDisplay').textContent = 'Lỗi tải UID'; }
};

$('btnCloseFriend').onclick = () => $('friendDialog').close();

// Tìm UID & Kết bạn
$('btnSearchUid').onclick = async () => {
    const uid = $('searchUidInput').value.trim();
    if (!uid) return;
    const resBox = $('searchResult');
    resBox.innerHTML = '<span class="text-slate-400 text-xs italic">Đang tìm trong Cassandra...</span>';
    resBox.classList.remove('hidden');
    try {
        const user = await apiFetch(`/api/friends/search?uid=${encodeURIComponent(uid)}`);
        if (user.userId === auth.userId) {
            resBox.innerHTML = '<span class="text-rose-400 text-[13px] font-semibold">Bạn không thể tự kết bạn với chính mình!</span>';
            return;
        }
        resBox.innerHTML = `
            <div class="flex items-center gap-3">
                <div class="w-10 h-10 rounded-full bg-gradient-to-br from-indigo-500/20 to-purple-500/20 border border-indigo-500/30 text-indigo-300 flex items-center justify-center font-bold text-sm">
                    ${user.displayName.charAt(0).toUpperCase()}
                </div>
                <div>
                    <div class="text-sm font-bold text-white">${user.displayName}</div>
                    <div class="text-[11px] text-slate-400 font-mono">UID: ${user.uid}</div>
                </div>
            </div>
            <button onclick="sendFriendRequest('${user.userId}')" class="px-3.5 py-2 bg-emerald-500/20 text-emerald-400 hover:bg-emerald-500 hover:text-white border border-emerald-500/50 rounded-xl text-xs font-bold transition">
                Kết bạn
            </button>
        `;
    } catch (e) { resBox.innerHTML = '<span class="text-rose-400 text-[13px] font-semibold">Sai UID hoặc Người dùng không tồn tại!</span>'; }
};

window.sendFriendRequest = async (targetId) => {
    try {
        await apiFetch(`/api/friends/request?senderId=${auth.userId}&receiverId=${targetId}`, { method: 'POST' });
        $('searchResult').innerHTML = '<span class="text-emerald-400 text-xs font-semibold">Đã gửi lời mời! Chờ người đó chấp nhận.</span>';
    } catch(e) { alert('Lỗi: ' + e.message); }
};

// Hiển thị Lời mời
async function loadFriendRequests() {
    const box = $('paneFriendRequests');
    box.innerHTML = '<div class="text-center text-xs text-slate-500 py-6">Đang tải từ Cassandra...</div>';
    try {
        const reqs = await apiFetch(`/api/friends/requests/${auth.userId}`);
        if (reqs.length === 0) { box.innerHTML = '<div class="text-center text-[13px] text-slate-500 py-6 italic">Bạn không có lời mời kết bạn nào.</div>'; return; }
        box.innerHTML = reqs.map(r => `
            <div class="flex items-center justify-between p-3.5 bg-slate-800/40 rounded-xl border border-slate-700/50 hover:bg-slate-800/80 transition">
                <div class="flex items-center gap-3">
                    <div class="w-9 h-9 rounded-full bg-slate-700 flex items-center justify-center font-bold text-slate-300 text-xs">${r.senderName.charAt(0).toUpperCase()}</div>
                    <div class="flex flex-col"><span class="text-[13.5px] font-bold text-white">${r.senderName}</span><span class="text-[11px] text-slate-400 font-mono">UID: ${r.senderUid}</span></div>
                </div>
                <div class="flex gap-2"><button onclick="acceptFriend('${r.senderId}')" class="px-3.5 py-1.5 bg-brand-600 hover:bg-brand-500 text-white rounded-lg text-xs font-semibold shadow-md transition">Đồng ý</button></div>
            </div>
        `).join('');
    } catch (e) { box.innerHTML = '<div class="text-rose-400 text-xs text-center py-4">Lỗi tải dữ liệu!</div>'; }
}

window.acceptFriend = async (senderId) => {
    try {
        await apiFetch(`/api/friends/accept?receiverId=${auth.userId}&senderId=${senderId}`, { method: 'POST' });
        loadFriendRequests(); updateFriendBadge();
    } catch (e) { alert('Lỗi: ' + e.message); }
};

async function updateFriendBadge() {
    try {
        const reqs = await apiFetch(`/api/friends/requests/${auth.userId}`);
        const modalBadge = $('modalReqBadge'); const sidebarBadge = $('sidebarReqBadge');
        if (reqs.length > 0) {
            modalBadge.textContent = reqs.length; modalBadge.classList.remove('hidden');
            sidebarBadge.textContent = reqs.length; sidebarBadge.classList.remove('hidden');
        } else {
            modalBadge.classList.add('hidden'); sidebarBadge.classList.add('hidden');
        }
    } catch (e) {}
}

async function loadFriendList() {
    const box = $('paneFriendList');
    box.innerHTML = '<div class="text-center text-xs text-slate-500 py-6">Đang tải...</div>';
    try {
        const friends = await apiFetch(`/api/friends/list/${auth.userId}`);
        if (friends.length === 0) { box.innerHTML = '<div class="text-center text-[13px] text-slate-500 py-6 italic">Danh sách bạn bè trống.</div>'; return; }
        box.innerHTML = friends.map(f => `
            <div class="flex items-center justify-between p-3.5 bg-slate-800/40 rounded-xl border border-slate-700/50 group hover:border-slate-600 transition">
                <div class="flex items-center gap-3">
                    <div class="w-9 h-9 rounded-full bg-emerald-500/20 text-emerald-400 flex items-center justify-center font-bold text-sm">${f.friendName.charAt(0).toUpperCase()}</div>
                    <div class="flex flex-col"><span class="text-[13.5px] font-bold text-white">${f.friendName}</span><span class="text-[11px] text-slate-400 font-mono">UID: ${f.friendUid}</span></div>
                </div>
                <button onclick="unfriend('${f.friendId}')" class="px-2.5 py-2 text-slate-500 hover:text-rose-400 hover:bg-rose-500/20 rounded-xl transition opacity-0 group-hover:opacity-100" title="Hủy kết bạn"><i data-lucide="user-minus" class="w-4 h-4"></i></button>
            </div>
        `).join('');
        if (window.lucide) window.lucide.createIcons();
    } catch (e) { box.innerHTML = '<div class="text-rose-400 text-xs text-center py-4">Lỗi tải dữ liệu!</div>'; }
}

window.unfriend = async (friendId) => {
    if (!confirm('Hủy kết bạn? Dữ liệu sẽ bị xóa 2 chiều.')) return;
    try { await apiFetch(`/api/friends/${auth.userId}/${friendId}`, { method: 'DELETE' }); loadFriendList(); } catch (e) { alert('Lỗi: ' + e.message); }
};


/* =====================================================================
   [MỚI] TÍNH NĂNG TẠO NHÓM BẰNG CÁCH CHỌN BẠN BÈ / NHẬP UID
   ===================================================================== */
let selectedGroupMembers = [];

// Hàm vẽ lại danh sách Badge những người đã được chọn
function renderSelectedMembers() {
    const container = $('selectedMembersContainer');
    container.innerHTML = '';
    if (selectedGroupMembers.length === 0) {
        container.innerHTML = '<span class="text-xs text-slate-500 italic flex items-center">Chưa chọn ai...</span>';
        return;
    }
    selectedGroupMembers.forEach(m => {
        const badge = document.createElement('div');
        badge.className = 'flex items-center gap-1.5 px-2.5 py-1 bg-brand-500/20 border border-brand-500/30 text-brand-300 rounded-lg text-[11px] font-semibold';
        badge.innerHTML = `
            ${m.displayName}
            <button type="button" class="hover:text-rose-400 transition ml-1" onclick="removeGroupMember('${m.username}')" title="Bỏ chọn">
                <i data-lucide="x" class="w-3 h-3"></i>
            </button>
        `;
        container.appendChild(badge);
    });
    if (window.lucide) window.lucide.createIcons();
}

// Xóa một người khỏi danh sách chọn
window.removeGroupMember = (username) => {
    selectedGroupMembers = selectedGroupMembers.filter(m => m.username !== username);
    renderSelectedMembers();
};

function showConvError(msg) {
    const err = $('newConvAddError');
    err.textContent = msg;
    err.classList.remove('hidden');
    setTimeout(() => err.classList.add('hidden'), 3000);
}

// Hàm dùng chung để thêm người vào mảng
function addMemberToGroup(userAccount) {
    if (userAccount.userId === auth.userId) {
        showConvError('Không thể tự thêm chính mình!'); return;
    }
    if (selectedGroupMembers.some(m => m.username === userAccount.username)) {
        showConvError('Người này đã được chọn rồi!'); return;
    }
    selectedGroupMembers.push({ username: userAccount.username, displayName: userAccount.displayName });
    $('newConvUidInput').value = '';
    $('newConvAddError').classList.add('hidden');
    renderSelectedMembers();
}

// Thêm bằng cách gõ tay UID
$('btnNewConvAddUid').onclick = async () => {
    const uid = $('newConvUidInput').value.trim();
    if (!uid) return;
    try {
        const user = await apiFetch(`/api/friends/search?uid=${encodeURIComponent(uid)}`);
        addMemberToGroup(user);
    } catch (e) {
        showConvError('UID không tồn tại!');
    }
};

// Nút bấm "Thêm" kế bên tên bạn bè
window.addGroupMemberFromFriend = async (friendId) => {
    try {
        // Gọi API lấy full thông tin để lấy username truyền cho Backend
        const user = await apiFetch(`/api/friends/me/${friendId}`);
        addMemberToGroup(user);
    } catch (e) { showConvError('Lỗi khi thêm!'); }
};

// ---------------- Khởi động Modal Tạo Nhóm ----------------
$('btnNewConv').onclick = async () => {
    $('newConvError').textContent = '';
    $('newConvName').value = '';
    $('newConvUidInput').value = '';

    // Reset mảng thành viên được chọn
    selectedGroupMembers = [];
    renderSelectedMembers();
    $('newConvDialog').showModal();

    // Auto-load danh sách bạn bè vào Modal
    const listContainer = $('newConvFriendList');
    listContainer.innerHTML = '<div class="text-xs text-slate-500 text-center py-3">Đang tải...</div>';

    try {
        const friends = await apiFetch(`/api/friends/list/${auth.userId}`);
        if (friends.length === 0) {
            listContainer.innerHTML = '<div class="text-xs text-slate-500 text-center py-3 italic">Chưa có bạn bè nào để mời.</div>';
        } else {
            listContainer.innerHTML = friends.map(f => `
                <div class="flex items-center justify-between p-2 hover:bg-slate-800/60 rounded-xl transition border border-transparent hover:border-slate-700/50">
                    <div class="flex items-center gap-2.5">
                        <div class="w-8 h-8 rounded-full bg-emerald-500/20 text-emerald-400 flex items-center justify-center font-bold text-xs">
                            ${f.friendName.charAt(0).toUpperCase()}
                        </div>
                        <div class="flex flex-col">
                            <span class="text-[13px] font-bold text-white leading-tight">${f.friendName}</span>
                            <span class="text-[10px] text-slate-400 font-mono">${f.friendUid}</span>
                        </div>
                    </div>
                    <button onclick="addGroupMemberFromFriend('${f.friendId}')" class="px-2.5 py-1.5 bg-slate-800 hover:bg-brand-600 text-slate-300 hover:text-white rounded-lg border border-slate-700 hover:border-brand-500 transition text-[11px] font-bold shadow-sm">
                        Thêm
                    </button>
                </div>
            `).join('');
        }
    } catch (e) {
        listContainer.innerHTML = '<div class="text-xs text-rose-400 text-center py-3">Lỗi tải danh sách bạn bè!</div>';
    }
};

$('btnNewConvCancel').onclick = () => $('newConvDialog').close();

$('btnNewConvOk').onclick = async () => {
    // Trích xuất mảng usernames để gửi cho backend
    const usernames = selectedGroupMembers.map(m => m.username);

    if (usernames.length === 0) {
        $('newConvError').textContent = 'Vui lòng chọn ít nhất 1 thành viên (từ Bạn bè hoặc UID)!';
        return;
    }

    try {
        const c = await apiFetch('/api/conversations', {
            method: 'POST',
            body: JSON.stringify({
                name: $('newConvName').value,
                members: usernames
            })
        });
        $('newConvDialog').close();
        await loadConversations();
        openConversation(c.conversationId);
    } catch (e) {
        $('newConvError').textContent = e.message;
    }
};

/* ---------- Auth & Setup ---------- */
$('btnSend').onclick = sendMessage;
$('msgInput').addEventListener('keydown', e => { if (e.key === 'Enter') sendMessage(); });
$('btnLogout').onclick = logout;

(async function init() {
    if (!auth) return;
    $('meName').textContent = auth.displayName + ' (@' + auth.username + ')';
    connectSocket(auth.token, handleSocketEvent, (online) => {
        const s = $('wsStatus');
        s.innerHTML = online ? '<span class="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span><span>đã kết nối</span>' : '<span class="w-2 h-2 rounded-full bg-rose-500"></span><span>mất mạng</span>';
        s.className = 'me-status text-[11px] font-mono flex items-center gap-1.5 mt-0.5 ' + (online ? 'text-emerald-400' : 'text-rose-400');
    });
    try {
        await loadConversations();
        updateFriendBadge();
    } catch (e) { console.error(e); }
})();

// Bắt sự kiện quay lại Mobile
document.body.addEventListener('click', (e) => {
    const backBtn = e.target.closest('#btnMobileBack');
    if (backBtn) {
        const appContainer = document.querySelector('.app');
        if (appContainer) appContainer.classList.remove('mobile-chat-active');
    }
});