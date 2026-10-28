/* Kết nối WebSocket realtime. Tự động kết nối lại khi mất mạng. */
let _socket = null;
let _reconnectTimer = null;

function connectSocket(token, onEvent, onStatus) {
    // Bắt buộc dùng wss:// nếu link là https:// (qua Cloudflare)
    const wsProtocol = window.location.protocol === 'https:' ? 'wss://' : 'ws://';

    // Bám sát tên miền hiện tại của trình duyệt (ví dụ: xxx.trycloudflare.com)
    const wsHost = window.location.host;

    const wsUrl = wsProtocol + wsHost + '/ws/chat?token=' + encodeURIComponent(token);
    console.log("Đang kết nối WebSocket tới:", wsUrl);

    _socket = new WebSocket(wsUrl);

    _socket.onopen = () => {
        console.log("WebSocket đã mở thành công!");
        onStatus(true);
    };

    _socket.onmessage = (e) => {
        try {
            onEvent(JSON.parse(e.data));
        } catch (err) {
            console.error('WS parse lỗi', err);
        }
    };

    _socket.onclose = () => {
        console.log("WebSocket bị đóng. Đang thử kết nối lại...");
        onStatus(false);
        clearTimeout(_reconnectTimer);
        // Thử kết nối lại sau mỗi 2 giây nếu mất mạng
        _reconnectTimer = setTimeout(() => connectSocket(token, onEvent, onStatus), 2000);
    };

    _socket.onerror = (err) => {
        console.error("Lỗi WebSocket:", err);
        _socket.close();
    };
}