import { spawn, spawnSync } from "node:child_process";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const configuredTimeout = Number(process.env.LIGHTHOUSE_TIMEOUT_MS);

const PROCESS_TIMEOUT_MS =
    Number.isFinite(configuredTimeout) && configuredTimeout >= 1000
        ? configuredTimeout
        : 120000;

const MAX_BUFFER_SIZE = 20 * 1024 * 1024;

const currentDirectory = path.dirname(fileURLToPath(import.meta.url));
const backendDirectory = path.resolve(currentDirectory, "..");

const lighthouseCli = path.join(
    backendDirectory,
    "node_modules",
    "lighthouse",
    "cli",
    "index.js"
);

const chromePath =
    process.env.CHROME_PATH || (process.platform === "win32" ? "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe" : "/usr/bin/chromium");

function killProcessTree(child) {
    if (!child.pid) return;

    if (process.platform === "win32") {
        const result = spawnSync(
            "taskkill",
            ["/PID", String(child.pid), "/T", "/F"],
            {
                windowsHide: true,
                encoding: "utf8",
                timeout: 15000
            }
        );

        if (result.error || result.status !== 0) {
            try {
                child.kill("SIGKILL");
            } catch {
                // Process may already have exited.
            }
        }

        return;
    }

    try {
        process.kill(-child.pid, "SIGKILL");
    } catch {
        try {
            child.kill("SIGKILL");
        } catch {
            // Process may already have exited.
        }
    }
}

function runLighthouseProcess(args) {
    return new Promise((resolve, reject) => {
        const child = spawn(
            process.execPath,
            [lighthouseCli, ...args],
            {
                windowsHide: true,
                detached: process.platform !== "win32",
                stdio: ["ignore", "pipe", "pipe"]
            }
        );

        let stdout = "";
        let stderr = "";
        let settled = false;
        let timedOut = false;
        let timer;

        function cleanup() {
            if (timer) {
                clearTimeout(timer);
                timer = null;
            }
        }

        function rejectOnce(error) {
            if (settled) return;

            settled = true;
            cleanup();
            reject(error);
        }

        function appendOutput(current, chunk, streamName) {
            const updated = current + chunk.toString();

            if (Buffer.byteLength(updated, "utf8") > MAX_BUFFER_SIZE) {
                killProcessTree(child);

                rejectOnce(
                    new Error(
                        `Lighthouse ${streamName} exceeded the 20 MB limit.`
                    )
                );

                return null;
            }

            return updated;
        }

        timer = setTimeout(() => {
            if (settled) return;

            timedOut = true;

            console.error(
                `Lighthouse process timed out after ${PROCESS_TIMEOUT_MS} ms.`
            );

            killProcessTree(child);
        }, PROCESS_TIMEOUT_MS);

        child.stdout.on("data", chunk => {
            const updated = appendOutput(stdout, chunk, "stdout");

            if (updated !== null) {
                stdout = updated;
            }
        });

        child.stderr.on("data", chunk => {
            const updated = appendOutput(stderr, chunk, "stderr");

            if (updated !== null) {
                stderr = updated;
            }
        });

        child.on("error", error => {
            rejectOnce(
                new Error(`Could not start Lighthouse process: ${error.message}`)
            );
        });

        child.on("close", (code, signal) => {
            if (settled) return;

            cleanup();

            if (timedOut) {
                rejectOnce(
                    new Error(
                        `Lighthouse process timed out after ${PROCESS_TIMEOUT_MS} ms.`
                    )
                );
                return;
            }

            if (code !== 0) {
                rejectOnce(
                    new Error(
                        stderr.trim() ||
                        `Lighthouse exited with code ${code}, signal ${signal}.`
                    )
                );
                return;
            }

            settled = true;

            resolve({
                stdout,
                stderr
            });
        });
    });
}

function getScore(report, categoryId) {
    const score = report.categories?.[categoryId]?.score;
    return score == null ? null : Math.round(score * 100);
}

