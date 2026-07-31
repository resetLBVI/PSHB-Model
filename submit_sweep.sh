#!/usr/bin/env bash
# ============================================================
# PSHB Parameter Sweep – Purdue Negishi sbatch submit script
#
# Submit:   sbatch submit_sweep.sh
# Monitor:  squeue -u $USER
# Cancel:   scancel <jobid>
# Log:      tail -f slurm_<jobid>.out
# ============================================================

#SBATCH --job-name=pshb_sweep
#SBATCH --account=pzollner
#SBATCH --partition=cpu
#SBATCH --nodes=1
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=32
#SBATCH --mem=96G
#SBATCH --time=24:00:00
#SBATCH --output=slurm_%j.out
#SBATCH --error=slurm_%j.err
#SBATCH --mail-type=BEGIN,END,FAIL
#SBATCH --mail-user=lin1789@purdue.edu     # <-- update this

# ── modules ──────────────────────────────────────────────────
# Verify names on Negishi with:  module spider java
#                                module spider python
module purge
module load gcc/12.2.0
module load openjdk/17.0.5_8
module load anaconda/2024.02-py311

# ── project directory ────────────────────────────────────────
# Update to wherever you uploaded the project on Negishi
PROJECT_DIR="$HOME/RESET_PSHB_2026-07-17"
cd "$PROJECT_DIR" || { echo "ERROR: cannot cd to $PROJECT_DIR"; exit 1; }

# ── sanity checks ────────────────────────────────────────────
echo "============================================"
echo "Job ID   : $SLURM_JOB_ID"
echo "Node     : $(hostname)"
echo "Started  : $(date)"
echo "Cores    : $(nproc)"
echo "Java     : $(java -version 2>&1 | head -1)"
echo "Python   : $(python3 --version)"
echo "Directory: $(pwd)"
echo "============================================"

# ── run sweep ────────────────────────────────────────────────
python3 run_sweep.py --config sweep_config_2026-07-17.json

echo "============================================"
echo "Finished : $(date)"
echo "============================================"