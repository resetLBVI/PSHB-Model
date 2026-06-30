#!/usr/bin/env python3
"""
PSHB Parameter Sweep Runner
============================
Reads sweep_config.json, generates parameter combinations, and launches
PSHBHeadless runs in parallel (or sequential).

Usage:
    python3 run_sweep.py                        # uses sweep_config.json
    python3 run_sweep.py --config my_sweep.json
    python3 run_sweep.py --dry-run              # print commands without running
"""

import argparse
import itertools
import json
import os
import subprocess
import sys
from concurrent.futures import ProcessPoolExecutor, as_completed
from datetime import datetime
from pathlib import Path


class Tee:
    """Mirror everything written to stdout into a log file as well."""
    def __init__(self, log_path: Path):
        self.terminal = sys.stdout
        self.log = open(log_path, "a", buffering=1)  # line-buffered
    def write(self, msg):
        self.terminal.write(msg)
        self.log.write(msg)
    def flush(self):
        self.terminal.flush()
        self.log.flush()


def build_run_list(config: dict) -> list[dict]:
    """Return a list of dicts, each describing one simulation run."""
    sweep   = config["sweep"]
    fixed   = config["fixed"]
    seeds   = config["seeds"]
    mode    = config.get("mode", "one_at_a_time")

    sweep_keys   = list(sweep.keys())
    sweep_values = list(sweep.values())

    # Default values = first element of each sweep list (used in OAT)
    defaults = {k: v[0] for k, v in sweep.items()}

    param_sets = []  # list of {param: value} dicts (without seed)

    if mode == "full_factorial":
        for combo in itertools.product(*sweep_values):
            param_sets.append(dict(zip(sweep_keys, combo)))

    elif mode == "one_at_a_time":
        # Baseline (all defaults)
        param_sets.append(dict(defaults))
        # Vary one parameter at a time
        for key, values in sweep.items():
            for val in values:
                if val == defaults[key]:
                    continue  # skip – already covered by baseline
                ps = dict(defaults)
                ps[key] = val
                param_sets.append(ps)

    else:
        raise ValueError(f"Unknown mode: {mode!r}. Use 'one_at_a_time' or 'full_factorial'.")

    # Combine with seeds
    runs = []
    run_idx = 0
    for ps in param_sets:
        for seed in seeds:
            run_idx += 1
            run = {}
            run.update(fixed)
            run.update(ps)
            run["seed"]  = seed
            run["runId"] = f"run_{run_idx:04d}_s{seed}"
            runs.append(run)

    return runs


def epsg_cache_dir(jar_path: str) -> Path:
    """Absolute, shared GeoTools EPSG-HSQL cache directory used by every run."""
    return Path(jar_path).resolve().parent / "geotools_epsg_cache"


def build_command(jar_path: str, run: dict, heap: str = "4g") -> list[str]:
    """Build the java command list for a single run."""
    cmd = [
        "java",
        f"-Xmx{heap}",                    # max heap per run (from config "heap")
        # Pin the GeoTools EPSG-HSQL cache to one fixed absolute directory so all
        # parallel jobs share the same warm DB (keeps it off the Desktop and
        # avoids cold-start lock contention between concurrent JVMs).
        f"-DEPSG-HSQL.directory={epsg_cache_dir(jar_path)}",
        "-cp", jar_path,
        "pshb.PSHBHeadless",
    ]
    for key, val in run.items():
        cmd += [f"-{key}", str(val)]
    return cmd


def log_has_oom(log_file: Path) -> bool:
    """Return True if the run's log contains a Java OutOfMemoryError.

    Scans the file in fixed-size chunks (with a small overlap so a match that
    straddles a chunk boundary is still found) so we never load a multi-GB log
    fully into memory.
    """
    needle = b"OutOfMemoryError"
    overlap = len(needle) - 1
    try:
        with open(log_file, "rb") as f:
            tail = b""
            while True:
                chunk = f.read(1 << 20)          # 1 MB at a time
                if not chunk:
                    return False
                if needle in tail + chunk:
                    return True
                tail = chunk[-overlap:]          # carry boundary bytes forward
    except OSError:
        return False


