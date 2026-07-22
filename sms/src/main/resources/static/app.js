/*
 * Scholar SMS — Frontend behavior
 * ------------------------------------------------------------------
 * A single app.js is shared by BOTH pages (login.html and dashboard.html).
 * On load, an init dispatcher detects which page is present and runs the
 * appropriate logic. Code is grouped into clearly-named modules so later
 * tasks can extend it without clashing:
 *
 *   Task 16.1 (this file) — token storage, JWT decode, role routing, the
 *             dashboard auth guard, logout, and a minimal working login
 *             submit with a seam for the shared fetch wrapper.
 *   Task 16.2 — shared fetch wrapper (loading indicators + friendly error
 *             mapping). Enhance `SMS.api.login` / add `SMS.api.request`.
 *   Task 17.x — admin flows (student table, add/update forms, delete).
 *   Task 18.x — student self-service view + outstanding balance.
 *
 * Everything is namespaced under `window.SMS` so subsequent tasks can add
 * to the same object. Page wiring is guarded against double-init.
 * ------------------------------------------------------------------
 */

window.SMS = window.SMS || {};

/* =================================================================
 * Module: auth — token storage + JWT decoding + validity checks
 * ================================================================= */
SMS.auth = (function () {
  // Stable storage key for the JWT (localStorage so it survives the
  // login.html -> dashboard.html navigation and page reloads).
  const TOKEN_KEY = "sms.jwt";

  /** Persist the JWT. */
  function saveToken(token) {
    try {
      localStorage.setItem(TOKEN_KEY, token);
    } catch (_) {
      /* storage unavailable — ignore */
    }
  }

  /** Read the raw JWT, or null when absent. */
  function getToken() {
    try {
      return localStorage.getItem(TOKEN_KEY);
    } catch (_) {
      return null;
    }
  }

  /** Discard the stored JWT. */
  function clearToken() {
    try {
      localStorage.removeItem(TOKEN_KEY);
    } catch (_) {
      /* ignore */
    }
  }

  /** Decode a base64url segment to a UTF-8 string. */
  function base64UrlDecode(segment) {
    let s = String(segment).replace(/-/g, "+").replace(/_/g, "/");
    const pad = s.length % 4;
    if (pad) s += "=".repeat(4 - pad);
    const raw = atob(s);
    try {
      // Recover multi-byte UTF-8 characters.
      return decodeURIComponent(
        raw
          .split("")
          .map((c) => "%" + ("00" + c.charCodeAt(0).toString(16)).slice(-2))
          .join(""),
      );
    } catch (_) {
      return raw;
    }
  }

  /**
   * Decode a JWT's payload. Returns the payload object, or null when the
   * token is missing / not a well-formed three-part JWT / unparseable.
   */
  function decodeToken(token) {
    if (!token || typeof token !== "string") return null;
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    try {
      return JSON.parse(base64UrlDecode(parts[1]));
    } catch (_) {
      return null;
    }
  }

  /**
   * Return the meaningful claims { sub, role, exp } only when the token is
   * parseable, carries the required claims, and has NOT expired. Returns
   * null for missing / malformed / unparseable / expired tokens so callers
   * can treat "no valid token" uniformly.
   */
  function getValidClaims(token) {
    const payload = decodeToken(token);
    if (!payload) return null;

    const sub = payload.sub;
    const role = payload.role;
    const exp = payload.exp;

    if (!sub || !role) return null; // identity/role claims required
    if (typeof exp !== "number") return null; // expiry claim required

    const nowSeconds = Math.floor(Date.now() / 1000);
    if (exp <= nowSeconds) return null; // expired

    return { sub: sub, role: role, exp: exp };
  }

  /** Convenience: is there a currently-valid, unexpired token stored? */
  function hasValidToken() {
    return getValidClaims(getToken()) !== null;
  }

  return {
    TOKEN_KEY: TOKEN_KEY,
    saveToken: saveToken,
    getToken: getToken,
    clearToken: clearToken,
    decodeToken: decodeToken,
    getValidClaims: getValidClaims,
    hasValidToken: hasValidToken,
  };
})();

/* =================================================================
 * Module: nav — page navigation + shared small helpers
 * ================================================================= */
SMS.nav = (function () {
  const LOGIN_PAGE = "login.html";
  const DASHBOARD_PAGE = "dashboard.html";

  /**
   * Redirect to the login page. Optional `reason` is surfaced as a query
   * param so login.html can show the right message:
   *   "session-expired" -> R4.6, "role" -> R4.5. Omit for a plain login (R4.3).
   */
  function toLogin(reason) {
    const url = reason
      ? LOGIN_PAGE + "?reason=" + encodeURIComponent(reason)
      : LOGIN_PAGE;
    window.location.assign(url);
  }

  function toDashboard() {
    window.location.assign(DASHBOARD_PAGE);
  }

  /** Read a query-string parameter from the current URL. */
  function queryParam(name) {
    return new URLSearchParams(window.location.search).get(name);
  }

  /** Derive up to two uppercase initials from a display string. */
  function initials(text) {
    if (!text) return "–";
    const parts = String(text).trim().split(/\s+/).filter(Boolean);
    if (parts.length === 0) return "–";
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }

  return {
    LOGIN_PAGE: LOGIN_PAGE,
    DASHBOARD_PAGE: DASHBOARD_PAGE,
    toLogin: toLogin,
    toDashboard: toDashboard,
    queryParam: queryParam,
    initials: initials,
  };
})();

