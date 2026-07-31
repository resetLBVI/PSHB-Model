# PSHB Model Changelog

---

## 2026-07-30

### 1. Bug Fix: `pWillowSum` in `getVegMapPrRepr()` (`PSHBEnvironment.java`)
- Reverted a debug override (`pWillowSum = 0.3`) back to the correct formula:
  `pWillowSum = vegInfo.get(patchID).pTrWillow + vegInfo.get(patchID).pShWillowM`
- The hardcoded value had been left in accidentally during a prior debugging session.

---

## 2026-07-17

### 1. Sweep Run Count (one_at_a_time mode)
- Confirmed 21 folders is correct for the original config (1 baseline + 20 non-baseline variations × 1 seed).
- Formula: `total = 1 + sum(len(values) - 1 for each parameter) × seeds`

### 2. OOM Root Cause
- Population explosion: 2,148 initial agents, Poisson(spawn) offspring, only ~1% mortality over 1,820 steps (35 yr × 52 wk).
- Most dangerous sweep values: `mpPshbSpawn=10`, `mpPshbMortLarva=0.001`, `mpPshbMortAdultDisp=0.001`.
- Each live agent holds 4 HashMaps; unbounded growth → heap exhaustion.
- Next step: add a population cap in `PSHBAgent.reproduce()` (not yet implemented).

### 3. sweep_config.json — Key Corrections Made
- Mortality baselines restored to **0.03** (was incorrectly set to 0.001, which made ALL runs OOM-prone).
- Spawn baseline set to **3** (down from 5/10) to keep population controlled during diagnostic sweep.
- `mpPshbDirStdDev` moved from `fixed` → `sweep` to explore dispersal direction variance.
- heap: 4g → **8g**, parallel_jobs: 21 → **8**.
- Added `"weeklyLog": false` to `fixed` block (see below).

### 4. New Parameter: `mpWeeklyLog` (default: false)
Controls whether `logPSHBWeekly.csv` is written. Disabled by default to save disk I/O and memory.

| File | Change |
|------|--------|
| `PSHBEnvironment.java` | Added `public boolean mpWeeklyLog = false`; wrapped `logWriter` init in `if (mpWeeklyLog)` block; added getter/setter |
| `PSHBAgent.java` | Guarded `logWriter.addToFile()` with null check |
| `PSHBHeadless.java` | Added `-weeklyLog` CLI arg; included in startup print and `run_params.json` |
| `sweep_config.json` | Added `"weeklyLog": false` to `fixed` block |

To enable for a specific run: `-weeklyLog true` on CLI, or `"weeklyLog": true` in `fixed`.

### 5. Negishi Cluster Setup (submit_sweep.sh)
Created `submit_sweep.sh` for Purdue Negishi sbatch submission.

**SLURM settings:**
```
--account=pzollner  --partition=cpu  --nodes=1
--ntasks=1  --cpus-per-task=32  --mem=96G  --time=24:00:00
```

**Modules:**
```
module load openjdk/17.0.5_8
module load anaconda/2024.02-py311
```

**Project directory on Negishi:** `$HOME/RESET_PSHB_2026-07-17`

**Files to upload to Negishi:**
- `RESET_PSHB_2026-07-17.jar` (rebuild with `mvn clean package -DskipTests`)
- `run_sweep.py`
- `sweep_config_2026-07-17.json`
- `submit_sweep.sh`
- `RESET_PSHB_inputData/` (full folder via rsync)

### 6. Current Sweep: 30 runs, 8 parallel jobs → ~4 batches × ~4 hrs = ~16 hrs walltime