# PSHB Model Changelog

---

## 2026-10-25

### 1. Bug Fix: Recorded population size stuck after die-out (`PSHBObserver.java`)
- **Symptom:** When the simulation's population died out, the recorded population size in the
  population-summary output stayed constant instead of dropping to 0 (and was inflated even while
  the population was alive).
- **Root cause:** `PSHBObserver.collectPopData()` set
  `state.populationSize = state.agentDevelopGrid.getAllObjects().size()`, but `agentDevelopGrid` holds
  **both** `PSHBAgent` objects and `PSHBVegCell` colonized-cell objects. Cells are only removed at full
  colonization, so cells whose members had all died lingered in the grid and kept being counted.
- **Fix:** In `collectPopData()`, count only `PSHBAgent` instances:
  ```java
  Bag allObjects = state.agentDevelopGrid.getAllObjects();
  int liveAgents = 0;
  for (int i = 0; i < allObjects.numObjs; i++) {
      if (allObjects.objs[i] instanceof PSHBAgent) liveAgents++;
  }
  state.populationSize = liveAgents;
  ```
  Also added `import sim.util.Bag;`. Compiles clean (`mvn -q compile`).

### 2. Bug Fix: Impact (dead-vegetation) data never written (`PSHBAgent.java`)
- **Symptom:** The impact output file (`impactWriter`) got its header row but no data rows — it stayed
  effectively empty.
- **Root cause:** `PSHBAgent.colonizeAHost()` creates a new `PSHBVegCell` when a host is first
  colonized, but the cell was **never added to the MASON schedule**, so `PSHBVegCell.step()` never ran.
  The impact row is written only inside `step()` (when
  `numColonizedAgents >= cellDamageThreshold && currentWeek == 1`), so no impact data was ever produced.
  The cell's `event` Stoppable was also never assigned.
- **Fix:** In `colonizeAHost()`, immediately after placing the newly activated cell on the grids,
  schedule it each week and store its Stoppable (mirrors how agents are scheduled in `reproduce()`):
  ```java
  double cellNextTick = state.schedule.getTime() + 1;
  newActiveCell.event = state.schedule.scheduleRepeating(cellNextTick, 1, newActiveCell);
  ```
  Only the newly-activated cell is scheduled; the "join existing cell" branch reuses an already-scheduled
  cell. Compiles clean (`mvn -q compile`). Not yet confirmed by a run producing a non-empty impact file.

### 3. New Parameter: `cellDamageThreshold` (default: 1)
- Made the colonized-cell vegetation-damage threshold researcher-controllable instead of hardcoded.
  A cell records vegetation damage once `numColonizedAgents >= cellDamageThreshold` (in week 1 of the
  year). Previously this threshold was a fixed literal.

| File | Change |
|------|--------|
| `PSHBEnvironment.java` | Added `public int cellDamageThreshold = 1;` with `getCellDamageThreshold()` / `setCellDamageThreshold()` |
| `PSHBVegCell.java` | `step()` now tests `numColonizedAgents >= eState.cellDamageThreshold` instead of the hardcoded value |

### 4. Bug Fix: Zombie cells never removed from the schedule (`PSHBVegCell.java`)
- **Symptom:** A colonized cell that lost all its members through attrition (members died before the
  cell ever reached `cellDamageThreshold`) was never taken off the MASON schedule. It kept `step()`-ing
  every week doing nothing and lingered in `agentDevelopGrid` / `agentColonizedGrid` / `vegMapCell`,
  accumulating without bound over a long run. This was the **root cause** of the Bug #1 inflated
  population count (item 1 above patched the symptom in the counter).
- **Root cause:** A cell is only removed via `death()`, which was reached only on the vegetation-damage
  path (threshold reached in week 1). There was no exit path for a cell that simply emptied out —
  when the last member's `PSHBAgent.death()` decremented `numColonizedAgents` to 0, nothing removed the
  cell.
- **Fix:** Added an `else if (numColonizedAgents == 0)` branch to `PSHBVegCell.step()` that calls
  `death()` to stop the schedule and free the cell. No impact row is written in this case (no
  vegetation damage occurred). Re-colonization of the same location later is unaffected —
  `colonizeAHost()` sees the slot empty and creates a fresh cell. Compiles clean (`mvn -q compile`).

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