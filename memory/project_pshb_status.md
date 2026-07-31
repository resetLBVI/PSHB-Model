---
name: PSHB Sweep Project Status
description: Current state of the PSHB agent-based model parameter sweep — OOM diagnosis, code changes, and Negishi cluster setup
type: project
---

Diagnostic sweep underway to identify which parameter values cause OOM (population explosion). Runs on Purdue Negishi cluster.

**Why:** Original 21-run sweep (heap=4g, parallel_jobs=21) hit OOM on most runs due to unbounded population growth. Root cause: high spawn rates + low mortality → exponential agent growth over 1,820 steps (35 yr × 52 wk). Fix (population cap in PSHBAgent.reproduce()) not yet implemented.

**How to apply:** When resuming, check if population cap has been added. Next task is implementing a cap in PSHBAgent.reproduce() before the full sweep.

### Current sweep config (sweep_config_2026-07-17.json)
- 30 runs, 1 seed, one_at_a_time mode
- heap=8g, parallel_jobs=8 → ~4 batches × ~4 hrs = ~16 hrs walltime
- Mortality baselines: 0.03 (conservative, avoids OOM)
- Spawn baseline: 3

### Negishi cluster
- Account: pzollner, partition: cpu
- Folder: ~/RESET_PSHB_2026-07-17/
- Submit: sbatch submit_sweep.sh
- Modules: openjdk/17.0.5_8, anaconda/2024.02-py311
- SLURM: 1 node, 32 cores, 96G RAM, 24h walltime

### Code change made 2026-07-17: mpWeeklyLog parameter
Added boolean `mpWeeklyLog` (default false) to skip writing logPSHBWeekly.csv.
- PSHBEnvironment.java: field + guarded logWriter init + getter/setter
- PSHBAgent.java: null check on logWriter.addToFile()
- PSHBHeadless.java: -weeklyLog CLI arg + run_params.json field
- sweep_config.json: "weeklyLog": false in fixed block
JAR must be rebuilt (mvn clean package -DskipTests) before uploading to Negishi.