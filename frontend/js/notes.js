let editingNoteId = null;

function tagsToArray(input) {
  return input
    .split(",")
    .map((t) => t.trim())
    .filter((t) => t.length > 0);
}

function renderNotes(notes) {
  const list = document.getElementById("notes-list");
  list.innerHTML = "";

  if (notes.length === 0) {
    list.innerHTML = '<p class="empty">Заметок пока нет</p>';
    return;
  }

  notes.forEach((note) => {
    const card = document.createElement("div");
    card.className = "note-card";

    const tagsHtml = (note.tags || [])
      .map((t) => `<span class="tag">${escapeHtml(t)}</span>`)
      .join("");

    card.innerHTML = `
      <h3>${escapeHtml(note.title)}</h3>
      <p>${escapeHtml(note.content || "")}</p>
      <div class="tags">${tagsHtml}</div>
      <div class="note-actions">
        <button data-action="edit" class="secondary">Редактировать</button>
        <button data-action="delete" class="danger">Удалить</button>
      </div>
    `;

    card.querySelector('[data-action="edit"]').addEventListener("click", () => startEdit(note));
    card.querySelector('[data-action="delete"]').addEventListener("click", () => removeNote(note.id));

    list.appendChild(card);
  });
}

function escapeHtml(str) {
  const div = document.createElement("div");
  div.textContent = str;
  return div.innerHTML;
}

async function loadNotes() {
  const tagFilter = document.getElementById("tag-filter").value.trim();
  const query = tagFilter ? "?tag=" + encodeURIComponent(tagFilter) : "";
  const notes = await apiFetch("/api/notes" + query);
  renderNotes(notes);
}

function startEdit(note) {
  editingNoteId = note.id;
  document.getElementById("form-title").value = note.title;
  document.getElementById("form-content").value = note.content || "";
  document.getElementById("form-tags").value = (note.tags || []).join(", ");
  document.getElementById("form-submit").textContent = "Сохранить";
  document.getElementById("form-cancel").hidden = false;
}

function resetForm() {
  editingNoteId = null;
  document.getElementById("note-form").reset();
  document.getElementById("form-submit").textContent = "Создать заметку";
  document.getElementById("form-cancel").hidden = true;
}

async function submitNoteForm(event) {
  event.preventDefault();
  const title = document.getElementById("form-title").value.trim();
  const content = document.getElementById("form-content").value.trim();
  const tags = tagsToArray(document.getElementById("form-tags").value);
  const errorEl = document.getElementById("form-error");
  errorEl.textContent = "";

  const payload = { title, content, tags };

  try {
    if (editingNoteId) {
      await apiFetch("/api/notes/" + editingNoteId, {
        method: "PUT",
        body: JSON.stringify(payload),
      });
    } else {
      await apiFetch("/api/notes", {
        method: "POST",
        body: JSON.stringify(payload),
      });
    }
    resetForm();
    await loadNotes();
  } catch (e) {
    errorEl.textContent = e.message;
  }
}

async function removeNote(id) {
  if (!confirm("Удалить заметку?")) return;
  await apiFetch("/api/notes/" + id, { method: "DELETE" });
  if (editingNoteId === id) resetForm();
  await loadNotes();
}

document.addEventListener("DOMContentLoaded", () => {
  requireAuth();

  document.getElementById("current-username").textContent = getUsername();
  document.getElementById("logout-btn").addEventListener("click", logout);
  document.getElementById("note-form").addEventListener("submit", submitNoteForm);
  document.getElementById("form-cancel").addEventListener("click", resetForm);
  document.getElementById("filter-btn").addEventListener("click", loadNotes);
  document.getElementById("filter-clear").addEventListener("click", () => {
    document.getElementById("tag-filter").value = "";
    loadNotes();
  });

  loadNotes().catch((e) => console.error(e));
});
