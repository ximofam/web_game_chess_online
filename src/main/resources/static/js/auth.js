const Auth = (() => {
  const TOKEN_KEY = 'access_token';
  const REFRESH_KEY = 'refresh_token';

  const getToken = () => localStorage.getItem(TOKEN_KEY);
  const getRefreshToken = () => localStorage.getItem(REFRESH_KEY);

  const setTokens = ({ accessToken, refreshToken }) => {
    localStorage.setItem(TOKEN_KEY, accessToken);
    if (refreshToken) localStorage.setItem(REFRESH_KEY, refreshToken);
  };

  const clearTokens = () => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_KEY);
  };

  // Gọi API kèm Bearer, tự refresh nếu 401
  const fetchWithAuth = async (url, options = {}) => {
    const token = getToken();
    const opts = {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...(options.headers || {}),
        'Authorization': `Bearer ${token}`
      }
    };

    let res = await fetch(url, opts);

    // Token hết hạn → thử refresh
    if (res.status === 401) {
      const refreshed = await tryRefresh();
      if (!refreshed) {
        clearTokens();
        window.location.href = '/admin/login';
        return;
      }
      opts.headers['Authorization'] = `Bearer ${getToken()}`;
      res = await fetch(url, opts);
    }

    return res;
  };

  const tryRefresh = async () => {
    const refreshToken = getRefreshToken();
    if (!refreshToken) return false;
    try {
      const res = await fetch('/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken })
      });
      if (!res.ok) return false;
      const data = await res.json();
      setTokens(data);
      return true;
    } catch {
      return false;
    }
  };

  return { getToken, setTokens, clearTokens, fetchWithAuth };
})();