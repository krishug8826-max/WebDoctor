import { useEffect, useState } from "react";
import "./App.css";
import Auth from "./Auth";

const API_BASE = (import.meta.env.VITE_API_BASE_URL || "http://localhost:8081/api").replace(/\/$/, "");
const API_URL = `${API_BASE}/scan`;
const HISTORY_URL = `${API_BASE}/scan/history`;

/* =========================
   GENERAL HELPERS
========================= */

function formatUrl(value) {
  let formatted = value.trim();

  if (!/^https?:\/\//i.test(formatted)) {
    formatted = `https://${formatted}`;
  }

  return formatted;
}

function getScoreColor(score) {
  if (score >= 80) return "#22c55e";
  if (score >= 60) return "#f59e0b";
  return "#ef4444";
}

function getGrade(score) {
  if (score >= 90) return "A";
  if (score >= 80) return "B";
  if (score >= 70) return "C";
  if (score >= 60) return "D";
  return "F";
}

function getStatusColor(status) {
  const value = String(status || "").toUpperCase();

  if (
    value.includes("WORKING") ||
    value.includes("PASS") ||
    value.includes("SECURE") ||
    value.includes("SUCCESS") ||
    value.includes("COMPLETED") ||
    value.includes("REACHABLE")
  ) {
    return "success";
  }

  if (
    value.includes("BROKEN") ||
    value.includes("ERROR") ||
    value.includes("FAIL") ||
    value.includes("CRITICAL") ||
    value.includes("UNREACHABLE")
  ) {
    return "danger";
  }

  if (
    value.includes("REDIRECT") ||
    value.includes("WARNING") ||
    value.includes("SKIPPED") ||
    value.includes("AUTHENTICATION")
  ) {
    return "warning";
  }

  return "neutral";
}

function getFindingTitle(finding) {
  return (
    finding.name ||
    finding.endpoint ||
    finding.url ||
    finding.element ||
    finding.title ||
    "Website finding"
  );
}

function getFindingDescription(finding) {
  return (
    finding.description ||
    finding.message ||
    finding.recommendation ||
    "No additional information available."
  );
}

function getFindingStatus(finding) {
  return (
    finding.status ||
    finding.riskLevel ||
    finding.responseCategory ||
    "INFO"
  );
}

/* =========================
   LIGHTHOUSE HELPERS
========================= */

// Safely render Markdown links from Lighthouse descriptions.
function renderAuditDescription(description) {
  const parts = String(description || "").split(
    /(\[[^\]]+\]\(https?:\/\/[^)\s]+\))/g
  );

  return parts.map((part, index) => {
    const match = part.match(
      /^\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)$/
    );

    if (!match) {
      return part;
    }

    try {
      const url = new URL(match[2]);

      if (!["http:", "https:"].includes(url.protocol)) {
        return part;
      }

      return (
        <a
          key={index}
          href={url.href}
          target="_blank"
          rel="noopener noreferrer"
        >
          {match[1]}
        </a>
      );
    } catch {
      return part;
    }
  });
}

function getAuditStatus(score) {
  if (score === 1) return "Passed";
  if (score === 0) return "Failed";

  if (typeof score === "number") {
    return "Needs review";
  }

  return "Informational";
}

function getAuditStatusClass(score) {
  if (score === 1) return "success";
  if (score === 0) return "danger";

  return "warning";
}

function LighthouseAuditList({
  title,
  audits,
  emptyMessage,
  showScore = false,
}) {
  const auditList = Array.isArray(audits) ? audits : [];

  return (
    <div className="lighthouse-audit-section">
      <div className="lighthouse-audit-heading">
        <h4>{title}</h4>
        <span>{auditList.length}</span>
      </div>

      {auditList.length > 0 ? (
        auditList.map((audit, index) => (
          <details
            className="lighthouse-audit"
            key={audit.id || `${title}-${index}`}
          >
            <summary>
              <span>{audit.title || "Lighthouse Audit"}</span>

              <small
                className={
                  showScore
                    ? getAuditStatusClass(audit.score)
                    : ""
                }
              >
                {showScore
                  ? getAuditStatus(audit.score)
                  : audit.displayValue || "Review"}
              </small>
            </summary>

            {audit.description && (
              <p>
                {renderAuditDescription(audit.description)}
              </p>
            )}

            {showScore &&
              typeof audit.score === "number" && (
                <p className="lighthouse-audit-value">
                  Audit Score:{" "}
                  {Math.round(audit.score * 100)}/100
                </p>
              )}

            {audit.displayValue && (
              <p className="lighthouse-audit-value">
                Measured Value: {audit.displayValue}
              </p>
            )}
          </details>
        ))
      ) : (
        <p className="lighthouse-empty">{emptyMessage}</p>
      )}
    </div>
  );
}

/* =========================
   FINDINGS
========================= */

function getModuleFindings(result) {
  if (!result) return [];

  const modules = [
    {
      name: "Security",
      data: result.securityAnalysis?.issues,
    },
    {
      name: "Performance",
      data: result.performanceAnalysis?.findings,
    },
    {
      name: "SEO",
      data: result.seoAnalysis?.findings,
    },
    {
      name: "Broken Links",
      data: result.brokenLinkAnalysis?.findings,
    },
    {
      name: "JavaScript",
      data: result.javascriptAnalysis?.findings,
    },
    {
      name: "Mobile",
      data: result.mobileAnalysis?.findings,
    },
    {
      name: "API",
      data: result.apiAnalysis?.findings,
    },
  ];

  return modules.flatMap((module) =>
    (module.data || []).map((finding) => ({
      ...finding,
      module: module.name,
    }))
  );
}

function isIssue(finding) {
  const status = String(getFindingStatus(finding)).toUpperCase();

  return ![
    "WORKING",
    "PASS",
    "PASSED",
    "OK",
    "SUCCESS",
    "SECURE",
    "HEALTHY",
    "INFO",
    "INFORMATIONAL",
  ].includes(status);
}