/* =================================================================
 * Module: api — backend calls + shared fetch wrapper
 * -----------------------------------------------------------------
 * Task 16.2 adds `SMS.api.request(method, url, body, options)` — a single
 * fetch wrapper that every authenticated call (tasks 17.x/18.x) routes
 * through. It:
 *   - attaches `Authorization: Bearer <token>` when a token is stored,
 *   - sets `Content-Type: application/json` for requests with a body,
 *   - shows a loading indicator within 500ms of start (R10.5) and ALWAYS
 *     hides it on completion in a `finally` step (R10.4),
 *   - parses the backend ErrorResponse envelope and throws a friendly Error
 *     with `.status`, `.message` (no technical detail), and `.fieldErrors`
 *     for field-level form messages (R10.3, R6.6),
 *   - on a 401 during an authenticated request, clears the token and
 *     redirects to login with a session-expired reason (R4.6),
 *   - maps network failures to a friendly connection error with `.status = 0`.
 *
 * `login()` is refactored to reuse the shared friendly-message mapper while
 * keeping its public return shape { token, role, username } stable.
 * ================================================================= */
SMS.api = (function () {
  const LOGIN_URL = "/api/auth/login";

  // Default global overlay element toggled when no explicit target is given.
  // Later pages may add an element with this id; absence is handled safely.
  const DEFAULT_OVERLAY_ID = "app-loading";
  const HIDDEN_CLASS = "hidden";
  const DEFAULT_DELAY_MS = 500;

  /* ---------------------------------------------------------------
   * Loading manager — shows an indicator within 500ms (so fast requests
   * don't flash a spinner) and always hides it on completion. Callers pass
   * `options.loadingEl` (an id string, an Element, or an array/NodeList of
   * them); when omitted, a global overlay (`#app-loading`) is used if present.
   * ------------------------------------------------------------- */
  const loading = (function () {
    /** Normalize a caller-supplied target into an array of Elements. */
    function resolve(target) {
      if (target === undefined || target === null) {
        const def = document.getElementById(DEFAULT_OVERLAY_ID);
        return def ? [def] : [];
      }
      if (typeof target === "string") {
        const node = document.getElementById(target);
        return node ? [node] : [];
      }
      // Array-like (Array / NodeList / HTMLCollection).
      if (
        typeof target.length === "number" &&
        typeof target.nodeType !== "number"
      ) {
        return Array.prototype.slice.call(target).filter(Boolean);
      }
      return [target];
    }

    function show(els) {
      els.forEach(function (el) {
        if (!el || !el.classList) return;
        el.classList.remove(HIDDEN_CLASS);
        el.setAttribute("aria-busy", "true");
      });
    }

    function hide(els) {
      els.forEach(function (el) {
        if (!el || !el.classList) return;
        el.classList.add(HIDDEN_CLASS);
        el.setAttribute("aria-busy", "false");
      });
    }

    /**
     * Begin a loading cycle. The indicator becomes visible only after
     * `delayMs` (default 500ms). Returns a `stop()` function that MUST be
     * called in a `finally` block; it cancels the pending timer and hides
     * the indicator (idempotent, safe if it was never shown).
     */
    function begin(target, delayMs) {
      const els = resolve(target);
      const delay = typeof delayMs === "number" ? delayMs : DEFAULT_DELAY_MS;
      let stopped = false;
      const timer = setTimeout(function () {
        if (!stopped) show(els);
      }, delay);

      return function stop() {
        if (stopped) return;
        stopped = true;
        clearTimeout(timer);
        hide(els);
      };
    }

    return { begin: begin, show: show, hide: hide, resolve: resolve };
  })();

  /**
   * Map a status + optional error envelope to a friendly, non-technical
   * message (R10.3). `context === "login"` preserves the sign-in wording;
   * the default covers general API calls for 400/401/403/404/409/500 and
   * network failures (status 0). When present, the backend's own
   * user-facing `message` is preferred for validation/conflict cases.
   */
  function messageForStatus(status, body, context) {
    const friendly =
      body && typeof body.message === "string" && body.message.trim()
        ? body.message.trim()
        : null;

    if (status === 0) {
      return "Unable to reach the server. Check your connection and try again.";
    }

    if (context === "login") {
      if (status === 400)
        return "Please enter both your username and password.";
      if (status === 401) return "Incorrect username or password.";
      if (status === 403) return "This account is not permitted to sign in.";
      if (status >= 500)
        return "Something went wrong on our end. Please try again shortly.";
      return friendly || "Unable to sign in right now. Please try again.";
    }

    switch (status) {
      case 400:
        return (
          friendly ||
          "Some of the information provided isn't valid. Please review and try again."
        );
      case 401:
        return "Your session has expired. Please sign in again.";
      case 403:
        return "You don't have permission to perform this action.";
      case 404:
        return friendly || "We couldn't find what you were looking for.";
      case 409:
        return (
          friendly || "That username is already taken. Please choose another."
        );
      default:
        if (status >= 500)
          return "Something went wrong on our end. Please try again shortly.";
        return friendly || "Something went wrong. Please try again.";
    }
  }

  /** Extract a safe `fieldErrors` map from an envelope, or null. */
  function fieldErrorsOf(body) {
    return body && body.fieldErrors && typeof body.fieldErrors === "object"
      ? body.fieldErrors
      : null;
  }

  /** Build a friendly Error carrying `.status` and `.fieldErrors`. */
  function makeError(status, body, context) {
    const err = new Error(messageForStatus(status, body, context));
    err.status = status;
    err.fieldErrors = fieldErrorsOf(body);
    return err;
  }

  /** Read a response body as parsed JSON, tolerating empty/non-JSON bodies. */
  async function readBody(response) {
    try {
      const text = await response.text();
      return text ? JSON.parse(text) : null;
    } catch (_) {
      return null;
    }
  }

  /**
   * Shared request wrapper used by all authenticated calls.
   *
   *   method  — HTTP verb ("GET", "POST", ...). Defaults to "GET".
   *   url     — request URL (same-origin API path).
   *   body    — optional payload; objects are JSON-stringified.
   *   options — {
   *     headers,               extra request headers
   *     authenticated = true,  attach the bearer token + 401 auth-guard
   *     loadingEl,             id | Element | Element[] to toggle; default overlay
   *     loadingDelay = 500,    ms before the indicator appears
   *   }
   *
   * Resolves with the parsed JSON body (or null). Rejects with a friendly
   * Error whose `.status`, `.message`, and `.fieldErrors` are set.
   */
  async function request(method, url, body, options) {
    options = options || {};
    const authenticated = options.authenticated !== false; // default: true
    const headers = Object.assign({}, options.headers || {});

    if (authenticated) {
      const token = SMS.auth.getToken();
      if (token) headers["Authorization"] = "Bearer " + token;
    }

    const hasBody = body !== undefined && body !== null;
    let payload;
    if (hasBody) {
      if (!headers["Content-Type"])
        headers["Content-Type"] = "application/json";
      payload = typeof body === "string" ? body : JSON.stringify(body);
    }

    const stopLoading = loading.begin(options.loadingEl, options.loadingDelay);

    try {
      let response;
      try {
        response = await fetch(url, {
          method: method || "GET",
          headers: headers,
          body: payload,
        });
      } catch (_) {
        throw makeError(0, null); // network failure (R10.3)
      }

      const parsed = await readBody(response);

      if (response.status === 401 && authenticated) {
        // Session expired mid-flight: clear + redirect to login (R4.6),
        // then still reject so the caller stops its own flow.
        SMS.auth.clearToken();
        SMS.nav.toLogin("session-expired");
        throw makeError(401, parsed);
      }

      if (!response.ok) {
        throw makeError(response.status, parsed);
      }

      return parsed;
    } finally {
      stopLoading(); // R10.4 — indicator never dangles after completion
    }
  }

  /* Convenience verb helpers (thin wrappers over `request`). */
  function get(url, options) {
    return request("GET", url, null, options);
  }
  function post(url, body, options) {
    return request("POST", url, body, options);
  }
  function put(url, body, options) {
    return request("PUT", url, body, options);
  }
  function del(url, options) {
    return request("DELETE", url, null, options);
  }

  /**
   * POST credentials to the auth endpoint. This is the ONE unauthenticated
   * call and is intentionally NOT routed through `request` (no bearer token,
   * no 401 auth-guard redirect — a 401 here means bad credentials). It reuses
   * the shared friendly-message mapper via the "login" context.
   *
   * Resolves with the parsed body { token, role, username } on 2xx.
   * Rejects with an Error whose `.status` is the HTTP status (0 for network
   * failures) and `.message` is human-readable (R10.3).
   */
  async function login(username, password) {
    let response;
    try {
      response = await fetch(LOGIN_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username: username, password: password }),
      });
    } catch (_) {
      throw makeError(0, null, "login");
    }

    const body = await readBody(response);

    if (!response.ok) {
      throw makeError(response.status, body, "login");
    }
    return body;
  }

  return {
    request: request,
    get: get,
    post: post,
    put: put,
    del: del,
    login: login,
    messageForStatus: messageForStatus,
    loading: loading,
  };
})();

