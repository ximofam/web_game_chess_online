const Notification = (() => {
  let stompClient = null;
  let unreadCount = 0;

  const connect = () => {
    const token = Auth.getToken();
    if (!token) return;

    const socket = new SockJS('/ws', null, {
      transports: ['websocket']
    });
    stompClient = Stomp.over(socket);
    stompClient.debug = (msg) => console.log('[STOMP]', msg);

    const headers = {
      Authorization: `Bearer ${token}`
    };

    stompClient.connect(headers, onConnected, onError);
  };

  const onConnected = () => {
    console.log('WS connected');

    // Subscribe kênh riêng của user
    stompClient.subscribe('/user/queue/notifications', (message) => {
      const data = JSON.parse(message.body);
      console.log(data)
      handleNewNotification(data);
    });

    // Load số thông báo chưa đọc từ API khi vào trang
    loadUnreadCount();
  };

  const onError = (error) => {
    console.warn('WS error, reconnect sau 5s', error);
    setTimeout(connect, 5000);
  };

  const loadUnreadCount = async () => {
    try {
      const res = await Auth.fetchWithAuth('/api/notifications/unread-count');
      if (!res || !res.ok) return;
      const data = await res.json();
      updateBadge(data.count ?? data); // tuỳ API trả về
    } catch (e) {
      console.error('Lỗi load notification count', e);
    }
  };

  const handleNewNotification = (data) => {
    unreadCount++;
    updateBadge(unreadCount);

    // Toast nếu có thư viện (tùy chọn)
    if (typeof toastr !== 'undefined') {
      toastr.info(data.message || 'Bạn có thông báo mới');
    }
  };

  const updateBadge = (count) => {
    unreadCount = count;
    const badge = document.getElementById('notif-badge');
    if (!badge) return;

    if (count > 0) {
      badge.textContent = count > 99 ? '99+' : count;
      badge.style.display = 'inline-block';
    } else {
      badge.style.display = 'none';
    }
  };

  // Gọi khi user click vào chuông → đánh dấu đã đọc
  const markAllRead = async () => {
    try {
      await Auth.fetchWithAuth('/api/notifications/read-all', { method: 'PATCH' });
      updateBadge(0);
    } catch (e) {
      console.error(e);
    }
  };

  return { connect, markAllRead };
})();

// Tự khởi động khi DOM sẵn sàng
document.addEventListener('DOMContentLoaded', () => {
  Notification.connect();

  // Gắn event cho nút chuông
  const bellBtn = document.getElementById('bell-btn');
  if (bellBtn) {
    bellBtn.addEventListener('click', Notification.markAllRead);
  }
});