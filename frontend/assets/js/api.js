const API_BASE = "http://localhost:8080/api";

async function request(path, options = {}) {
    const response = await fetch(`${API_BASE}${path}`, {
        headers: {
            "Content-Type": "application/json",
            ...(options.headers || {})
        },
        ...options
    });
    return response.json();
}

function formToJson(form) {
    return Object.fromEntries(new FormData(form).entries());
}

// Session helpers using localStorage to store a non-sensitive UserView object
function setCurrentUser(user) {
    try {
        localStorage.setItem('currentUser', JSON.stringify(user));
        window.currentUser = user;
    } catch (e) {
        console.error('setCurrentUser error', e);
    }
}

function getCurrentUser() {
    if (window.currentUser) return window.currentUser;
    try {
        const s = localStorage.getItem('currentUser');
        if (!s) return null;
        const user = JSON.parse(s);
        window.currentUser = user;
        return user;
    } catch (e) {
        console.error('getCurrentUser error', e);
        return null;
    }
}

function clearCurrentUser() {
    try {
        localStorage.removeItem('currentUser');
        window.currentUser = null;
    } catch (e) {
        console.error('clearCurrentUser error', e);
    }
}

// expose helpers globally for other scripts to use
window.request = request;
window.formToJson = formToJson;
window.setCurrentUser = setCurrentUser;
window.getCurrentUser = getCurrentUser;
window.clearCurrentUser = clearCurrentUser;