/* =================================================================
 * Page: login.html
 * ================================================================= */
SMS.loginPage = (function () {
  const RECOGNIZED_ROLES = ["ADMIN", "STUDENT"];
  let wired = false;

  function el(id) {
    return document.getElementById(id);
  }

  function showError(message) {
    const box = el("login-error");
    const text = el("login-error-text");
    if (text) text.textContent = message;
    if (box) {
      box.classList.remove("hidden");
      box.classList.add("flex");
    }
  }

  function clearError() {
    const box = el("login-error");
    if (box) {
      box.classList.add("hidden");
      box.classList.remove("flex");
    }
  }

  // Minimal loading UX. Task 16.2 refines timing/wrapper behavior.
  function setLoading(isLoading) {
    const submit = el("login-submit");
    const spinner = el("login-spinner");
    const label = el("login-submit-label");
    if (submit) submit.disabled = isLoading;
    if (spinner) spinner.classList.toggle("hidden", !isLoading);
    if (label) label.textContent = isLoading ? "Signing in…" : "Sign in";
  }

  // Surface a message when we were redirected back here by the auth guard.
  function showRedirectReason() {
    const reason = SMS.nav.queryParam("reason");
    if (reason === "session-expired") {
      showError("Your session has expired. Please sign in again.");
    } else if (reason === "role") {
      showError(
        "Your account role is not recognized. Please contact your administrator.",
      );
    }
  }

  function togglePassword() {
    const input = el("login-password");
    const btn = el("toggle-password");
    if (!input) return;
    const revealed = input.type === "text";
    input.type = revealed ? "password" : "text";
    if (btn)
      btn.setAttribute(
        "aria-label",
        revealed ? "Show password" : "Hide password",
      );
  }

  async function handleSubmit(event) {
    event.preventDefault();
    clearError();

    const username = (el("login-username")?.value || "").trim();
    const password = el("login-password")?.value || "";

    if (!username || !password) {
      showError("Please enter both your username and password.");
      return;
    }

    setLoading(true);
    try {
      const result = await SMS.api.login(username, password);
      const token = result && result.token;
      const role = result && result.role;

      if (!token) {
        showError("Login did not return a valid session. Please try again.");
        return;
      }

      // R4.5: successful auth but an unrecognized role — discard + error.
      if (!RECOGNIZED_ROLES.includes(role)) {
        SMS.auth.clearToken();
        showError(
          "Your account role is not recognized. Please contact your administrator.",
        );
        return;
      }

      // Store token; role/identity are re-derived from the token on the
      // dashboard by the auth guard.
      SMS.auth.saveToken(token);
      SMS.nav.toDashboard();
    } catch (err) {
      showError((err && err.message) || "Unable to sign in right now.");
    } finally {
      setLoading(false);
    }
  }

  function init() {
    if (wired) return;
    const form = el("login-form");
    if (!form) return;
    wired = true;

    // Already signed in? Skip straight to the dashboard.
    if (SMS.auth.hasValidToken()) {
      SMS.nav.toDashboard();
      return;
    }

    form.addEventListener("submit", handleSubmit);
    const toggle = el("toggle-password");
    if (toggle) toggle.addEventListener("click", togglePassword);

    showRedirectReason();
  }

  return { init: init };
})();