def _emit(msg: str, log_path: str | None) -> None:
    """Print a line, and also append it to the sweep log when the caller is a
    parallel worker process (whose stdout is not the Tee installed in main)."""
    print(msg)
    if log_path and not isinstance(sys.stdout, Tee):
        try:
            with open(log_path, "a", buffering=1) as f:
                f.write(msg + "\n")
        except OSError:
            pass


def execute_run(jar_path: str, run: dict, dry_run: bool, heap: str = "4g",
                log_path: str | None = None) -> tuple[str, int, bool]:
    """Run one simulation. Returns (runId, return_code, hit_oom)."""
    run_id = run["runId"]
    cmd    = build_command(jar_path, run, heap)

    if dry_run:
        print("DRY RUN:", " ".join(cmd))
        return run_id, 0, False

    log_dir = Path("runs") / run_id
    log_dir.mkdir(parents=True, exist_ok=True)
    log_file = log_dir / "stdout.log"

    _emit(f"[{datetime.now().strftime('%H:%M:%S')}] START  {run_id}", log_path)
    with open(log_file, "w") as f:
        proc = subprocess.run(cmd, stdout=f, stderr=subprocess.STDOUT)

    oom = proc.returncode != 0 and log_has_oom(log_file)
    status = "OK" if proc.returncode == 0 else (
        "FAILED (OutOfMemory)" if oom else f"FAILED (rc={proc.returncode})")
    _emit(f"[{datetime.now().strftime('%H:%M:%S')}] {status:30s}  {run_id}  → {log_file}",
          log_path)
    return run_id, proc.returncode, oom


def write_manifest(runs: list[dict], config: dict) -> None:
    """
    Write runs/sweep_manifest.csv — one row per run with every parameter value.
    This is the top-level index that maps runId → parameter set.
    Also writes runs/sweep_manifest.json for programmatic access.
    """
    import csv

    manifest_dir = Path("runs")
    manifest_dir.mkdir(parents=True, exist_ok=True)

    all_keys = list(runs[0].keys()) if runs else []

    # CSV
    csv_path = manifest_dir / "sweep_manifest.csv"
    with open(csv_path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=all_keys)
        writer.writeheader()
        writer.writerows(runs)
    print(f"Manifest written: {csv_path}")

    # JSON (also saves sweep config for full reproducibility)
    json_path = manifest_dir / "sweep_manifest.json"
    with open(json_path, "w") as f:
        json.dump({
            "generated": datetime.now().isoformat(),
            "config": config,
            "runs": runs
        }, f, indent=2)
    print(f"Manifest written: {json_path}\n")


