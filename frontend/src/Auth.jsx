import { useState } from "react";

const AUTH_URL = `${(import.meta.env.VITE_API_BASE_URL || "http://localhost:8081/api").replace(/\/$/, "")}/auth`;

function Auth({ onLogin }) {
  const [mode, setMode] = useState("login");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError("");

    if (!email.trim()) {
      setError("Please enter your email address.");
      return;
    }

    if (mode === "register" && name.trim().length < 2) {
      setError("Please enter your full name.");
      return;
    }

    if (password.length < 8) {
      setError("Password must contain at least 8 characters.");
      return;
    }

    setLoading(true);

    try {
      const endpoint =
        mode === "login" ? "/login" : "/register";

      const payload =
        mode === "login"
          ? {
              email: email.trim(),
              password,
            }
          : {
              name: name.trim(),
              email: email.trim(),
              password,
            };

      const response = await fetch(`${AUTH_URL}${endpoint}`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(payload),
      });

      const data = await response.json();

      if (!response.ok) {
        throw new Error(
          data.message ||
            data.error ||
            "Authentication failed. Please try again."
        );
      }

      if (!data.token) {
        throw new Error("Authentication token was not received.");
      }

      onLogin(data, mode === "login" ? rememberMe : true);

    } catch (err) {
      setError(
        err.message || "Unable to connect to WebDoctor server."
      );
    } finally {
      setLoading(false);
    }
  };

  const switchMode = () => {
    setMode(mode === "login" ? "register" : "login");
    setError("");
    setPassword("");
    setShowPassword(false);
  };

  return (
    <main className="auth-page">
      <div className="auth-background-glow" />

      <div className="auth-card">

        {/* BRAND */}
        <div className="auth-brand">
          <div className="auth-brand-icon">✦</div>

          <div>
            <h1>WebDoctor</h1>
            <p>WEBSITE DIAGNOSTICS PLATFORM</p>
          </div>
        </div>

        {/* HEADING */}
        <div className="auth-heading">
          <span className="auth-eyebrow">
            {mode === "login" ? "WELCOME BACK" : "GET STARTED"}
          </span>

          <h2>
            {mode === "login"
              ? "Sign in to your account"
              : "Create your account"}
          </h2>

          <p>
            {mode === "login"
              ? "Continue monitoring your website health."
              : "Start diagnosing and improving your websites."}
          </p>
        </div>

        {/* AUTH FORM */}
        <form onSubmit={handleSubmit}>

          {mode === "register" && (
            <div className="auth-field">
              <label htmlFor="auth-name">Full Name</label>

              <input
                id="auth-name"
                type="text"
                placeholder="Enter your full name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                minLength={2}
                maxLength={100}
                autoComplete="name"
              />
            </div>
          )}

          <div className="auth-field">
            <label htmlFor="auth-email">Email Address</label>

            <input
              id="auth-email"
              type="email"
              placeholder="you@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoComplete="email"
            />
          </div>

          {/* PASSWORD */}
          <div className="auth-field">
            <label htmlFor="auth-password">Password</label>

            <div className="auth-password-wrapper">
              <input
                id="auth-password"
                type={showPassword ? "text" : "password"}
                placeholder="Minimum 8 characters"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                minLength={8}
                maxLength={100}
                autoComplete={
                  mode === "login"
                    ? "current-password"
                    : "new-password"
                }
              />

              <button
                type="button"
                className="auth-password-toggle"
                onClick={() => setShowPassword(!showPassword)}
                aria-label={
                  showPassword ? "Hide password" : "Show password"
                }
              >
                {showPassword ? "Hide" : "Show"}
              </button>
            </div>
          </div>

          {/* REMEMBER ME */}
          {mode === "login" && (
            <div className="auth-options">
              <label className="auth-remember">
                <input
                  type="checkbox"
                  checked={rememberMe}
                  onChange={(e) => setRememberMe(e.target.checked)}
                />

                <span>Remember me</span>
              </label>
            </div>
          )}

          {/* ERROR */}
          {error && (
            <div className="auth-error" role="alert">
              {error}
            </div>
          )}

          {/* SUBMIT */}
          <button
            type="submit"
            className="auth-submit"
            disabled={loading}
          >
            {loading
              ? "Please wait..."
              : mode === "login"
                ? "Sign In"
                : "Create Account"}

            {!loading && <span>→</span>}
          </button>

        </form>

        {/* SWITCH MODE */}
        <div className="auth-switch">
          {mode === "login"
            ? "Don't have an account?"
            : "Already have an account?"}

          <button
            type="button"
            onClick={switchMode}
          >
            {mode === "login" ? "Create account" : "Sign in"}
          </button>
        </div>

        {/* SECURITY */}
        <div className="auth-security">
          <span>🔒</span>
          Secure authentication powered by WebDoctor
        </div>

      </div>
    </main>
  );
}

export default Auth;