/* =================================================================
 * Page: dashboard.html — auth guard, role routing, user chip, logout
 * ================================================================= */
SMS.dashboardPage = (function () {
  let wired = false;

  function el(id) {
    return document.getElementById(id);
  }

  function reveal(id) {
    const node = el(id);
    if (node) node.classList.remove("hidden");
  }

  function populateUserChip(claims) {
    const nameNode = el("user-name");
    const roleNode = el("user-role");
    const initialsNode = el("user-initials");
    if (nameNode) nameNode.textContent = claims.sub;
    if (roleNode) roleNode.textContent = claims.role;
    if (initialsNode) initialsNode.textContent = SMS.nav.initials(claims.sub);
  }

  function handleLogout() {
    // R4.4: discard the stored JWT and return to the login page.
    SMS.auth.clearToken();
    SMS.nav.toLogin();
  }

  /**
   * Auth guard (runs on dashboard load). Returns the valid claims when the
   * caller may proceed, or null after issuing a redirect.
   *   - no token stored           -> login (no message)             R4.3
   *   - token expired / unparseable -> discard + session-expired    R4.6
   *   - unrecognized role         -> discard + role error           R4.5
   */
  function guard() {
    const token = SMS.auth.getToken();

    if (!token) {
      SMS.nav.toLogin();
      return null;
    }

    const claims = SMS.auth.getValidClaims(token);
    if (!claims) {
      SMS.auth.clearToken();
      SMS.nav.toLogin("session-expired");
      return null;
    }

    if (claims.role !== "ADMIN" && claims.role !== "STUDENT") {
      SMS.auth.clearToken();
      SMS.nav.toLogin("role");
      return null;
    }

    return claims;
  }

  function init() {
    if (wired) return;
    // Detect the dashboard by the presence of a role view container.
    if (!el("admin-view") && !el("student-view")) return;
    wired = true;

    const claims = guard();
    if (!claims) return; // a redirect is already in flight

    // Wire logout (R4.4).
    const logoutBtn = el("logout-btn");
    if (logoutBtn) logoutBtn.addEventListener("click", handleLogout);

    populateUserChip(claims);

    // Expose the resolved session so tasks 17.x / 18.x can build on it.
    // Set BEFORE revealing a view so downstream modules can read it in init.
    SMS.session = { username: claims.sub, role: claims.role, exp: claims.exp };

    // Role routing: reveal exactly one view.
    if (claims.role === "ADMIN") {
      reveal("admin-view"); // R4.1 — admin dashboard (View/Add Students)
      // Task 17.1 — admin flows (table, add/update forms, delete confirm).
      // Guarded against double-init inside the module itself.
      if (SMS.adminFlows && typeof SMS.adminFlows.init === "function") {
        SMS.adminFlows.init();
      }
    } else {
      reveal("student-view"); // R4.2 — student self-service view
      // Task 18.1 — student self-service view (own record + outstanding
      // balance). Guarded against double-init inside the module itself.
      if (SMS.studentView && typeof SMS.studentView.init === "function") {
        SMS.studentView.init();
      }
    }
  }

  return { init: init, guard: guard };
})();

