const Notification = (() => {
  let stompClient = null;
  let unreadCount = 0;
  let currentTab = 'all';
  let page = 0;
  const PAGE_SIZE = 20;
  let items = [];

  // ── WebSocket ──────────────────────────────────────────
  const connect = () => {
    const token = Auth.getToken();
    if (!token) return;
    const socket = new SockJS('/ws', null, { transports: ['websocket'] });
    stompClient = Stomp.over(socket);
    stompClient.debug = () => { };
    stompClient.connect({ Authorization: `Bearer ${token}` }, onConnected, onError);
  };

  const onConnected = () => {
    stompClient.subscribe('/user/queue/notifications', (msg) => {
      const n = JSON.parse(msg.body);

      const mapped = {
        id: n.id,
        sender: n.sender?.username || 'Hệ thống',
        initials: (n.sender?.username || 'SY').slice(0, 2).toUpperCase(),
        type: n.type || 'SYSTEM_MESSAGE',
        title: n.title,
        msg: n.message,
        time: formatTime(n.createdAt),
        unread: true,
      };

      items.unshift(mapped);
      unreadCount++;
      updateBadge(unreadCount);
      if (toastr) toastr.info(data.message || 'Bạn có thông báo mới', data.title || '');
      renderList();
    });
    loadUnreadCount();
    loadNotifications(true);
  };

  const onError = () => setTimeout(connect, 5000);

  // ── API calls ──────────────────────────────────────────
  const loadUnreadCount = async () => {
    const res = await Auth.fetchWithAuth('/api/notifications/unread-count');
    if (!res?.ok) return;
    const data = await res.json();
    updateBadge(data.count ?? 0);
  };

  const loadNotifications = async (reset = false) => {
    if (reset) { page = 0; items = []; }
    const res = await Auth.fetchWithAuth(
      `/api/notifications?page=${page}&size=${PAGE_SIZE}&sort=id,desc`
    );
    if (!res?.ok) return;
    const data = await res.json();

    const mapped = (data.content || []).map(n => ({
      id: n.id,
      sender: n.sender?.username || 'Hệ thống',
      initials: (n.sender?.username || 'SY').slice(0, 2).toUpperCase(),
      type: n.type || 'SYSTEM_MESSAGE',
      title: n.title,
      msg: n.message,
      time: formatTime(n.createdAt),
      unread: n.isRead === false,
    }));

    items = reset ? mapped : [...items, ...mapped];
    page++;
    renderList();

    const pageInfo = data.page;
    const isLast = pageInfo.number >= pageInfo.totalPages - 1;
    const loadMoreBtn = document.getElementById('notif-load-more');
    if (loadMoreBtn) loadMoreBtn.style.display = isLast ? 'none' : 'block';
  };

  const markAsRead = async (id) => {
    await Auth.fetchWithAuth(`/api/notifications/${id}/read`, { method: 'PATCH' });
    const item = items.find(n => n.id === id);
    if (item && item.unread) { item.unread = false; unreadCount = Math.max(0, unreadCount - 1); }
    updateBadge(unreadCount);
    renderList();
  };

  const markAllRead = async () => {
    await Auth.fetchWithAuth('/api/notifications/read-all', { method: 'PATCH' });
    items.forEach(n => n.unread = false);
    updateBadge(0);
    renderList();
  };

  const deleteOne = async (id) => {
    await Auth.fetchWithAuth(`/api/notifications/${id}`, { method: 'DELETE' });
    const idx = items.findIndex(n => n.id === id);
    if (idx > -1) {
      if (items[idx].unread) unreadCount = Math.max(0, unreadCount - 1);
      items.splice(idx, 1);
    }
    updateBadge(unreadCount);
    renderList();
  };

  const deleteAll = async () => {
    await Auth.fetchWithAuth('/api/notifications', { method: 'DELETE' });
    items = [];
    updateBadge(0);
    renderList();
  };

  // ── UI ─────────────────────────────────────────────────
  const updateBadge = (count) => {
    unreadCount = count;
    const badge = document.getElementById('notif-badge');
    if (!badge) return;
    badge.textContent = count > 99 ? '99+' : count;
    badge.style.display = count > 0 ? 'inline-block' : 'none';

    const tabSpan = document.getElementById('notif-unread-tab');
    if (tabSpan) tabSpan.textContent = count > 0 ? `(${count})` : '';
  };

  const TYPE_LABELS = { COMMENT: 'Bình luận', LIKE: 'Thích', FOLLOW: 'Theo dõi', MENTION: 'Nhắc đến', SYSTEM_MESSAGE: 'Hệ thống' };

  const formatTime = (instant) => {
    if (!instant) return '';
    const diff = (Date.now() - new Date(instant)) / 1000;
    if (diff < 60) return `${Math.floor(diff)}s`;
    if (diff < 3600) return `${Math.floor(diff / 60)} phút`;
    if (diff < 86400) return `${Math.floor(diff / 3600)} giờ`;
    return `${Math.floor(diff / 86400)} ngày`;
  };

  const renderList = () => {
    const list = document.getElementById('notif-list');
    if (!list) return;
    const visible = currentTab === 'unread' ? items.filter(n => n.unread) : items;

    if (visible.length === 0) {
      list.innerHTML = `<div class="notif-empty"><i class="bi bi-bell-slash fs-3 d-block mb-2"></i>Không có thông báo nào</div>`;
      return;
    }

    list.innerHTML = visible.map(n => `
      <div class="notif-item ${n.unread ? 'unread' : ''}" data-id="${n.id}">
        <div class="notif-avatar">${n.initials}</div>
        <div class="notif-body">
          <span class="notif-time">${n.time}</span>
          <div class="notif-sender">${n.sender}</div>
          <div class="notif-msg">${n.msg}</div>
          <span class="notif-type-badge badge-${n.type}">${TYPE_LABELS[n.type] || n.type}</span>
        </div>
        ${n.unread ? '<div class="unread-dot"></div>' : ''}
        <button class="notif-del-btn" data-del="${n.id}" title="Xóa"><i class="bi bi-x"></i></button>
      </div>
    `).join('');

    list.querySelectorAll('.notif-item').forEach(el => {
      el.addEventListener('click', e => {
        if (e.target.closest('.notif-del-btn')) return;
        markAsRead(+el.dataset.id);
      });
    });
    list.querySelectorAll('.notif-del-btn').forEach(btn => {
      btn.addEventListener('click', e => { e.stopPropagation(); deleteOne(+btn.dataset.del); });
    });
  };

  const togglePanel = () => {
    const modal = document.getElementById('notif-modal');
    if (!modal) return;
    const isOpen = modal.style.display !== 'none';
    modal.style.display = isOpen ? 'none' : 'block';
  };

  // ── Init ───────────────────────────────────────────────
  const init = () => {
    connect();

    const bellBtn = document.getElementById('bell-btn');
    if (bellBtn) bellBtn.addEventListener('click', (e) => { e.stopPropagation(); togglePanel(); });

    document.addEventListener('click', (e) => {
      const modal = document.getElementById('notif-modal');
      if (modal && !modal.contains(e.target) && e.target !== bellBtn) {
        modal.style.display = 'none';
      }
    });

    document.querySelectorAll('.notif-tab').forEach(tab => {
      tab.addEventListener('click', () => {
        document.querySelectorAll('.notif-tab').forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
        currentTab = tab.dataset.tab;
        renderList();
      });
    });

    const markAllBtn = document.getElementById('notif-mark-all');
    if (markAllBtn) markAllBtn.addEventListener('click', markAllRead);

    const delAllBtn = document.getElementById('notif-delete-all');
    if (delAllBtn) delAllBtn.addEventListener('click', deleteAll);

    const loadMoreBtn = document.getElementById('notif-load-more');
    if (loadMoreBtn) loadMoreBtn.addEventListener('click', () => loadNotifications(false));
  };

  return { connect, markAllRead, init };
})();

document.addEventListener('DOMContentLoaded', Notification.init);