function formatDate(dateValue) {
  if (!dateValue) return "N/A";

  const date = new Date(dateValue);

  if (Number.isNaN(date.getTime())) {
    return dateValue;
  }

  return date.toLocaleString("en-IN", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

async function getErrorMessage(response) {
  try {
    const data = await response.json();
    return data.message || data.error || `HTTP ${response.status}`;
  } catch {
    return `HTTP ${response.status}`;
  }
}

/* =========================
   SCORE CARD
========================= */

function ScoreCard({ title, score, grade, icon }) {
  const available = typeof score === "number";

  return (
    <div className="stat-card">
      <div className="stat-top">
        <div className="stat-icon">{icon}</div>
        <span className="stat-label">{title}</span>
      </div>

      <div className="stat-value">
        {available ? score : "N/A"}
        {available && <span className="stat-unit">/100</span>}
      </div>

      <div className="stat-bottom">
        <span
          className="stat-grade"
          style={{
            color: available ? getScoreColor(score) : "#94a3b8",
          }}
        >
          {available ? `Grade ${grade || getGrade(score)}` : "Not assessed"}
        </span>
      </div>

      <div className="stat-progress">
        <div
          className="stat-progress-fill"
          style={{
            width: available
              ? `${Math.max(0, Math.min(score, 100))}%`
              : "0%",
            background: available ? getScoreColor(score) : "#475569",
          }}
        />
      </div>
    </div>
  );
}

/* =========================
   MAIN APP
========================= */

function App() {
  // Authentication
 const [token, setToken] = useState(
  () =>
    localStorage.getItem("webdoctor_token") ||
    sessionStorage.getItem("webdoctor_token")
);

 const [user, setUser] = useState(() => {
  try {
    return JSON.parse(
      localStorage.getItem("webdoctor_user") ||
      sessionStorage.getItem("webdoctor_user") ||
      "null"
    );
  } catch {
    return null;
  }
});

  const authHeaders = (extra = {}) => ({
    ...extra,
    Authorization: `Bearer ${token}`,
  });

  const handleLogin = (data, rememberMe = true) => {
  const storage = rememberMe ? localStorage : sessionStorage;

  // Clear previous session data
  localStorage.removeItem("webdoctor_token");
  localStorage.removeItem("webdoctor_user");
  sessionStorage.removeItem("webdoctor_token");
  sessionStorage.removeItem("webdoctor_user");

  const userData = {
    userId: data.userId,
    name: data.name,
    email: data.email,
  };

  storage.setItem("webdoctor_token", data.token);
  storage.setItem("webdoctor_user", JSON.stringify(userData));

  setUser(userData);
  setToken(data.token);
};

  const handleLogout = () => {
    localStorage.removeItem("webdoctor_token");
localStorage.removeItem("webdoctor_user");
sessionStorage.removeItem("webdoctor_token");
sessionStorage.removeItem("webdoctor_user");

    setToken(null);
    setUser(null);
    setScanResult(null);
    setRecentScans([]);
    setSelectedScanId(null);
    setSelectedCompareScans([]);
    setShowComparison(false);
    setComparisonData([]);
    setUrl("");
    setError("");
    setHistoryError("");
    setActiveMenu("Dashboard");
  };
  const [url, setUrl] = useState("");
  const [scanning, setScanning] = useState(false);
  const [scanResult, setScanResult] = useState(null);

  const [error, setError] = useState("");
  const [activeMenu, setActiveMenu] = useState("Dashboard");

  // Database history
const [recentScans, setRecentScans] = useState([]);
const [historyLoading, setHistoryLoading] = useState(false);
const [historyError, setHistoryError] = useState("");

// Scan History Search, Sorting and Filters
const [historySearch, setHistorySearch] = useState("");
const [historySort, setHistorySort] = useState("newest");
const [historyStatus, setHistoryStatus] = useState("all");
const [historyDateRange, setHistoryDateRange] = useState("all");

// Server-side Pagination
const [currentPage, setCurrentPage] = useState(1);
const [recordsPerPage, setRecordsPerPage] = useState(10);
const [totalHistoryElements, setTotalHistoryElements] = useState(0);
const [totalHistoryPages, setTotalHistoryPages] = useState(0);
  // Previous scan details
  const [selectedScanId, setSelectedScanId] = useState(null);
  const [detailsLoading, setDetailsLoading] = useState(false);
// Scan Comparison
const [selectedCompareScans, setSelectedCompareScans] = useState([]);
const [showComparison, setShowComparison] = useState(false);
const [comparisonData, setComparisonData] = useState([]);
const [comparisonLoading, setComparisonLoading] = useState(false);

// Code Analyzer
const [codeLanguage, setCodeLanguage] = useState("javascript");
const [sourceCode, setSourceCode] = useState("");
const [codeAnalyzing, setCodeAnalyzing] = useState(false);
const [codeAnalysis, setCodeAnalysis] = useState(null);
const [codeAnalysisError, setCodeAnalysisError] = useState("");
// Change Password
const [currentPassword, setCurrentPassword] = useState("");
const [newPassword, setNewPassword] = useState("");
const [confirmNewPassword, setConfirmNewPassword] = useState("");
const [passwordLoading, setPasswordLoading] = useState(false);
const [passwordMessage, setPasswordMessage] = useState("");
const [passwordError, setPasswordError] = useState("");
// Toggle scan selection for comparison
const handleCompareSelection = (scanId) => {
  setSelectedCompareScans((previous) => {
    if (previous.includes(scanId)) {
      return previous.filter((id) => id !== scanId);
    }

    if (previous.length >= 2) {
      alert("You can compare only 2 scans at a time.");
      return previous;
    }

    return [...previous, scanId];
  });
};
// Load paginated database history
const loadScanHistory = async () => {
  if (!token) return;

  setHistoryLoading(true);
  setHistoryError("");

  try {
    const sortConfig = {
      newest: { sortBy: "scannedAt", sortDirection: "desc" },
      oldest: { sortBy: "scannedAt", sortDirection: "asc" },
      "score-high": { sortBy: "healthScore", sortDirection: "desc" },
      "score-low": { sortBy: "healthScore", sortDirection: "asc" },
    };

    const selectedSort = sortConfig[historySort] || sortConfig.newest;

    const params = new URLSearchParams({
      page: String(currentPage - 1),
      size: String(recordsPerPage),
      sortBy: selectedSort.sortBy,
      sortDirection: selectedSort.sortDirection,
      status: historyStatus,
      dateRange: historyDateRange,
    });

    if (historySearch.trim()) {
      params.set("search", historySearch.trim());
    }

    const response = await fetch(
      `${HISTORY_URL}/page?${params.toString()}`,
      { headers: authHeaders() }
    );

    if (!response.ok) {
      throw new Error(await getErrorMessage(response));
    }

    const data = await response.json();

    if (!Array.isArray(data.content)) {
      throw new Error("Invalid paginated scan history response.");
    }

    setRecentScans(data.content);
    setTotalHistoryElements(data.totalElements);
    setTotalHistoryPages(data.totalPages);

  } catch (err) {
    setHistoryError(
      err.message || "Unable to load scan history."
    );
  } finally {
    setHistoryLoading(false);
  }
};

 useEffect(() => {
  if (token) {
    loadScanHistory();
  } else {
    setRecentScans([]);
    setTotalHistoryElements(0);
    setTotalHistoryPages(0);
  }
}, [
  token,
  currentPage,
  recordsPerPage,
  historySearch,
  historySort,
  historyStatus,
  historyDateRange,
]);

// Server-side pagination results
const filteredScans = recentScans;

const totalPages = Math.max(1, totalHistoryPages);

const startIndex = (currentPage - 1) * recordsPerPage;

const paginatedScans = recentScans;

  // Open previous scan details
  const handleViewDetails = async (scanId) => {
    setDetailsLoading(true);
    setHistoryError("");
    setSelectedScanId(scanId);

    try {
      const response = await fetch(`${HISTORY_URL}/${scanId}`, { headers: authHeaders() });

      if (!response.ok) {
        throw new Error(await getErrorMessage(response));
      }

      const data = await response.json();

      if (!data.scanResponse) {
        throw new Error("Saved scan details are not available.");
      }

      setScanResult(data.scanResponse);
      setUrl(data.scanResponse.url || "");
      setActiveMenu("Dashboard");

      window.scrollTo({
        top: 0,
        behavior: "smooth",
      });
    } catch (err) {
      setHistoryError(
        err.message || "Unable to load previous scan details."
      );
    } finally {
      setDetailsLoading(false);
      setSelectedScanId(null);
    }
  };
    // Delete individual scan history
  const handleDeleteScan = async (scanId) => {
    const confirmed = window.confirm(
      "Are you sure you want to delete this scan history? This action cannot be undone."
    );

    if (!confirmed) {
      return;
    }

    setHistoryError("");

    try {
      const response = await fetch(`${HISTORY_URL}/${scanId}`, {
        method: "DELETE",
        headers: authHeaders(),
      });

      if (!response.ok) {
        throw new Error(await getErrorMessage(response));
      }

      await loadScanHistory();

      window.alert("Scan history deleted successfully.");
    } catch (err) {
      setHistoryError(
        err.message || "Unable to delete scan history."
      );
    }
  };
  // Compare two selected scans
  const handleCompareScans = async () => {
    if (selectedCompareScans.length !== 2) {
      alert("Please select exactly 2 scans to compare.");
      return;
    }

    setComparisonLoading(true);
    setHistoryError("");

    try {
      const results = await Promise.all(
        selectedCompareScans.map(async (scanId) => {
          const response = await fetch(`${HISTORY_URL}/${scanId}`, { headers: authHeaders() });

          if (!response.ok) {
            throw new Error(await getErrorMessage(response));
          }

          const data = await response.json();

          if (!data.scanResponse) {
            throw new Error(
              `Saved scan details are not available for scan ${scanId}.`
            );
          }

          return data;
        })
      );

      setComparisonData(results);
      setShowComparison(true);
    } catch (err) {
      setHistoryError(
        err.message || "Unable to compare selected scans."
      );
    } finally {
      setComparisonLoading(false);
    }
  };
  // Perform code analysis
  const handleCodeAnalysis = async (event) => {
    event?.preventDefault();

    if (!sourceCode.trim()) {
      setCodeAnalysisError("Please enter your source code.");
      return;
    }

    setCodeAnalyzing(true);
    setCodeAnalysisError("");
    setCodeAnalysis(null);

    try {
      const response = await fetch(`${API_BASE}/code-analyzer/analyze`, {
        method: "POST",
        headers: authHeaders({
          "Content-Type": "application/json",
        }),
        body: JSON.stringify({
          language: codeLanguage,
          code: sourceCode,
        }),
      });

      if (!response.ok) {
        throw new Error(await getErrorMessage(response));
      }

      const data = await response.json();
      setCodeAnalysis(data);

    } catch (err) {
      setCodeAnalysisError(
        err.message || "Unable to analyze the source code."
      );
    } finally {
      setCodeAnalyzing(false);
    }
  };
  // Change account password
const handleChangePassword = async (event) => {
  event.preventDefault();

  setPasswordMessage("");
  setPasswordError("");

  if (newPassword !== confirmNewPassword) {
    setPasswordError("New passwords do not match.");
    return;
  }

  if (newPassword.length < 8) {
    setPasswordError("New password must contain at least 8 characters.");
    return;
  }

  setPasswordLoading(true);

  try {
    const response = await fetch(`${API_BASE}/auth/change-password`, {
      method: "POST",
      headers: authHeaders({
        "Content-Type": "application/json",
      }),
      body: JSON.stringify({
        currentPassword,
        newPassword,
        confirmNewPassword,
      }),
    });

    if (!response.ok) {
      throw new Error(await getErrorMessage(response));
    }

    const data = await response.json();

    setPasswordMessage(data.message || "Password changed successfully.");

    setCurrentPassword("");
    setNewPassword("");
    setConfirmNewPassword("");

  } catch (err) {
    setPasswordError(
      err.message || "Unable to change password."
    );
  } finally {
    setPasswordLoading(false);
  }
};

  // Perform new website scan
  const handleScan = async (event) => {
    event?.preventDefault();

    if (!url.trim()) {
      setError("Please enter a website URL.");
      return;
    }

    let formattedUrl;

    try {
      formattedUrl = formatUrl(url);

      const parsedUrl = new URL(formattedUrl);

      if (!["http:", "https:"].includes(parsedUrl.protocol)) {
        setError("Only HTTP and HTTPS websites are supported.");
        return;
      }

      if (!parsedUrl.hostname.includes(".")) {
        setError("Please enter a valid public website domain.");
        return;
      }
    } catch {
      setError("Please enter a valid website URL.");
      return;
    }

    setScanning(true);
    setError("");
    setScanResult(null);

    try {
      const response = await fetch(API_URL, {
        method: "POST",
        headers: authHeaders({
          "Content-Type": "application/json",
        }),
        body: JSON.stringify({
          url: formattedUrl,
        }),
      });

      if (!response.ok) {
        throw new Error(await getErrorMessage(response));
      }

      const data = await response.json();

      setScanResult(data);
      setActiveMenu("Dashboard");

      await loadScanHistory();
    } catch (err) {
      setError(
        err.message ||
          "Unable to connect to WebDoctor backend. Please check whether the backend is running."
      );
    } finally {
      setScanning(false);
    }
  };

  // Score calculations
  const scores = [
    scanResult?.performanceAnalysis?.performanceScore,
    scanResult?.securityAnalysis?.securityScore,
    scanResult?.seoAnalysis?.seoScore,
    scanResult?.mobileAnalysis?.mobileScore,
    scanResult?.apiAnalysis?.healthStatus === "NOT_ASSESSED"
      ? null
      : scanResult?.apiAnalysis?.apiHealthScore,
  ].filter((score) => typeof score === "number");

  const averageScore =
    scores.length > 0
      ? Math.round(
          scores.reduce((total, score) => total + score, 0) /
            scores.length
        )
      : null;

  const findings = getModuleFindings(scanResult);
  const issues = findings.filter(isIssue);

  const api = scanResult?.apiAnalysis;
  const brokenLinks = scanResult?.brokenLinkAnalysis;
  const javascript = scanResult?.javascriptAnalysis;
  const mobile = scanResult?.mobileAnalysis;
  const ssl = scanResult?.sslAnalysis;

  // Lighthouse
  const lighthouse = scanResult?.lighthouseAnalysis;
  const lighthouseMobile = lighthouse?.mobile;
  const lighthouseDesktop = lighthouse?.desktop;

  const isDashboard = activeMenu === "Dashboard";

  if (!token) {
    return <Auth onLogin={handleLogin} />;
  }

  return (
    <div className="app-container">
      {/* SIDEBAR */}

      <aside className="sidebar">
        <div className="brand">
          <div className="brand-icon">✚</div>

          <div>
            <h2>WebDoctor</h2>
            <span>Website Diagnostics</span>
          </div>
        </div>

        <div className="sidebar-section">
          <p className="sidebar-heading">WORKSPACE</p>

          <button
            className={`nav-item ${
              activeMenu === "Dashboard" ? "active" : ""
            }`}
            onClick={() => setActiveMenu("Dashboard")}
          >
            <span>▦</span>
            Dashboard
          </button>

          <button
            className={`nav-item ${
              activeMenu === "New Scan" ? "active" : ""
            }`}
            onClick={() => {
              setActiveMenu("New Scan");

              document.querySelector(".scanner-card")?.scrollIntoView({
                behavior: "smooth",
              });
            }}
          >
            <span>⊕</span>
            New Scan
          </button>

          <button
            className={`nav-item ${
              activeMenu === "Scan History" ? "active" : ""
            }`}
            onClick={() => {
              setActiveMenu("Scan History");
              loadScanHistory();
            }}
          >
            <span>◷</span>
            Scan History
          </button>

          <button
            className={`nav-item ${
              activeMenu === "Reports" ? "active" : ""
            }`}
            onClick={() => setActiveMenu("Reports")}
          >
            <span>▤</span>
            Reports
          </button>
        </div>

        <div className="sidebar-section">
          <p className="sidebar-heading">ANALYSIS TOOLS</p>

          {[
            ["API Analyzer", "⌘"],
            ["Code Analyzer", "‹›"],
            ["Settings", "⚙"],
          ].map(([name, icon]) => (
            <button
              key={name}
              className={`nav-item ${
                activeMenu === name ? "active" : ""
              }`}
              onClick={() => setActiveMenu(name)}
            >
              <span>{icon}</span>
              {name}
            </button>
          ))}
        </div>

        <div className="sidebar-footer">
          <div className="sidebar-user">
            <div className="user-avatar">WD</div>

            <div>
              <strong>WebDoctor</strong>
              <span>Free Workspace</span>
            </div>
          </div>
        </div>
      </aside>

      {/* MAIN CONTENT */}

      <main className="main-content">
        <header className="topbar">
          <div className="breadcrumb">
            Workspace <span>/</span> {activeMenu}
          </div>

          <div className="topbar-right">
            <span className="system-status">
              <span className="status-dot" />
              Scanner Ready
            </span>

            <div className="topbar-avatar">
              {(user?.name || "U").charAt(0).toUpperCase()}
            </div>

            <span className="topbar-user-name">
              {user?.name || user?.email || "User"}
            </span>

            <button
              className="logout-button"
              onClick={handleLogout}
              title="Sign out of WebDoctor"
            >
              Logout
            </button>
          </div>
        </header>

        <div className="dashboard-content">
          <section className="welcome-section">
            <div>
              <p className="welcome-label">WEBDOCTOR WORKSPACE</p>

              <h1>
                {isDashboard
                  ? "Website Health Dashboard"
                  : activeMenu}
              </h1>

              <p className="welcome-description">
                Monitor, analyze and improve your website health.
              </p>
            </div>
          </section>

          {/* SCANNER */}

          {(isDashboard || activeMenu === "New Scan") && (
          <section className="scanner-card">
            <div className="scanner-header">
              <div>
                <h2>Website Scanner</h2>

                <p>
                  Enter your website URL to perform a comprehensive
                  diagnosis.
                </p>
              </div>

              <div className="scanner-badge">
                ✦ Live Analysis
              </div>
            </div>

            <form className="scanner-form" onSubmit={handleScan}>
              <div className="url-input-wrapper">
                <span className="url-icon">🌐</span>

                <input
                  type="text"
                  value={url}
                  onChange={(event) => {
                    setUrl(event.target.value);
                    setError("");
                  }}
                  placeholder="https://example.com"
                  disabled={scanning}
                />
              </div>

              <button
                className="scan-button"
                type="submit"
                disabled={scanning}
              >
                {scanning ? (
                  <>
                    <span className="spinner" />
                    Scanning...
                  </>
                ) : (
                  <>✦ Scan Website</>
                )}
              </button>
            </form>

            {error && (
              <div className="scan-error">
                <span>⚠</span>
                {error}
              </div>
            )}

            {scanning && (
              <div className="scan-loading">
                <div className="loading-line" />

                <p>
                  Analyzing website. This may take a little time...
                </p>
              </div>
            )}

            <div className="scanner-note">
              Supports public HTTP and HTTPS websites.
            </div>
          </section>
          )}

          {/* CODE ANALYZER */}

          {activeMenu === "Code Analyzer" && (
            <section className="code-analyzer-page">
              <div className="code-analyzer-card">
                <div className="scanner-header">
                  <div>
                    <h2>Code Analyzer</h2>
                    <p>
                      Analyze your JavaScript code for common security
                      and quality issues.
                    </p>
                  </div>
                  <div className="scanner-badge">✦ Static Analysis</div>
                </div>

                <form onSubmit={handleCodeAnalysis}>
                  <div className="code-analyzer-toolbar">
                    <label htmlFor="code-language">Programming Language</label>
                    <select
                      id="code-language"
                      value={codeLanguage}
                      onChange={(event) => setCodeLanguage(event.target.value)}
                      disabled={codeAnalyzing}
                    >
                      <option value="javascript">JavaScript</option>
                    </select>
                  </div>

                  <textarea
                    className="code-editor"
                    value={sourceCode}
                    onChange={(event) => {
                      setSourceCode(event.target.value);
                      setCodeAnalysisError("");
                    }}
                    placeholder={'// Paste your JavaScript code here\nconst message = "Hello WebDoctor";'}
                    spellCheck="false"
                    disabled={codeAnalyzing}
                  />

                  {codeAnalysisError && (
                    <div className="scan-error">
                      ⚠ {codeAnalysisError}
                    </div>
                  )}

                  <button
                    className="scan-button"
                    type="submit"
                    disabled={codeAnalyzing || !sourceCode.trim()}
                  >
                    {codeAnalyzing ? "Analyzing Code..." : "✦ Analyze Code"}
                  </button>
                </form>
              </div>

              {codeAnalysis && (
                <div className="code-analysis-results">
                  <div className="code-analysis-summary">
                    <div className="code-score-card">
                      <span>Code Health Score</span>
                      <strong>{codeAnalysis.score}/100</strong>
                    </div>
                    <div className="code-stat-card">
                      <span>Total Issues</span>
                      <strong>{codeAnalysis.totalIssues}</strong>
                    </div>
                    <div className="code-stat-card">
                      <span>Errors</span>
                      <strong>{codeAnalysis.errors}</strong>
                    </div>
                    <div className="code-stat-card">
                      <span>Warnings</span>
                      <strong>{codeAnalysis.warnings}</strong>
                    </div>
                    <div className="code-stat-card">
                      <span>Info</span>
                      <strong>{codeAnalysis.info}</strong>
                    </div>
                  </div>

                  <div className="code-findings-card">
                    <h3>Analysis Findings</h3>

                    {codeAnalysis.findings.length === 0 ? (
                      <div className="empty-state">
                        <h3>No issues detected!</h3>
                        <p>Your code passed all currently enabled checks.</p>
                      </div>
                    ) : (
                      codeAnalysis.findings.map((finding, index) => (
                        <article
                          className="code-finding"
                          key={`${finding.ruleId}-${finding.line}-${index}`}
                        >
                          <div className="code-finding-heading">
                            <strong>{finding.title}</strong>
                            <span className={`code-severity ${finding.severity.toLowerCase()}`}>
                              {finding.severity}
                            </span>
                          </div>
                          <p>{finding.message}</p>
                          <span className="code-finding-line">
                            Rule: {finding.ruleId} · Line: {finding.line}
                          </span>
                          <div className="code-suggestion">
                            <strong>Suggestion:</strong> {finding.suggestion}
                          </div>
                        </article>
                      ))
                    )}
                  </div>
                </div>
              )}
            </section>
          )}

          {/* DASHBOARD */}

          {isDashboard && (
            <>
              {!scanResult && !scanning && (
                <div className="empty-state">
                  <div className="empty-icon">◷</div>

                  <h3>Ready to diagnose your website?</h3>

                  <p>
                    Enter a website URL above to see its real performance,
                    security, SEO and other analysis results.
                  </p>
                </div>
              )}

              {scanResult && (
  <div className="print-report">
                  {/* PROFESSIONAL PDF REPORT HEADER */}
                  <section className="report-header">
                    <div className="report-brand">
                      <div className="report-brand-icon">✚</div>
                      <div>
                        <h1>WebDoctor</h1>
                        <span>WEBSITE HEALTH &amp; SECURITY ANALYSIS</span>
                      </div>
                    </div>

                    <div className="report-title">
                      <span>WEBSITE AUDIT REPORT</span>
                      <h2>Website Health Assessment</h2>
                      <p>Comprehensive technical analysis and recommendations</p>
                    </div>

                    <div className="report-meta">
                      <div>
                        <span>WEBSITE</span>
                        <strong>{scanResult.url}</strong>
                      </div>
                      <div>
                        <span>REPORT GENERATED</span>
                        <strong>
                          {new Date().toLocaleString("en-IN", {
                            dateStyle: "medium",
                            timeStyle: "short"
                          })}
                        </strong>
                      </div>
                      <div>
                        <span>OVERALL HEALTH SCORE</span>
                        <strong>
                          {averageScore !== null ? `${averageScore}/100` : "N/A"}
                        </strong>
                      </div>
                    </div>
                  </section>
                  {/* SCAN SUMMARY */}

                  <section className="scan-summary">
                    <div>
                      <span className="summary-label">
                        LAST SCANNED WEBSITE
                      </span>

                      <h3>{scanResult.url}</h3>

                      <p>
                        HTTP Status: {scanResult.statusCode || "N/A"}
                        {" · "}
                        Response Time: {scanResult.responseTimeMs ?? "N/A"} ms
                      </p>
                    </div>

                    <div
                      className={`scan-status ${
                        scanResult.reachable ? "success" : "danger"
                      }`}
                    >
                      {scanResult.reachable
                        ? "● Reachable"
                        : "● Unreachable"}
                    </div>
                                        <button
                      className="download-report-button"
                      onClick={() => window.print()}
                      type="button"
                    >
                      ↓ Download PDF Report
                    </button>
                  </section>

                  {/* HEALTH OVERVIEW */}

                  <section className="section-block">
                    <div className="section-heading">
                      <div>
                        <h2>Health Overview</h2>

                        <p>
                          Actual results returned by the WebDoctor backend.
                        </p>
                      </div>
                    </div>

                    <div className="stats-grid">
                      <ScoreCard
                        title="Performance"
                        score={
                          scanResult.performanceAnalysis?.performanceScore
                        }
                        grade={scanResult.performanceAnalysis?.grade}
                        icon="⚡"
                      />

                      <ScoreCard
                        title="Security"
                        score={scanResult.securityAnalysis?.securityScore}
                        grade={scanResult.securityAnalysis?.grade}
                        icon="🛡️"
                      />

                      <ScoreCard
                        title="SEO Health"
                        score={scanResult.seoAnalysis?.seoScore}
                        grade={scanResult.seoAnalysis?.grade}
                        icon="🔍"
                      />

                      <ScoreCard
                        title="API Health"
                        score={
                          api?.healthStatus === "NOT_ASSESSED"
                            ? null
                            : api?.apiHealthScore
                        }
                        grade={api?.grade}
                        icon="🔌"
                      />
                    </div>
                  </section>

                  {/* LIGHTHOUSE PERFORMANCE */}

                  <section className="section-block lighthouse-section">
                    <div className="section-heading">
                      <div>
                        <h2>Lighthouse Performance</h2>
                        <p>
                          Detailed browser-based audit for Mobile and Desktop.
                        </p>
                      </div>

                      <span className="period-label">
                        {lighthouse?.status || "NOT AVAILABLE"}
                      </span>
                    </div>

                    <div className="stats-grid lighthouse-grid">
                      {[
                        {
                          title: "Mobile Performance",
                          data: lighthouseMobile,
                        },
                        {
                          title: "Desktop Performance",
                          data: lighthouseDesktop,
                        },
                      ].map(({ title, data }) => (
                        <div
                          className="stat-card lighthouse-card"
                          key={title}
                        >
                          <div className="stat-top">
                            <h3>{title}</h3>

                            <span className="period-label">
                              {data?.status || "NOT RUN"}
                            </span>
                          </div>

                          {/* PERFORMANCE SCORE */}

                          <div className="score-row">
                            <strong
                              style={{
                                color:
                                  typeof data?.performanceScore === "number"
                                    ? getScoreColor(data.performanceScore)
                                    : "#8594ac",
                              }}
                            >
                              {data?.performanceScore ?? "N/A"}
                            </strong>

                            <span>/ 100</span>
                          </div>

                          {/* CORE METRICS */}

                          <div className="lighthouse-metrics">
                            {[
                              ["First Contentful Paint", data?.fcp],
                              ["Largest Contentful Paint", data?.lcp],
                              ["Total Blocking Time", data?.tbt],
                              ["Cumulative Layout Shift", data?.cls],
                              ["Speed Index", data?.speedIndex],
                            ].map(([label, metric]) => (
                              <div
                                className="lighthouse-metric"
                                key={label}
                              >
                                <span>{label}</span>

                                <strong>
                                  {metric?.displayValue ??
                                    (metric?.value != null
                                      ? metric.value
                                      : "N/A")}
                                </strong>
                              </div>
                            ))}
                          </div>

                          {/* CATEGORY SCORES */}

                          <div className="lighthouse-category-grid">
                            {[
                              [
                                "Accessibility",
                                data?.accessibilityScore,
                              ],
                              [
                                "Best Practices",
                                data?.bestPracticesScore,
                              ],
                              ["SEO", data?.seoScore],
                            ].map(([label, score]) => (
                              <div
                                className="lighthouse-category"
                                key={label}
                              >
                                <span>{label}</span>

                                <strong
                                  style={{
                                    color:
                                      typeof score === "number"
                                        ? getScoreColor(score)
                                        : "#8594ac",
                                  }}
                                >
                                  {score ?? "N/A"}
                                </strong>

                                <small>/ 100</small>
                              </div>
                            ))}
                          </div>

                          {/* OPPORTUNITIES */}

                          <LighthouseAuditList
                            title="Opportunities"
                            audits={data?.opportunities}
                            emptyMessage="No performance opportunities identified."
                          />

                          {/* DIAGNOSTICS */}

                          <LighthouseAuditList
                            title="Diagnostics"
                            audits={data?.diagnostics}
                            emptyMessage="No diagnostic information available."
                            showScore
                          />

                          {/* NEEDS ATTENTION */}

                          <LighthouseAuditList
                            title="Needs Attention"
                            audits={data?.failedAudits}
                            emptyMessage="No audits need attention."
                            showScore
                          />

                          {data?.finalUrl && (
                            <div className="lighthouse-final-url">
                              Audited URL: {data.finalUrl}
                            </div>
                          )}
                        </div>
                      ))}
                    </div>

                    {lighthouse?.message && (
                      <p className="lighthouse-message">
                        {lighthouse.message}
                      </p>
                    )}
                  </section>
                                    {/* BOTTOM GRID */}

                  <section className="bottom-grid">
                    {/* WEBSITE HEALTH SCORE */}

                    <div className="panel-card health-score-card">
                      <div className="panel-heading">
                        <div>
                          <h2>Website Health Score</h2>
                          <p>Overall diagnostic score</p>
                        </div>
                      </div>

                      <div className="health-score-content">
                        <div
                          className="health-score-circle"
                          style={{
                            "--score-color":
                              averageScore !== null
                                ? getScoreColor(averageScore)
                                : "#64748b",
                          }}
                        >
                          <div>
                            <strong>
                              {averageScore ?? "N/A"}
                            </strong>
                            <span>/100</span>
                          </div>
                        </div>

                        <div className="health-score-info">
                          <h3>
                            {averageScore === null
                              ? "Not Available"
                              : averageScore >= 90
                                ? "Excellent Health"
                                : averageScore >= 80
                                  ? "Good Health"
                                  : averageScore >= 60
                                    ? "Needs Improvement"
                                    : "Poor Health"}
                          </h3>

                          <p>
                            {averageScore === null
                              ? "Not enough assessment data is available."
                              : "Combined score based on the available website analysis modules."}
                          </p>
                        </div>
                      </div>
                    </div>

                    {/* ISSUES DETECTED */}

                    <div className="panel-card issues-card">
                      <div className="panel-heading">
                        <div>
                          <h2>Issues Detected</h2>
                          <p>Findings requiring attention</p>
                        </div>

                        <span className="issue-count">
                          {issues.length}
                        </span>
                      </div>

                      {issues.length > 0 ? (
                        <div className="issues-list">
                          {issues.slice(0, 6).map((finding, index) => (
                            <div
                              className="issue-item"
                              key={`${finding.module}-${index}`}
                            >
                              <span
                                className={`issue-indicator ${getStatusColor(
                                  getFindingStatus(finding)
                                )}`}
                              />

                              <div className="issue-details">
                                <strong>
                                  {getFindingTitle(finding)}
                                </strong>

                                <span>
                                  {finding.module} ·{" "}
                                  {getFindingDescription(finding)}
                                </span>
                              </div>

                              <small>
                                {getFindingStatus(finding)}
                              </small>
                            </div>
                          ))}
                        </div>
                      ) : (
                        <div className="no-issues">
                          <span>✓</span>
                          <p>No major issues detected in the available findings.</p>
                        </div>
                      )}
                    </div>
                  </section>

                  {/* DETAILED ANALYSIS */}

                  <section className="section-block detailed-analysis-section">
                    <div className="section-heading">
                      <div>
                        <h2>Detailed Analysis</h2>
                        <p>
                          Detailed results from individual diagnostic modules.
                        </p>
                      </div>
                    </div>

                    <div className="analysis-grid">
                      {/* SSL ANALYSIS */}

                      <div className="panel-card analysis-card">
                        <div className="analysis-card-heading">
                          <div className="analysis-icon">🔒</div>

                          <div>
                            <h3>SSL & Security</h3>
                            <p>Certificate and connection security</p>
                          </div>
                        </div>

                        <div className="analysis-result">
                          <span>Status</span>

                          <strong
                            className={`status-text ${getStatusColor(
                              ssl?.status || ssl?.sslStatus
                            )}`}
                          >
                            {ssl?.status ||
                              ssl?.sslStatus ||
                              "Not Available"}
                          </strong>
                        </div>

                        {ssl?.issuer && (
                          <div className="analysis-result">
                            <span>Issuer</span>
                            <strong>{ssl.issuer}</strong>
                          </div>
                        )}

                        {ssl?.expiryDate && (
                          <div className="analysis-result">
                            <span>Expiry Date</span>
                            <strong>{formatDate(ssl.expiryDate)}</strong>
                          </div>
                        )}

                        {ssl?.message && (
                          <p className="analysis-description">
                            {ssl.message}
                          </p>
                        )}
                      </div>

                      {/* BROKEN LINKS */}

                      <div className="panel-card analysis-card">
                        <div className="analysis-card-heading">
                          <div className="analysis-icon">🔗</div>

                          <div>
                            <h3>Broken Links</h3>
                            <p>Link accessibility analysis</p>
                          </div>
                        </div>

                        <div className="analysis-highlight">
                          <strong>
                            {brokenLinks?.brokenLinkCount ??
                              brokenLinks?.totalBrokenLinks ??
                              0}
                          </strong>

                          <span>Broken Links</span>
                        </div>

                        <div className="analysis-result">
                          <span>Total Checked</span>

                          <strong>
                            {brokenLinks?.totalLinksChecked ??
                              brokenLinks?.totalLinks ??
                              "N/A"}
                          </strong>
                        </div>

                        {brokenLinks?.message && (
                          <p className="analysis-description">
                            {brokenLinks.message}
                          </p>
                        )}
                      </div>

                      {/* JAVASCRIPT */}

                      <div className="panel-card analysis-card">
                        <div className="analysis-card-heading">
                          <div className="analysis-icon">JS</div>

                          <div>
                            <h3>JavaScript Analysis</h3>
                            <p>Client-side script analysis</p>
                          </div>
                        </div>

                        <div className="analysis-highlight">
                          <strong>
                            {javascript?.errorCount ??
                              javascript?.totalErrors ??
                              0}
                          </strong>

                          <span>JavaScript Errors</span>
                        </div>

                        <div className="analysis-result">
                          <span>Status</span>

                          <strong
                            className={`status-text ${getStatusColor(
                              javascript?.status
                            )}`}
                          >
                            {javascript?.status || "Not Available"}
                          </strong>
                        </div>

                        {javascript?.message && (
                          <p className="analysis-description">
                            {javascript.message}
                          </p>
                        )}
                      </div>

                      {/* MOBILE ANALYSIS */}

                      <div className="panel-card analysis-card">
                        <div className="analysis-card-heading">
                          <div className="analysis-icon">📱</div>

                          <div>
                            <h3>Mobile Compatibility</h3>
                            <p>Responsive website analysis</p>
                          </div>
                        </div>

                        <div className="analysis-highlight">
                          <strong>
                            {mobile?.mobileScore ??
                              mobile?.score ??
                              "N/A"}
                          </strong>

                          <span>
                            {typeof (mobile?.mobileScore ?? mobile?.score) ===
                            "number"
                              ? "Mobile Score"
                              : "Score unavailable"}
                          </span>
                        </div>

                        <div className="analysis-result">
                          <span>Viewport</span>

                          <strong>
                            {mobile?.viewportStatus ||
                              mobile?.viewport ||
                              "N/A"}
                          </strong>
                        </div>

                        {mobile?.message && (
                          <p className="analysis-description">
                            {mobile.message}
                          </p>
                        )}
                      </div>

                      {/* API ANALYSIS */}

                      <div className="panel-card analysis-card">
                        <div className="analysis-card-heading">
                          <div className="analysis-icon">⌘</div>

                          <div>
                            <h3>API Analysis</h3>
                            <p>Observable API endpoint analysis</p>
                          </div>
                        </div>

                        <div className="analysis-result">
                          <span>Status</span>

                          <strong
                            className={`status-text ${getStatusColor(
                              api?.healthStatus || api?.status
                            )}`}
                          >
                            {api?.healthStatus ||
                              api?.status ||
                              "Not Available"}
                          </strong>
                        </div>

                        <div className="analysis-result">
                          <span>Endpoints Checked</span>

                          <strong>
                            {api?.totalEndpointsChecked ??
                              api?.totalEndpoints ??
                              "N/A"}
                          </strong>
                        </div>

                        {api?.message && (
                          <p className="analysis-description">
                            {api.message}
                          </p>
                        )}
                      </div>

                      {/* PERFORMANCE ANALYSIS */}

                      <div className="panel-card analysis-card">
                        <div className="analysis-card-heading">
                          <div className="analysis-icon">⚡</div>

                          <div>
                            <h3>Performance Analysis</h3>
                            <p>Website response and loading performance</p>
                          </div>
                        </div>

                        <div className="analysis-highlight">
                          <strong>
                            {scanResult.responseTimeMs ?? "N/A"}
                          </strong>

                          <span>Response Time (ms)</span>
                        </div>

                        <div className="analysis-result">
                          <span>Performance Score</span>

                          <strong>
                            {scanResult.performanceAnalysis
                              ?.performanceScore ?? "N/A"}
                          </strong>
                        </div>

                        {scanResult.performanceAnalysis?.message && (
                          <p className="analysis-description">
                            {scanResult.performanceAnalysis.message}
                          </p>
                        )}
                      </div>
                    </div>
                  </section>

                  {/* RECOMMENDATIONS */}

                 <section className="section-block recommendations-section">
                    <div className="section-heading">
                      <div>
                        <h2>Recommendations</h2>
                        <p>
                          Suggested improvements based on scan findings.
                        </p>
                      </div>
                    </div>

                    <div className="recommendations-card">
                      {issues.length > 0 ? (
                        issues.slice(0, 8).map((finding, index) => (
                          <div
                            className="recommendation-item"
                            key={`recommendation-${index}`}
                          >
                            <div className="recommendation-icon">
                              ✦
                            </div>

                            <div>
                              <strong>
                                {getFindingTitle(finding)}
                              </strong>

                              <p>
                                {finding.recommendation ||
                                  getFindingDescription(finding)}
                              </p>

                              <span>{finding.module}</span>
                            </div>
                          </div>
                        ))
                      ) : (
                        <div className="no-issues">
                          <span>✓</span>
                          <p>
                            No additional recommendations are available.
                          </p>
                        </div>
                      )}
                    </div>
                  </section>
                 </div>
              )}
            </>
          )}

          {/* SCAN HISTORY */}

          {(activeMenu === "Scan History" ||
            activeMenu === "Dashboard") && (
            <section className="section-block history-section">
              <div className="section-heading">
                <div>
                  <h2>Scan History</h2>

                  <p>
                    Previously scanned websites saved in your database.
                  </p>
                </div>

                <button
                  className="refresh-button"
                  onClick={loadScanHistory}
                  disabled={historyLoading}
                >
                  {historyLoading ? "Loading..." : "↻ Refresh"}
                </button>
              </div>

              {historyError && (
                <div className="scan-error">
                  ⚠ {historyError}
                </div>
              )}
<div className="history-controls">
  <div className="history-search">
    <span>◷</span>
    <input
      type="text"
      placeholder="Search website URL..."
      value={historySearch}
      onChange={(e) => {
  setHistorySearch(e.target.value);
  setCurrentPage(1);
}}
    />
    {historySearch && (
      <button
        type="button"
        onClick={() => {
  setHistorySearch("");
  setCurrentPage(1);
}}
        aria-label="Clear search"
      >
        ×
      </button>
    )}
  </div>

  <select
  className="history-sort"
  value={historySort}
  onChange={(e) => {
    setHistorySort(e.target.value);
    setCurrentPage(1);
  }}
>
  <option value="newest">Newest First</option>
  <option value="oldest">Oldest First</option>
  <option value="score-high">Health Score: High to Low</option>
  <option value="score-low">Health Score: Low to High</option>
</select>

<select
  className="history-sort"
  value={historyStatus}
  onChange={(e) => {
    setHistoryStatus(e.target.value);
    setCurrentPage(1);
  }}
>
  <option value="all">All Status</option>
  <option value="successful">Successful</option>
  <option value="failed">Failed</option>
</select>

<select
  className="history-sort"
  value={historyDateRange}
  onChange={(e) => {
    setHistoryDateRange(e.target.value);
    setCurrentPage(1);
  }}
>
  <option value="all">All Time</option>
  <option value="today">Today</option>
  <option value="7days">Last 7 Days</option>
  <option value="30days">Last 30 Days</option>
</select>
</div>
<div className="comparison-toolbar">
  <div>
    <strong>Scan Comparison</strong>
    <p>
      Select any 2 scans to compare their performance.
      ({selectedCompareScans.length}/2 selected)
    </p>
  </div>

  <button
    className="compare-button"
    onClick={handleCompareScans}
    disabled={
      selectedCompareScans.length !== 2 || comparisonLoading
    }
  >
    {comparisonLoading ? "Comparing..." : "Compare Selected"}
  </button>
</div>
              <div className="history-table-wrapper">
                <table className="history-table">
                  <thead>
                    <tr>
  <th>Select</th>
  <th>Website</th>
                      <th>Health Score</th>
                      <th>Status</th>
                      <th>Scan Date</th>
                      <th>Action</th>
                    </tr>
                  </thead>

                  <tbody>
                    {recentScans.length > 0 ? (
                      paginatedScans.map((scan) => (
                        <tr key={scan.id}>
  <td>
    <input
      type="checkbox"
      checked={selectedCompareScans.includes(scan.id)}
      onChange={() => handleCompareSelection(scan.id)}
      aria-label={`Select scan for comparison: ${scan.url}`}
    />
  </td>

  <td>
    <div className="history-website">
                              <span className="history-website-icon">
                                🌐
                              </span>

                              <span>
                                {scan.url || "Unknown Website"}
                              </span>
                            </div>
                          </td>

                          <td>
                            <span
  className={`health-score-badge ${
    typeof scan.healthScore !== "number"
      ? "score-unknown"
      : scan.healthScore >= 80
      ? "score-excellent"
      : scan.healthScore >= 60
      ? "score-warning"
      : "score-critical"
  }`}
>
  {typeof scan.healthScore === "number"
    ? `${scan.healthScore}/100`
    : "N/A"}
</span>
                          </td>

                          <td>
                            <span
                              className={`history-status ${
                                getStatusColor(scan.status)
                              }`}
                            >
                              {scan.status || "COMPLETED"}
                            </span>
                          </td>

                          <td>{formatDate(scan.scannedAt || scan.createdAt)}</td>

                          <td>
  <div className="history-action-buttons">
    <button
      className="view-button"
      onClick={() => handleViewDetails(scan.id)}
      disabled={detailsLoading}
    >
      {detailsLoading &&
      selectedScanId === scan.id
        ? "Loading..."
        : "View Details"}
    </button>

    <button
      className="delete-button"
      onClick={() => handleDeleteScan(scan.id)}
    >
      Delete
    </button>
  </div>
</td>
                        </tr>
                      ))
                    ) : (
                      <tr>
                        <td colSpan="6" className="history-empty">
                          {historyLoading
                            ? "Loading scan history..."
                            : "No scan history available yet."}
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
                                      {/* SCAN COMPARISON RESULTS */}
              {showComparison && comparisonData.length === 2 && (
                <div className="comparison-panel">
                  <div className="comparison-heading">
                    <div>
                      <h2>Scan Comparison Results</h2>
                      <p>
                        Compare the results of your two selected website scans.
                      </p>
                    </div>

                    <button
                      className="comparison-close"
                      onClick={() => setShowComparison(false)}
                    >
                      Close
                    </button>
                  </div>

                  <div className="comparison-table-wrapper">
                    <table className="comparison-table">
                      <thead>
                        <tr>
                          <th>Metric</th>
                          <th>Scan 1</th>
                          <th>Scan 2</th>
                        </tr>
                      </thead>

                      <tbody>
                        {[
                          {
                            label: "Website",
                            getValue: (data) =>
                              data.scanResponse?.url || "N/A",
                          },
                          {
                            label: "Overall Health Score",
                            getValue: (data) =>
                              recentScans.find(
                                (scan) => scan.id === data.id
                              )?.healthScore ?? "N/A",
                          },
                          {
                            label: "Performance Score",
                            getValue: (data) =>
                              data.scanResponse?.performanceAnalysis
                                ?.performanceScore ?? "N/A",
                          },
                          {
                            label: "Security Score",
                            getValue: (data) =>
                              data.scanResponse?.securityAnalysis
                                ?.securityScore ?? "N/A",
                          },
                          {
                            label: "SEO Score",
                            getValue: (data) =>
                              data.scanResponse?.seoAnalysis?.seoScore ??
                              "N/A",
                          },
                          {
                            label: "Mobile Score",
                            getValue: (data) =>
                              data.scanResponse?.mobileAnalysis
                                ?.mobileScore ?? "N/A",
                          },
                          {
                            label: "Lighthouse Mobile",
                            getValue: (data) =>
                              data.scanResponse?.lighthouseAnalysis?.mobile
                                ?.performanceScore ?? "N/A",
                          },
                          {
                            label: "Lighthouse Desktop",
                            getValue: (data) =>
                              data.scanResponse?.lighthouseAnalysis?.desktop
                                ?.performanceScore ?? "N/A",
                          },
                          {
                            label: "Response Time",
                            getValue: (data) =>
                              data.scanResponse?.responseTimeMs != null
                                ? `${data.scanResponse.responseTimeMs} ms`
                                : "N/A",
                          },
                          {
                            label: "HTTP Status",
                            getValue: (data) =>
                              data.scanResponse?.statusCode ?? "N/A",
                          },
                          {
                            label: "Scan Date",
                            getValue: (data) =>
                              formatDate(data.scannedAt),
                          },
                                                ].map((metric, index) => {
                          const firstValue = metric.getValue(comparisonData[0]);
                          const secondValue = metric.getValue(comparisonData[1]);

                          const comparableMetrics = [
                            "Overall Health Score",
                            "Performance Score",
                            "Security Score",
                            "SEO Score",
                            "Mobile Score",
                            "Lighthouse Mobile",
                            "Lighthouse Desktop",
                            "Response Time",
                          ];

                          const isComparable = comparableMetrics.includes(
                            metric.label
                          );

                          const firstNumber = Number(
                            String(firstValue).replace(/[^\d.-]/g, "")
                          );

                          const secondNumber = Number(
                            String(secondValue).replace(/[^\d.-]/g, "")
                          );

                          const isValid =
                            isComparable &&
                            firstValue !== "N/A" &&
                            secondValue !== "N/A" &&
                            Number.isFinite(firstNumber) &&
                            Number.isFinite(secondNumber);

                          const lowerIsBetter =
                            metric.label === "Response Time";

                          let statusClass = "";
                          let indicator = "";

                          if (isValid) {
                            if (firstNumber === secondNumber) {
                              statusClass = "comparison-neutral";
                              indicator = "— No Change";
                            } else {
                              const improved = lowerIsBetter
                                ? secondNumber < firstNumber
                                : secondNumber > firstNumber;

                              statusClass = improved
                                ? "comparison-improved"
                                : "comparison-decreased";

                              indicator = improved
                                ? "↑ Improved"
                                : "↓ Decreased";
                            }
                          }

                          return (
                            <tr key={index}>
                              <td>{metric.label}</td>

                              <td>{firstValue}</td>

                              <td>
                                <span className={statusClass}>
                                  {secondValue}
                                </span>

                                {isValid && (
                                  <span className={`comparison-indicator ${statusClass}`}>
                                    {indicator}
                                  </span>
                                )}
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}    

            {/* SCAN HISTORY PAGINATION */}
{totalHistoryElements > 0 && (
  <div className="history-pagination">

    <div className="pagination-info">
      Showing {startIndex + 1}–
      {Math.min(
        startIndex + recentScans.length,
        totalHistoryElements
      )} of {totalHistoryElements} records
    </div>

    <div className="pagination-buttons">

      {/* RECORDS PER PAGE */}
      <select
        className="history-sort"
        value={recordsPerPage}
        onChange={(e) => {
          setRecordsPerPage(Number(e.target.value));
          setCurrentPage(1);
        }}
        aria-label="Records per page"
      >
        <option value={10}>10 per page</option>
        <option value={20}>20 per page</option>
        <option value={50}>50 per page</option>
      </select>

      <button
        className="pagination-button"
        onClick={() =>
          setCurrentPage((page) => Math.max(1, page - 1))
        }
        disabled={currentPage === 1 || historyLoading}
      >
        ← Previous
      </button>

      <span className="pagination-page">
        Page {currentPage} of {totalPages}
      </span>

      <button
        className="pagination-button"
        onClick={() =>
          setCurrentPage((page) => Math.min(totalPages, page + 1))
        }
        disabled={currentPage === totalPages || historyLoading}
      >
        Next →
      </button>

    </div>
  </div>
)}

            
            </section>
          )}
{/* API ANALYZER */}

{activeMenu === "API Analyzer" && (
  <section className="section-block">
    {!scanResult?.apiAnalysis ? (
      <div className="empty-state">
        <h3>No API Analysis Available</h3>
        <p>Run a website scan first to view API analysis.</p>
        <button
          className="scan-button"
          onClick={() => setActiveMenu("New Scan")}
        >
          Start New Scan
        </button>
      </div>
    ) : (
      (() => {
        const api = scanResult.apiAnalysis;

        return (
          <>
            <div className="section-heading">
              <div>
                <h2>API Health Overview</h2>
                <p>API endpoints discovered during website scanning.</p>
              </div>
            </div>

            <div className="analysis-grid">
              <div className="panel-card analysis-card">
                <h3>API Health Score</h3>
                <div className="analysis-highlight">
                  <strong>{api.apiHealthScore}/100</strong>
                  <span>Grade: {api.grade || "N/A"}</span>
                </div>
                <div className="analysis-result">
                  <span>Health Status</span>
                  <strong className={`status-text ${getStatusColor(api.healthStatus)}`}>
                    {api.healthStatus || "N/A"}
                  </strong>
                </div>
              </div>

              <div className="panel-card analysis-card">
                <h3>Endpoint Statistics</h3>
                {[
                  ["Total Endpoints", api.totalEndpoints],
                  ["Checked", api.checkedEndpoints],
                  ["Working", api.workingEndpoints],
                  ["Broken", api.brokenEndpoints],
                  ["Redirected", api.redirectedEndpoints],
                  ["Authentication Required", api.authenticationRequiredEndpoints],
                  ["Errors", api.errorEndpoints],
                  ["Skipped", api.skippedEndpoints],
                ].map(([label, value]) => (
                  <div className="analysis-result" key={label}>
                    <span>{label}</span>
                    <strong>{value ?? 0}</strong>
                  </div>
                ))}
              </div>

              <div className="panel-card analysis-card">
                <h3>Response Performance</h3>
                {[
                  ["Average Response", api.averageResponseTimeMs + " ms"],
                  ["Fastest Response", api.fastestResponseTimeMs + " ms"],
                  ["Slowest Response", api.slowestResponseTimeMs + " ms"],
                ].map(([label, value]) => (
                  <div className="analysis-result" key={label}>
                    <span>{label}</span>
                    <strong>{value}</strong>
                  </div>
                ))}
              </div>

              <div className="panel-card analysis-card">
                <h3>JSON Validation</h3>
                {[
                  ["JSON Endpoints", api.jsonEndpoints],
                  ["Invalid JSON", api.invalidJsonEndpoints],
                  ["JavaScript API Patterns Detected", api.apiPatternsDetected],
                  ["Scripts Found", api.totalScriptsFound],
                  ["Inline Scripts Scanned", api.inlineScriptsScanned],
                  ["External Scripts Found", api.externalScriptsFound],
                  ["External Scripts Downloaded", api.externalScriptsDownloaded],
                ].map(([label, value]) => (
                  <div className="analysis-result" key={label}>
                    <span>{label}</span>
                    <strong>{value ?? 0}</strong>
                  </div>
                ))}
              </div>
            </div>

            <div className="section-heading">
              <div>
                <h2>Detected API Endpoints</h2>
                <p>Observable endpoint responses and validation details.</p>
              </div>
            </div>

            <div className="panel-card analysis-card">
              {(api.findings || []).length === 0 ? (
                <p>No endpoint findings available.</p>
              ) : (
                api.findings.map((item, index) => (
                  <div className="api-endpoint-item" key={index}>
                    <strong>{item.method || "GET"} {item.endpoint}</strong>
                    <div className="analysis-result">
                      <span>Status</span>
                      <strong>{item.status || "N/A"} ({item.statusCode || 0})</strong>
                    </div>
                    <div className="analysis-result">
                      <span>Response Time</span>
                      <strong>{item.responseTimeMs} ms</strong>
                    </div>
                    <div className="analysis-result">
                      <span>Content Type</span>
                      <strong>{item.contentType || "N/A"}</strong>
                    </div>
                    <div className="analysis-result">
                      <span>JSON Valid</span>
                      <strong>{item.jsonResponse ? (item.validJson ? "Yes" : "No") : "Not JSON"}</strong>
                    </div>
                    <p>{item.message}</p>
                    {item.recommendation && (
                      <p>{item.recommendation}</p>
                    )}
                  </div>
                ))
              )}
            </div>

            <div className="section-heading">
              <div>
                <h2>Recommendations</h2>
              </div>
            </div>

            <div className="panel-card analysis-card">
              {(api.recommendations || []).length === 0 ? (
                <p>No recommendations available.</p>
              ) : (
                api.recommendations.map((item, index) => (
                  <div className="analysis-result" key={index}>
                    <strong>{item}</strong>
                  </div>
                ))
              )}
            </div>
          </>
        );
      })()
    )}
  </section>
)}
          {/* REPORTS PAGE */}
{activeMenu === "Reports" && (
  <section className="section-block history-section">
    <div className="section-heading">
      <div>
        <h2>Scan Reports</h2>
        <p>Access reports generated from your previous website scans.</p>
      </div>

      <button
        className="refresh-button"
        onClick={loadScanHistory}
        disabled={historyLoading}
      >
        {historyLoading ? "Loading..." : "↻ Refresh"}
      </button>
    </div>

    {historyError && (
      <div className="scan-error">⚠ {historyError}</div>
    )}

    <div className="history-table-wrapper">
      <table className="history-table">
        <thead>
          <tr>
            <th>Website</th>
            <th>Health Score</th>
            <th>Status</th>
            <th>Scan Date</th>
            <th>Action</th>
          </tr>
        </thead>

        <tbody>
          {recentScans.length > 0 ? (
            recentScans.map((scan) => (
              <tr key={scan.id}>
                <td>{scan.url}</td>
                <td>
                  {typeof scan.healthScore === "number"
                    ? `${scan.healthScore}/100`
                    : "N/A"}
                </td>
                <td>{scan.status || "COMPLETED"}</td>
                <td>
                  {formatDate(scan.scannedAt || scan.createdAt)}
                </td>
                <td>
                  <button
                    className="view-button"
                    onClick={() => handleViewDetails(scan.id)}
                    disabled={detailsLoading}
                  >
                    {detailsLoading && selectedScanId === scan.id
                      ? "Loading..."
                      : "Open Report"}
                  </button>
                </td>
              </tr>
            ))
          ) : (
            <tr>
              <td colSpan="5" className="history-empty">
                {historyLoading
                  ? "Loading reports..."
                  : "No saved reports available yet."}
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  </section>
)}

{/* SETTINGS PAGE */}
{activeMenu === "Settings" && (
  <section className="section-block">
    <div className="section-heading">
      <div>
        <h2>Account Settings</h2>
        <p>Manage your WebDoctor account and preferences.</p>
      </div>
    </div>

    {/* PROFILE INFORMATION */}
    <div className="settings-card">
      <h3>Profile Information</h3>
      <p>Your registered account details.</p>

      <div className="settings-info-grid">
        <div>
          <span>Full Name</span>
          <strong>{user?.name || "Not available"}</strong>
        </div>

        <div>
          <span>Email Address</span>
          <strong>{user?.email || "Not available"}</strong>
        </div>

        <div>
          <span>User ID</span>
          <strong>{user?.userId || "Not available"}</strong>
        </div>
      </div>
    </div>

    {/* ACCOUNT SECURITY */}
    <div className="settings-card">
      <h3>Account Security</h3>
      <p>Your current login session.</p>

      <div className="settings-info-grid">
        <div>
          <span>Account Status</span>
          <strong className="settings-active">● Active</strong>
        </div>

        <div>
          <span>Login Method</span>
          <strong>Email & Password</strong>
        </div>

        <div>
          <span>Session</span>
          <strong>
            {localStorage.getItem("webdoctor_token")
              ? "Remembered Login"
              : "Current Session"}
          </strong>
        </div>
      </div>
    </div>
{/* CHANGE PASSWORD */}
<div className="settings-card">
  <h3>Change Password</h3>
  <p>Update your account password securely.</p>

  <form onSubmit={handleChangePassword} className="password-form">

    <div className="password-field">
      <label>Current Password</label>
      <input
        type="password"
        value={currentPassword}
        onChange={(e) => setCurrentPassword(e.target.value)}
        placeholder="Enter current password"
        autoComplete="current-password"
        required
      />
    </div>

    <div className="password-field">
      <label>New Password</label>
      <input
        type="password"
        value={newPassword}
        onChange={(e) => setNewPassword(e.target.value)}
        placeholder="Enter new password"
        minLength={8}
        maxLength={100}
        autoComplete="new-password"
        required
      />
    </div>

    <div className="password-field">
      <label>Confirm New Password</label>
      <input
        type="password"
        value={confirmNewPassword}
        onChange={(e) => setConfirmNewPassword(e.target.value)}
        placeholder="Confirm new password"
        autoComplete="new-password"
        required
      />
    </div>

    {passwordError && (
      <div className="password-error">{passwordError}</div>
    )}

    {passwordMessage && (
      <div className="password-success">{passwordMessage}</div>
    )}

    <button
      type="submit"
      className="password-submit-button"
      disabled={passwordLoading}
    >
      {passwordLoading ? "Updating..." : "Update Password"}
    </button>

  </form>
</div>
    {/* ACCOUNT ACTIONS */}
    <div className="settings-card settings-danger-card">
      <h3>Account Actions</h3>
      <p>Sign out from your WebDoctor account.</p>

      <button
        className="logout-button"
        onClick={handleLogout}
        type="button"
      >
        Logout
      </button>
    </div>
  </section>
)}

{/* OTHER NAVIGATION PAGES */}
{![
  "Dashboard",
  "Scan History",
  "New Scan",
  "Code Analyzer",
  "API Analyzer",
  "Reports",
  "Settings",
].includes(activeMenu) && (
  <section className="empty-state">
    <div className="empty-icon">⚙</div>
    <h3>{activeMenu}</h3>
    <p>
      This section is planned for a future WebDoctor update.
    </p>
    <button
      className="scan-button"
      onClick={() => setActiveMenu("Dashboard")}
    >
      Back to Dashboard
    </button>
  </section>
)}

          {/* FOOTER */}

          <footer className="dashboard-footer">
            <span>
              © {new Date().getFullYear()} WebDoctor
            </span>

            <span>
              Website Diagnostics Platform
            </span>
          </footer>
        </div>
      </main>
    </div>
  );
}

export default App;