/* =================================================================
 * Module: adminFlows — admin dashboard interactions (Task 17.1)
 * -----------------------------------------------------------------
 * Invoked by SMS.dashboardPage.init() after the admin view is revealed
 * (role === ADMIN). Owns everything inside #admin-view:
 *
 *   - Panel navigation via [data-nav-target] with an active nav marker
 *     (.is-active). "View Students" loads/refreshes the list; "Add Student"
 *     shows a cleared Add form (R5.1).
 *   - The Student_Table: loading indicator (R6.5), one row per record with
 *     name/course/mobile/email + Edit/Delete controls (R6.2, R6.3), an
 *     empty-state message (R6.4), and a list-load error with retry (R6.6).
 *   - The Add_Student_Form: POST /api/students, per-field validation errors
 *     (400), username conflict (409), success confirmation + refresh.
 *   - The Update_Student_Form: pre-populated from the selected record (R7.1),
 *     PUT /api/students/{id}, success confirmation (R7.3); password left
 *     blank means "keep current". Identical field handling to Add (R10.2).
 *   - The delete confirmation modal: opening it sends NO request (R8.1),
 *     Cancel dismisses without a request (R8.2), Confirm sends DELETE and
 *     refreshes the list on success.
 *
 * Guarded against double-init. Later tasks (18.x) can follow this shape to
 * wire the student self-service view.
 * ================================================================= */
