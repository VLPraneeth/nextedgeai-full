const { spawn } = require("child_process");
const fs = require("fs");
const net = require("net");
const path = require("path");

const rootDir = path.resolve(__dirname, "..");
const backendDir = path.join(rootDir, "backend-master");
const backendTargetDir = path.join(backendDir, "arcade", "target");
const spectrumDir = path.join(rootDir, "spectrum-master");
const proxyDir = path.join(spectrumDir, "proxy");
const localDevProperties = path.join(backendDir, "local-dev.properties").replace(/\\/g, "/");
const backendJar = path.join(backendTargetDir, "arcade-DEV-SNAPSHOT.jar");

const children = new Set();

function bin(name) {
  return process.platform === "win32" ? `${name}.cmd` : name;
}

function log(message) {
  console.log(`[synca] ${message}`);
}

function mergeNodeOptions(option) {
  const existing = process.env.NODE_OPTIONS || "";
  return existing.includes(option) ? existing : `${existing} ${option}`.trim();
}

function quoteCmdArg(arg) {
  if (!/[()\s%!"^<>&|]/.test(arg)) {
    return arg;
  }

  return `"${arg.replace(/"/g, '\\"')}"`;
}

function spawnOptions(command, args, options) {
  if (process.platform === "win32" && command.endsWith(".cmd")) {
    return {
      command: "cmd.exe",
      args: ["/d", "/s", "/c", [command, ...args].map(quoteCmdArg).join(" ")],
    };
  }

  return { command, args };
}

function prefixOutput(child, label) {
  child.stdout?.on("data", (data) => {
    for (const line of data.toString().split(/\r?\n/).filter(Boolean)) {
      console.log(`[${label}] ${line}`);
    }
  });

  child.stderr?.on("data", (data) => {
    for (const line of data.toString().split(/\r?\n/).filter(Boolean)) {
      console.error(`[${label}] ${line}`);
    }
  });
}

function runStep(label, command, args, options = {}) {
  return new Promise((resolve, reject) => {
    log(`${label}: ${command} ${args.join(" ")}`);
    const spawned = spawnOptions(command, args, options);

    const child = spawn(spawned.command, spawned.args, {
      cwd: options.cwd || rootDir,
      env: { ...process.env, ...(options.env || {}) },
      shell: false,
      stdio: ["ignore", "pipe", "pipe"],
      windowsHide: true,
    });

    prefixOutput(child, label);

    child.on("error", reject);
    child.on("close", (code) => {
      if (code === 0) {
        resolve();
      } else {
        reject(new Error(`${label} failed with exit code ${code}`));
      }
    });
  });
}

function startService(label, command, args, options = {}) {
  log(`${label}: ${command} ${args.join(" ")}`);

  const child = spawn(command, args, {
    cwd: options.cwd || rootDir,
    env: { ...process.env, ...(options.env || {}) },
    shell: false,
    stdio: ["ignore", "pipe", "pipe"],
    windowsHide: false,
  });

  children.add(child);
  prefixOutput(child, label);

  child.on("exit", (code, signal) => {
    children.delete(child);
    if (!shuttingDown) {
      console.error(`[synca] ${label} stopped unexpectedly. code=${code} signal=${signal || ""}`);
      shutdown(1);
    }
  });

  child.on("error", (error) => {
    console.error(`[synca] ${label} failed to start: ${error.message}`);
    shutdown(1);
  });

  return child;
}

function waitForPort(port, label, timeoutMs = 180000) {
  const startedAt = Date.now();

  return new Promise((resolve, reject) => {
    const tryConnect = () => {
      const socket = new net.Socket();

      socket.setTimeout(1000);
      socket.once("connect", () => {
        socket.destroy();
        log(`${label} is ready on port ${port}.`);
        resolve();
      });
      socket.once("timeout", () => {
        socket.destroy();
        retry();
      });
      socket.once("error", () => {
        socket.destroy();
        retry();
      });

      socket.connect(port, "127.0.0.1");
    };

    const retry = () => {
      if (Date.now() - startedAt > timeoutMs) {
        reject(new Error(`${label} did not open port ${port} within ${Math.round(timeoutMs / 1000)} seconds.`));
        return;
      }

      setTimeout(tryConnect, 1500);
    };

    tryConnect();
  });
}

async function ensureRedis() {
  try {
    await runStep("redis", "docker", ["start", "redis-syncari"]);
  } catch (error) {
    log("Redis container was not found or was not startable; creating redis-syncari.");
    await runStep("redis", "docker", ["run", "-d", "-p", "6379:6379", "--name", "redis-syncari", "redis"]);
  }

  await runStep("redis", "docker", ["exec", "redis-syncari", "redis-cli", "ping"]);
  await waitForPort(6379, "Redis", 30000);
}

let shuttingDown = false;

function killTree(pid) {
  if (!pid) {
    return;
  }

  if (process.platform === "win32") {
    spawn("taskkill", ["/pid", String(pid), "/T", "/F"], {
      stdio: "ignore",
      windowsHide: true,
    });
  } else {
    try {
      process.kill(-pid, "SIGTERM");
    } catch (_) {
      try {
        process.kill(pid, "SIGTERM");
      } catch (_ignored) {
        // Already gone.
      }
    }
  }
}

function shutdown(exitCode = 0) {
  if (shuttingDown) {
    return;
  }

  shuttingDown = true;
  log("Stopping local services...");

  for (const child of children) {
    killTree(child.pid);
  }

  setTimeout(() => process.exit(exitCode), 750);
}

process.on("SIGINT", () => shutdown(0));
process.on("SIGTERM", () => shutdown(0));

async function main() {
  log("Starting local stack from LOCAL_RUNBOOK.md.");

  await ensureRedis();

  await runStep("backend-build", bin("mvn"), ["clean", "package", "-DskipTests=true", "-Dmaven.test.skip=true"], {
    cwd: backendDir,
  });

  if (!fs.existsSync(backendJar)) {
    throw new Error(`Backend build finished, but the jar was not found at ${backendJar}`);
  }

  startService(
    "backend",
    "java",
    [
      "-jar",
      backendJar,
      `--spring.config.additional-location=file:${localDevProperties}`,
    ],
    { cwd: backendTargetDir },
  );

  await waitForPort(8080, "Backend Arcade", 240000);

  await runStep("proxy-build", bin("npm"), ["run", "build"], { cwd: proxyDir });

  startService("proxy", "node", ["dist/index.js"], {
    cwd: proxyDir,
    env: {
      ARCADE_TARGET: "http://localhost:8080",
      DISABLE_XSRF: "true",
      SECURE_COOKIES: "false",
      ARCADE_LOG_LEVEL: "debug",
      SPECTRUM_PORT: "8088",
    },
  });

  await waitForPort(8088, "Spectrum Proxy", 90000);

  await runStep("frontend-less", bin("npx"), ["ts-node", "scripts/lessToTs.ts"], { cwd: spectrumDir });

  startService("frontend", "node", ["--max-old-space-size=8192", "scripts/start.js"], {
    cwd: spectrumDir,
    env: {
      NODE_OPTIONS: mergeNodeOptions("--max-old-space-size=8192"),
    },
  });

  await waitForPort(3000, "Frontend", 120000);

  log("All services are up.");
  log("Open http://localhost:3000/login");
  log("Press Ctrl+C in this terminal to stop backend, proxy, and frontend.");
}

main().catch((error) => {
  console.error(`[synca] ${error.message}`);
  shutdown(1);
});