function getMetric(audits, id) {
    const audit = audits[id];

    return {
        value: audit?.numericValue ?? null,
        displayValue: audit?.displayValue ?? null,
        score: audit?.score ?? null
    };
}

function getAudit(audit) {
    return {
        id: audit.id,
        title: audit.title,
        description: audit.description,
        displayValue: audit.displayValue ?? null,
        score: audit.score ?? null,
        scoreDisplayMode: audit.scoreDisplayMode ?? null,
        numericValue: audit.numericValue ?? null,
        numericUnit: audit.numericUnit ?? null,
        estimatedSavingsMs: audit.details?.overallSavingsMs ?? null,
        estimatedSavingsBytes: audit.details?.overallSavingsBytes ?? null
    };
}

function getAuditLists(report) {
    const audits = report.audits ?? {};
    const opportunities = new Map();
    const diagnostics = new Map();
    const failedAudits = new Map();

    for (const category of Object.values(report.categories ?? {})) {
        for (const reference of category.auditRefs ?? []) {
            const audit = audits[reference.id];

            if (!audit) continue;

            if (
                audit.scoreDisplayMode === "notApplicable" ||
                audit.scoreDisplayMode === "manual" ||
                audit.scoreDisplayMode === "informative"
            ) {
                continue;
            }

            const item = getAudit(audit);

            if (
                reference.group === "load-opportunities" ||
                reference.group === "budgets"
            ) {
                if (audit.score !== null && audit.score < 1) {
                    opportunities.set(audit.id, item);
                }
            }

            if (reference.group === "diagnostics") {
                diagnostics.set(audit.id, item);
            }

            if (audit.score !== null && audit.score < 1) {
                failedAudits.set(audit.id, item);
            }
        }
    }

    return {
        opportunities: [...opportunities.values()],
        diagnostics: [...diagnostics.values()],
        failedAudits: [...failedAudits.values()]
    };
}

async function runLighthouse(url, strategy) {
    if (!url || !["mobile", "desktop"].includes(strategy)) {
        throw new Error("Invalid URL or Lighthouse strategy.");
    }

    const temporaryDirectory = await mkdtemp(
        path.join(tmpdir(), "webdoctor-lighthouse-")
    );

    const outputPath = path.join(temporaryDirectory, "report.json");

    try {
        await runLighthouseProcess([
            url,
            `--chrome-path=${chromePath}`,
            "--output=json",
            `--output-path=${outputPath}`,
            "--quiet",
            "--no-enable-error-reporting",
            ...(strategy === "desktop"
                ? ["--preset=desktop"]
                : ["--form-factor=mobile"]),
            process.platform === "win32" ? "--chrome-flags=--headless=new" : "--chrome-flags=--headless=new --no-sandbox --disable-dev-shm-usage"
        ]);

        const reportText = await readFile(outputPath, "utf8");
        const report = JSON.parse(reportText);

        const audits = report.audits ?? {};
        const auditLists = getAuditLists(report);

        const result = {
            status: "COMPLETED",
            strategy,

            performanceScore: getScore(report, "performance"),

            fcp: getMetric(audits, "first-contentful-paint"),
            lcp: getMetric(audits, "largest-contentful-paint"),
            tbt: getMetric(audits, "total-blocking-time"),
            cls: getMetric(audits, "cumulative-layout-shift"),
            speedIndex: getMetric(audits, "speed-index"),

            accessibilityScore: getScore(report, "accessibility"),
            bestPracticesScore: getScore(report, "best-practices"),
            seoScore: getScore(report, "seo"),

            opportunities: auditLists.opportunities,
            diagnostics: auditLists.diagnostics,
            failedAudits: auditLists.failedAudits,

            finalUrl: report.finalDisplayedUrl,
            fetchTime: report.fetchTime
        };

        process.stdout.write(JSON.stringify(result));

    } finally {
        await rm(temporaryDirectory, {
            recursive: true,
            force: true
        });
    }
}

const url = process.argv[2];
const strategy = process.argv[3] || "mobile";

runLighthouse(url, strategy).catch(error => {
    console.error("Lighthouse audit failed:", error.message);
    process.exitCode = 1;
});