SMS.adminFlows = (function () {
  let wired = false;

  // In-memory cache of the last-loaded student list (order preserved from
  // the backend, which sorts ascending by name — R6.1).
  let students = [];
  // The record targeted by the delete modal; null when no request is pending.
  let deleteTarget = null;

  // Field names shared by the Add and Update forms (identical layout — R10.2).
  const TEXT_FIELDS = ["name", "course", "mobile", "email", "username"];
  const FEE_FIELDS = ["totalFees", "paidFees"];

  function el(id) {
    return document.getElementById(id);
  }

  /* ---------------- small DOM helpers ---------------- */

  function setCell(row, key, value) {
    const node = row.querySelector('[data-cell="' + key + '"]');
    if (node) node.textContent = value == null ? "" : String(value);
  }

  function fieldValue(prefix, name) {
    const node = el(prefix + "-" + name);
    return node ? node.value : "";
  }

  function textValue(prefix, name) {
    return String(fieldValue(prefix, name)).trim();
  }

  /** Parse a fee input: blank -> null (lets the backend flag "required"),
   *  numeric -> Number, otherwise the raw string (backend rejects with 400). */
  function feeValue(prefix, name) {
    const raw = String(fieldValue(prefix, name)).trim();
    if (raw === "") return null;
    const n = Number(raw);
    return isFinite(n) ? n : raw;
  }

  /** Collect a StudentRequestDTO-shaped payload from a form (add|edit). */
  function gatherPayload(prefix) {
    const payload = {};
    TEXT_FIELDS.forEach(function (name) {
      payload[name] = textValue(prefix, name);
    });
    FEE_FIELDS.forEach(function (name) {
      payload[name] = feeValue(prefix, name);
    });
    // Password is intentionally NOT trimmed. Blank on edit = keep current.
    payload.password = fieldValue(prefix, "password");
    return payload;
  }

  /* ---------------- per-field + form-level messaging ---------------- */

  function clearFieldErrors(form) {
    if (!form) return;
    form.querySelectorAll("[data-error-for]").forEach(function (p) {
      p.textContent = "";
      p.classList.add("hidden");
    });
  }

  function setFieldError(form, field, message) {
    if (!form) return;
    const p = form.querySelector('[data-error-for="' + field + '"]');
    if (p) {
      p.textContent = message;
      p.classList.remove("hidden");
    }
  }

  function applyFieldErrors(form, fieldErrors) {
    if (!form || !fieldErrors) return;
    Object.keys(fieldErrors).forEach(function (key) {
      setFieldError(form, key, fieldErrors[key]);
    });
  }

  function setFormMessage(node, message, kind) {
    if (!node) return;
    node.textContent = message;
    node.classList.remove(
      "hidden",
      "bg-danger/15",
      "text-danger",
      "bg-success/15",
      "text-success",
    );
    if (kind === "success") {
      node.classList.add("bg-success/15", "text-success");
    } else {
      node.classList.add("bg-danger/15", "text-danger");
    }
  }

  function clearFormMessage(node) {
    if (!node) return;
    node.textContent = "";
    node.classList.add("hidden");
    node.classList.remove(
      "bg-danger/15",
      "text-danger",
      "bg-success/15",
      "text-success",
    );
  }

  /** Transient confirmation surfaced in the shared toast region so it stays
   *  visible even after we navigate back to the students panel (R5.2, R7.3). */
  function showToast(message, kind) {
    const region = el("toast-region");
    if (!region) return;
    const toast = document.createElement("div");
    toast.setAttribute("role", "status");
    toast.className =
      "pointer-events-auto max-w-sm rounded-xl px-4 py-3 text-sm font-medium shadow-card animate-fade-up " +
      (kind === "success"
        ? "bg-success/15 text-success ring-1 ring-success/30"
        : "bg-danger/15 text-danger ring-1 ring-danger/30");
    toast.textContent = message;
    region.appendChild(toast);
    setTimeout(function () {
      toast.remove();
    }, 3500);
  }

  /** Route a thrown API error to the right form: field-level messages for
   *  validation (400), the username field for a conflict (409), plus a
   *  friendly form-level message (R10.3). */
  function handleFormError(prefix, err) {
    const form = el(prefix + "-student-form");
    const msgEl = el(prefix + "-form-message");
    if (err && err.fieldErrors) applyFieldErrors(form, err.fieldErrors);
    if (err && err.status === 409) {
      setFieldError(form, "username", err.message);
    }
    setFormMessage(
      msgEl,
      (err && err.message) || "Something went wrong. Please try again.",
      "error",
    );
  }

  function setSubmitting(prefix, on) {
    const btn = el(prefix + "-submit");
    const spinner = el(prefix + "-spinner");
    if (btn) btn.disabled = on;
    if (spinner) spinner.classList.toggle("hidden", !on);
  }

  /* ---------------- panel navigation ---------------- */

  /** Show exactly one [data-panel]; mark the matching .nav-item active. */
  function showPanel(targetId) {
    document.querySelectorAll("[data-panel]").forEach(function (panel) {
      panel.classList.toggle("hidden", panel.id !== targetId);
    });
    document.querySelectorAll(".nav-item").forEach(function (item) {
      const active = item.getAttribute("data-nav-target") === targetId;
      item.classList.toggle("is-active", active);
    });
  }

  /** Show a panel and run its side effect: refresh the list for the students
   *  panel, clear the form for the add panel. */
  function navigateTo(targetId) {
    if (!targetId) return;
    showPanel(targetId);
    if (targetId === "students-panel") {
      loadStudents();
    } else if (targetId === "add-panel") {
      resetAddForm();
    }
  }

  /* ---------------- student table ---------------- */

  /** Reveal exactly one of the table's states: loading | error | empty | table. */
  function setListState(state) {
    const nodes = {
      loading: el("students-loading"),
      error: el("students-error"),
      empty: el("students-empty"),
      table: el("students-table-wrap"),
    };
    Object.keys(nodes).forEach(function (key) {
      const node = nodes[key];
      if (!node) return;
      if (key === state) {
        node.classList.remove("hidden");
        // loading/error/empty rely on flex for their centered layout.
        if (key !== "table") node.classList.add("flex");
      } else {
        node.classList.add("hidden");
        node.classList.remove("flex");
      }
    });
  }

  function renderStudents(list) {
    students = Array.isArray(list) ? list : [];
    const tbody = el("students-tbody");
    if (tbody) tbody.innerHTML = "";

    if (!students.length) {
      setListState("empty"); // R6.4
      return;
    }

    const template = el("student-row-template");
    if (!template || !tbody) return;

    students.forEach(function (student) {
      const row = template.content.firstElementChild.cloneNode(true);
      setCell(row, "initials", SMS.nav.initials(student.name));
      setCell(row, "name", student.name);
      setCell(row, "course", student.course);
      setCell(row, "mobile", student.mobile);
      setCell(row, "email", student.email);

      const editBtn = row.querySelector('[data-action="edit"]');
      if (editBtn)
        editBtn.addEventListener("click", function () {
          openEdit(student);
        });

      const deleteBtn = row.querySelector('[data-action="delete"]');
      if (deleteBtn)
        deleteBtn.addEventListener("click", function () {
          openDelete(student);
        });

      tbody.appendChild(row);
    });

    setListState("table"); // R6.2, R6.3
  }

  /** GET /api/students, showing the loading indicator while in flight and
   *  the error+retry state on failure (R6.5, R6.6). */
  async function loadStudents() {
    setListState("loading");
    try {
      const list = await SMS.api.get("/api/students");
      renderStudents(list || []);
    } catch (err) {
      const text = el("students-error-text");
      if (text)
        text.textContent =
          (err && err.message) || "We couldn't load the students.";
      setListState("error");
    }
  }

  /* ---------------- add form ---------------- */

  function resetAddForm() {
    const form = el("add-student-form");
    if (form) form.reset();
    clearFieldErrors(form);
    clearFormMessage(el("add-form-message"));
  }

  async function handleAddSubmit(event) {
    event.preventDefault();
    const form = el("add-student-form");
    clearFieldErrors(form); // fresh slate each submit
    clearFormMessage(el("add-form-message"));

    const payload = gatherPayload("add");
    setSubmitting("add", true);
    try {
      await SMS.api.post("/api/students", payload);
      setFormMessage(
        el("add-form-message"),
        "Student created successfully.",
        "success",
      );
      showToast("Student created successfully.", "success");
      navigateTo("students-panel"); // refresh list + return to table
    } catch (err) {
      handleFormError("add", err);
    } finally {
      setSubmitting("add", false);
    }
  }

  /* ---------------- edit form ---------------- */

  /** Pre-populate the Update form from a record and show it (R7.1). Password
   *  is left blank (placeholder communicates "keep current"). */
  function openEdit(record) {
    const form = el("edit-student-form");
    clearFieldErrors(form);
    clearFormMessage(el("edit-form-message"));

    if (el("edit-id")) el("edit-id").value = record.id != null ? record.id : "";
    TEXT_FIELDS.forEach(function (name) {
      const node = el("edit-" + name);
      if (node) node.value = record[name] != null ? record[name] : "";
    });
    FEE_FIELDS.forEach(function (name) {
      const node = el("edit-" + name);
      if (node) node.value = record[name] != null ? record[name] : "";
    });
    if (el("edit-password")) el("edit-password").value = "";

    showPanel("edit-panel");
  }

  async function handleEditSubmit(event) {
    event.preventDefault();
    const form = el("edit-student-form");
    clearFieldErrors(form);
    clearFormMessage(el("edit-form-message"));

    const id = el("edit-id") ? el("edit-id").value : "";
    const payload = gatherPayload("edit");
    setSubmitting("edit", true);
    try {
      await SMS.api.put("/api/students/" + encodeURIComponent(id), payload);
      setFormMessage(
        el("edit-form-message"),
        "Student updated successfully.",
        "success",
      );
      showToast("Student updated successfully.", "success"); // R7.3
      navigateTo("students-panel");
    } catch (err) {
      handleFormError("edit", err);
    } finally {
      setSubmitting("edit", false);
    }
  }

  /* ---------------- delete confirmation ---------------- */

  function openModal() {
    const modal = el("delete-modal");
    if (!modal) return;
    modal.classList.remove("hidden");
    modal.classList.add("flex");
  }

  function closeModal() {
    const modal = el("delete-modal");
    if (!modal) return;
    modal.classList.add("hidden");
    modal.classList.remove("flex");
    setDeleteSpinner(false);
  }

  function setDeleteSpinner(on) {
    const spinner = el("delete-spinner");
    if (spinner) spinner.classList.toggle("hidden", !on);
  }

  /** Open the confirmation prompt for a record. Sends NO request (R8.1). */
  function openDelete(record) {
    deleteTarget = record;
    const nameNode = el("delete-target-name");
    if (nameNode) nameNode.textContent = record.name || "this student";
    openModal();
  }

  /** Cancel dismisses the prompt, sends no request, changes nothing (R8.2). */
  function cancelDelete() {
    deleteTarget = null;
    closeModal();
  }

  /** Confirm sends the DELETE, then refreshes the list on success (R8.3). */
  async function confirmDelete() {
    if (!deleteTarget) {
      closeModal();
      return;
    }
    const id = deleteTarget.id;
    const name = deleteTarget.name;
    const confirmBtn = el("delete-confirm");
    if (confirmBtn) confirmBtn.disabled = true;
    setDeleteSpinner(true);
    try {
      await SMS.api.del("/api/students/" + encodeURIComponent(id));
      deleteTarget = null;
      closeModal();
      showToast((name ? name + " " : "Student ") + "was deleted.", "success");
      loadStudents(); // refresh
    } catch (err) {
      // Keep the modal open so the admin can retry or cancel.
      showToast(
        (err && err.message) || "Unable to delete this student.",
        "error",
      );
    } finally {
      setDeleteSpinner(false);
      if (confirmBtn) confirmBtn.disabled = false;
    }
  }

  /* ---------------- init ---------------- */

  function init() {
    if (wired) return;
    if (!el("admin-view")) return; // only wire on the admin dashboard
    wired = true;

    // Panel navigation — sidebar tabs, the header "Add Student" shortcut, and
    // the forms' Cancel buttons all carry [data-nav-target].
    document.querySelectorAll("[data-nav-target]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        navigateTo(btn.getAttribute("data-nav-target"));
      });
    });

    // List error retry (R6.6).
    const retry = el("students-retry");
    if (retry) retry.addEventListener("click", loadStudents);

    // Forms.
    const addForm = el("add-student-form");
    if (addForm) addForm.addEventListener("submit", handleAddSubmit);
    const editForm = el("edit-student-form");
    if (editForm) editForm.addEventListener("submit", handleEditSubmit);

    // Delete modal controls (confirm/cancel + backdrop dismiss).
    const cancelBtn = el("delete-cancel");
    if (cancelBtn) cancelBtn.addEventListener("click", cancelDelete);
    const confirmBtn = el("delete-confirm");
    if (confirmBtn) confirmBtn.addEventListener("click", confirmDelete);
    const backdrop = el("delete-backdrop");
    if (backdrop) backdrop.addEventListener("click", cancelDelete);

    // Initial load: the students panel is the default active tab.
    loadStudents();
  }

  return {
    init: init,
    // Exposed for later tasks / tests to reuse without re-wiring.
    loadStudents: loadStudents,
    renderStudents: renderStudents,
    navigateTo: navigateTo,
  };
})();

