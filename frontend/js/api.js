function getUserId() {
  return localStorage.getItem("userId");
}

function getUsername() {
  return localStorage.getItem("username");
}

function setSession(user) {
  localStorage.setItem("userId", user.id);
  localStorage.setItem("username", user.username);
}

function clearSession() {
  localStorage.removeItem("userId");
  localStorage.removeItem("username");
}

function requireAuth() {
  if (!getUserId()) {
    window.location.href = "login.html";
  }
}

async function apiFetch(path, options) {
  options = options || {};
  const headers = Object.assign(
    { "Content-Type": "application/json" },
    options.headers || {}
  );
  const userId = getUserId();
  if (userId) {
    headers["X-User-Id"] = userId;
  }

  const response = await fetch(window.API_BASE_URL + path, {
    ...options,
    headers,
  });

  if (response.status === 401) {
    clearSession();
    if (!window.location.pathname.endsWith("login.html")) {
      window.location.href = "login.html";
    }
    throw new Error("Не авторизован");
  }

  if (!response.ok) {
    let message = response.statusText;
    try {
      const body = await response.json();
      message = body.message || message;
    } catch (e) {
      // тело не JSON — оставляем statusText
    }
    throw new Error(message);
  }

  if (response.status === 204) {
    return null;
  }
  return response.json();
}