def main():
    parser = argparse.ArgumentParser(description="PSHB parameter sweep runner")
    parser.add_argument("--config",  default="sweep_config.json",
                        help="Path to sweep config JSON (default: sweep_config.json)")
    parser.add_argument("--dry-run", action="store_true",
                        help="Print commands without executing")
    args = parser.parse_args()

    # ---- load config ----
    config_path = Path(args.config)
    if not config_path.exists():
        sys.exit(f"Config file not found: {config_path}")
    with open(config_path) as f:
        config = json.load(f)

    jar_path = config["jar"]
    if not args.dry_run and not Path(jar_path).exists():
        sys.exit(f"JAR not found: {jar_path}\nRun:  mvn clean package -DskipTests  first.")

    n_jobs = int(config.get("parallel_jobs", 1))
    heap   = str(config.get("heap", "4g"))   # max JVM heap per run, e.g. "8g"

    # ---- mirror this script's console output to runs/sweep_run.log ----
    sweep_log = str((Path("runs") / "sweep_run.log").resolve())
    if not args.dry_run:
        Path("runs").mkdir(parents=True, exist_ok=True)
        sys.stdout = Tee(Path("runs") / "sweep_run.log")
        print(f"\n===== Sweep started {datetime.now().isoformat()} =====")

    # ---- generate runs ----
    runs = build_run_list(config)
    print(f"\nParameter sweep:  mode={config.get('mode','one_at_a_time')}  "
          f"seeds={config['seeds']}  total_runs={len(runs)}  parallel_jobs={n_jobs}  "
          f"heap={heap}\n")

    # ---- write sweep manifest (before any run starts) ----
    if not args.dry_run:
        write_manifest(runs, config)

    # Print a summary table of unique parameter sets
    print(f"{'runId':<22}  {'seed':>6}  ", end="")
    sweep_keys = list(config["sweep"].keys())
    for k in sweep_keys:
        print(f"{k:>22}", end="  ")
    print()
    print("-" * (30 + 24 * len(sweep_keys)))
    for run in runs:
        print(f"{run['runId']:<22}  {run['seed']:>6}  ", end="")
        for k in sweep_keys:
            print(f"{str(run.get(k, '')):>22}", end="  ")
        print()
    print()

    if args.dry_run:
        print("--- DRY RUN: commands that would be executed ---")
        for run in runs:
            print(" ".join(build_command(jar_path, run, heap)))
        return

    # ---- pre-warm the GeoTools EPSG cache ----
    # On a cold start, GeoTools builds a ~32 MB HSQL EPSG database in the shared
    # cache dir. Letting all parallel JVMs build it at once causes lock
    # contention / a corrupt DB, so when the cache is cold we run the first job
    # alone to build it, then fan the rest out in parallel against the warm DB.
    cache_dir   = epsg_cache_dir(jar_path)
    cache_dir.mkdir(parents=True, exist_ok=True)
    # GeoTools writes the marker inside a versioned subfolder (e.g. v11.0.31/),
    # so search recursively rather than at the top level.
    is_warm     = lambda: any(cache_dir.rglob("EPSG_creation_marker.txt"))
    parallel    = runs
    failed      = []
    oom_runs    = []

    if n_jobs > 1 and len(runs) > 1 and not is_warm():
        warm_run = runs[0]
        print(f"EPSG cache cold ({cache_dir}). Pre-warming with {warm_run['runId']} "
              f"before parallel fan-out...\n")
        _, rc, oom = execute_run(jar_path, warm_run, dry_run=False, heap=heap,
                                 log_path=sweep_log)
        if rc != 0:
            failed.append(warm_run["runId"])
        if oom:
            oom_runs.append(warm_run["runId"])
        if not is_warm():
            print(f"WARNING: pre-warm finished but EPSG_creation_marker.txt not found; "
                  f"parallel runs may rebuild the EPSG cache.\n")
        parallel = runs[1:]

    # ---- execute ----
    if n_jobs == 1:
        for run in parallel:
            _, rc, oom = execute_run(jar_path, run, dry_run=False, heap=heap,
                                     log_path=sweep_log)
            if rc != 0:
                failed.append(run["runId"])
            if oom:
                oom_runs.append(run["runId"])
    else:
        with ProcessPoolExecutor(max_workers=n_jobs) as pool:
            futures = {
                pool.submit(execute_run, jar_path, run, False, heap, sweep_log): run["runId"]
                for run in parallel
            }
            for future in as_completed(futures):
                run_id, rc, oom = future.result()
                if rc != 0:
                    failed.append(run_id)
                if oom:
                    oom_runs.append(run_id)

    # ---- summary ----
    print(f"\n{'='*60}")
    print(f"Sweep complete.  {len(runs) - len(failed)}/{len(runs)} runs succeeded.")
    if failed:
        print("FAILED runs:")
        for r in failed:
            tag = "  [OutOfMemory]" if r in oom_runs else ""
            print(f"  {r}{tag}")
    if oom_runs:
        print(f"\n⚠  {len(oom_runs)} run(s) hit OutOfMemoryError with heap={heap}.")
        print(f"   Raise \"heap\" in the config (and/or lower \"parallel_jobs\" to keep")
        print(f"   heap x parallel_jobs under total RAM), then re-run the failed runs.")
    print(f"\nResults are in:  runs/")


if __name__ == "__main__":
    main()