/* =================================================================
 * Module: studentView — student self-service view (Task 18.1)
 * -----------------------------------------------------------------
 * Invoked by SMS.dashboardPage.init() after the student view is revealed
 * (role === STUDENT). Owns everything inside #student-view:
 *
 *   - On init: show the loading indicator (#student-loading) and fetch the
 *     signed-in student's own record via GET /api/students/me through the
 *     shared SMS.api wrapper.
 *   - On success: populate the [data-field] nodes (name, course, email,
 *     mobile), format totalFees / paidFees to two decimals, compute the
 *     outstanding balance = max(totalFees - paidFees, 0) formatted to
 *     exactly two decimal places (R9.3, R9.4), set the avatar initials
 *     (#student-initials) from the name, reveal #student-card, and hide
 *     the loading indicator.
 *   - On error: reveal #student-error with a friendly, non-technical
 *     message drawn from the SMS.api error (R10.3).
 *
 * Follows the SMS.adminFlows module shape and is guarded against double-init.
 * ================================================================= */
SMS.studentView = (function () {
  const ME_URL = "/api/students/me";
  let wired = false;

  function el(id) {
    return document.getElementById(id);
  }

  function show(id) {
    const node = el(id);
    if (node) node.classList.remove("hidden");
  }

  function hide(id) {
    const node = el(id);
    if (node) node.classList.add("hidden");
  }

  /** Format a numeric-ish value to exactly two decimals; blank on null/NaN. */
  function formatMoney(value) {
    const n = Number(value);
    if (value === null || value === undefined || value === "" || !isFinite(n)) {
      return "—";
    }
    return n.toFixed(2);
  }

  /** Outstanding balance = max(total - paid, 0), to exactly 2dp (R9.3, R9.4). */
  function outstandingBalance(totalFees, paidFees) {
    const total = Number(totalFees);
    const paid = Number(paidFees);
    if (!isFinite(total) || !isFinite(paid)) return "—";
    const balance = total - paid;
    return (balance > 0 ? balance : 0).toFixed(2);
  }

  /** Write a value into the single [data-field="name"]-style node. */
  function setField(field, value) {
    const node = document.querySelector('[data-field="' + field + '"]');
    if (node) node.textContent = value == null || value === "" ? "—" : value;
  }

  /** Populate the read-only card from a StudentResponseDTO (R9.1). */
  function render(student) {
    student = student || {};

    setField("name", student.name);
    setField("course", student.course);
    setField("email", student.email);
    setField("mobile", student.mobile);
    setField("totalFees", formatMoney(student.totalFees));
    setField("paidFees", formatMoney(student.paidFees));
    setField(
      "balance",
      outstandingBalance(student.totalFees, student.paidFees),
    );

    const initialsNode = el("student-initials");
    if (initialsNode) initialsNode.textContent = SMS.nav.initials(student.name);
  }

  /** Show the error state with a friendly, non-technical message (R10.3). */
  function showError(message) {
    const text = el("student-error-text");
    if (text && message) text.textContent = message;
    hide("student-loading");
    hide("student-card");
    show("student-error");
  }

  /** Load the student's own record and reveal the detail card. */
  async function load() {
    hide("student-error");
    hide("student-card");
    show("student-loading");

    try {
      const student = await SMS.api.get(ME_URL, {
        loadingEl: "student-loading",
      });
      render(student);
      hide("student-loading");
      show("student-card");
    } catch (err) {
      // A 401 mid-flight already redirects to login via the shared wrapper;
      // for any other failure, surface a friendly inline message.
      showError((err && err.message) || "We couldn't load your details.");
    }
  }

  function init() {
    if (wired) return;
    if (!el("student-view")) return;
    wired = true;
    load();
  }

  return {
    init: init,
    // Exposed for later tasks / tests to reuse without re-wiring.
    render: render,
    formatMoney: formatMoney,
    outstandingBalance: outstandingBalance,
  };
})();

