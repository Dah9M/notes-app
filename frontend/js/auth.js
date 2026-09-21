async function register(username, password) {
  const user = await apiFetch("/api/auth/register", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });
  setSession(user);
}

async function login(username, password) {
  const user = await apiFetch("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });
  setSession(user);
}

function logout() {
  clearSession();
  window.location.href = "login.html";
}