/* =================================================================
 * Init dispatcher — decide which page we're on and run its logic.
 * Scripts are loaded with `defer`, so the DOM is parsed by now; guard for
 * DOMContentLoaded anyway for safety.
 * ================================================================= */
(function bootstrap() {
  function run() {
    if (document.getElementById("login-form")) {
      SMS.loginPage.init();
    } else if (
      document.getElementById("admin-view") ||
      document.getElementById("student-view")
    ) {
      SMS.dashboardPage.init();
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", run, { once: true });
  } else {
    run();
  }
})();

/* =================================================================
 * Module: tableTools — client-side search + live count (additive UX)
 * -----------------------------------------------------------------
 * Purely presentational enhancement layered on top of the admin student
 * table. It NEVER touches the render/data flow: it observes the tbody that
 * SMS.adminFlows populates and (a) keeps a live record count in
 * #students-count and (b) filters visible rows from #students-search.
 * Every hook is optional — absent elements make this a no-op, so the module
 * is safe on the student view and the login page.
 * ================================================================= */
SMS.tableTools = (function () {
  let wired = false;

  function el(id) {
    return document.getElementById(id);
  }

  function rowsOf(tbody) {
    return Array.prototype.slice.call(
      tbody.querySelectorAll("[data-student-row]"),
    );
  }

  function currentQuery(search) {
    return search
      ? String(search.value || "")
          .trim()
          .toLowerCase()
      : "";
  }

  /** Apply the active search filter and refresh the count label. */
  function refresh(tbody, search, count) {
    const rows = rowsOf(tbody);
    const total = rows.length;
    const query = currentQuery(search);
    let visible = 0;

    rows.forEach(function (row) {
      const match =
        !query || row.textContent.toLowerCase().indexOf(query) !== -1;
      row.classList.toggle("hidden", !match);
      if (match) visible++;
    });

    if (count) {
      if (total === 0) {
        count.textContent = "";
      } else if (query) {
        count.textContent = "(" + visible + " of " + total + ")";
      } else {
        count.textContent = "(" + total + ")";
      }
    }
  }

  function init() {
    if (wired) return;
    const tbody = el("students-tbody");
    if (!tbody) return; // only the admin dashboard has the table
    wired = true;

    const search = el("students-search");
    const count = el("students-count");

    if (search) {
      search.addEventListener("input", function () {
        refresh(tbody, search, count);
      });
    }

    // Re-run whenever SMS.adminFlows re-renders rows (load / refresh).
    const observer = new MutationObserver(function () {
      refresh(tbody, search, count);
    });
    observer.observe(tbody, { childList: true });

    refresh(tbody, search, count);
  }

  return { init: init, refresh: refresh };
})();

(function bootstrapTableTools() {
  function run() {
    SMS.tableTools.init();
  }
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", run, { once: true });
  } else {
    run();
  }
})();
