package pshb;

/**
 * Headless (no-GUI) runner for PSHB parameter sweeps.
 *
 * Usage (after building the fat JAR):
 *   java -cp target/RESET_PSHB-1.0-SNAPSHOT.jar pshb.PSHBHeadless \
 *        -runId run_001 -seed 42 \
 *        -mpProbMate 0.65 -mpPshbMove 2300 -mpPshbSpawn 5 \
 *        -mpPshbMortLarva 0.01 -mpPshbMortPreovi 0.01 \
 *        -mpPshbMortAdultDisp 0.01 -mpPshbMortAdultCol 0.01 \
 *        -mpPshbDirStdDev 30 -mpPshbShouldIStay 0.5 \
 *        -weeklyOutput false
 *
 * Output files are written to:  runs/<runId>/
 * (relative to the JAR's containing directory, same logic as OutputWriter)
 */
public class PSHBHeadless {

    // 35 years × 52 weeks per year
    private static final int TOTAL_STEPS = 35 * 52;

    public static void main(String[] args) {

        // ---- parse CLI arguments ----
        String  runId               = parseString (args, "-runId",               "run_default");
        long    seed                = parseLong   (args, "-seed",                System.currentTimeMillis());
        double  mpProbMate          = parseDouble (args, "-mpProbMate",          0.65);
        int     mpPshbMove          = parseInt    (args, "-mpPshbMove",          2300);
        int     mpPshbDirStdDev     = parseInt    (args, "-mpPshbDirStdDev",     30);
        double  mpPshbShouldIStay   = parseDouble (args, "-mpPshbShouldIStay",   0.5);
        int     mpPshbSpawn         = parseInt    (args, "-mpPshbSpawn",         5);
        double  mpPshbMortLarva     = parseDouble (args, "-mpPshbMortLarva",     0.01);
        double  mpPshbMortPreovi    = parseDouble (args, "-mpPshbMortPreovi",    0.01);
        double  mpPshbMortAdultDisp = parseDouble (args, "-mpPshbMortAdultDisp", 0.01);
        double  mpPshbMortAdultCol  = parseDouble (args, "-mpPshbMortAdultCol",  0.01);
        boolean weeklyOutput        = parseBoolean(args, "-weeklyOutput",        false);

        System.out.printf("[PSHBHeadless] runId=%s  seed=%d%n", runId, seed);
        System.out.printf("  mpProbMate=%.3f  mpPshbMove=%d  mpPshbSpawn=%d%n",
                mpProbMate, mpPshbMove, mpPshbSpawn);
        System.out.printf("  mpPshbShouldIStay=%.3f  mpPshbDirStdDev=%d%n",
                mpPshbShouldIStay, mpPshbDirStdDev);
        System.out.printf("  mort(larva=%.4f preovi=%.4f disp=%.4f col=%.4f)%n",
                mpPshbMortLarva, mpPshbMortPreovi, mpPshbMortAdultDisp, mpPshbMortAdultCol);
        System.out.printf("  weeklyOutput=%b%n", weeklyOutput);

        // ---- create and configure environment ----
        PSHBEnvironment env = new PSHBEnvironment(seed);

        env.mpProbMate          = mpProbMate;
        env.mpPshbMove          = mpPshbMove;
        env.mpPshbDirStdDev     = mpPshbDirStdDev;
        env.mpPshbShouldIStay   = mpPshbShouldIStay;
        env.mpPshbSpawn         = mpPshbSpawn;
        env.mpPshbMortLarva     = mpPshbMortLarva;
        env.mpPshbMortPreovi    = mpPshbMortPreovi;
        env.mpPshbMortAdultDisp = mpPshbMortAdultDisp;
        env.mpPshbMortAdultCol  = mpPshbMortAdultCol;
        env.mpPshbWeeklyOutput  = weeklyOutput;

        // ---- redirect outputs to per-run subdirectory ----
        String prefix = "runs/" + runId + "/";
        env.debugFile        = prefix + "RESET_PSHB_debug.txt";
        env.logFile          = prefix + "logPSHBWeekly.csv";
        env.agentSummaryFile = prefix + "RESET_PSHB_agentSummary.csv";
        env.popSummaryFile   = prefix + "RESET_PSHB_popSummary.csv";
        env.impactFile       = prefix + "RESET_PSHB_impact.csv";

        // ---- write run_params.json so results are always traceable ----
        writeParamsJson(prefix, runId, seed,
                mpProbMate, mpPshbMove, mpPshbDirStdDev, mpPshbShouldIStay, mpPshbSpawn,
                mpPshbMortLarva, mpPshbMortPreovi, mpPshbMortAdultDisp, mpPshbMortAdultCol,
                weeklyOutput);

        // ---- run simulation ----
        long wallStart = System.currentTimeMillis();
        env.start();
        do {
            if (!env.schedule.step(env)) break;
        } while (env.schedule.getSteps() < TOTAL_STEPS);
        env.finish();

        long elapsed = (System.currentTimeMillis() - wallStart) / 1000;
        System.out.printf("[PSHBHeadless] DONE  runId=%s  elapsed=%ds  output=%s%n",
                runId, elapsed, prefix);
    }

    // ---- parameter tracing ----

    /**
     * Writes run_params.json into the run's output directory.
     * This file is the definitive record of every parameter value used.
     */
    private static void writeParamsJson(
            String prefix, String runId, long seed,
            double mpProbMate, int mpPshbMove, int mpPshbDirStdDev,
            double mpPshbShouldIStay, int mpPshbSpawn,
            double mpPshbMortLarva, double mpPshbMortPreovi,
            double mpPshbMortAdultDisp, double mpPshbMortAdultCol,
            boolean weeklyOutput) {

        try {
            String paramsFilePath = pshb.Utils.OutputWriter.getFileName(prefix + "run_params.json", false);
            // Ensure directory exists
            java.io.File dir = new java.io.File(paramsFilePath).getParentFile();
            if (dir != null) dir.mkdirs();

            // Build JSON manually (no external dependency)
            String json = String.format(
                "{\n" +
                "  \"runId\":               \"%s\",\n" +
                "  \"seed\":                %d,\n" +
                "  \"mpProbMate\":          %.6f,\n" +
                "  \"mpPshbMove\":          %d,\n" +
                "  \"mpPshbDirStdDev\":     %d,\n" +
                "  \"mpPshbShouldIStay\":   %.6f,\n" +
                "  \"mpPshbSpawn\":         %d,\n" +
                "  \"mpPshbMortLarva\":     %.6f,\n" +
                "  \"mpPshbMortPreovi\":    %.6f,\n" +
                "  \"mpPshbMortAdultDisp\": %.6f,\n" +
                "  \"mpPshbMortAdultCol\":  %.6f,\n" +
                "  \"weeklyOutput\":        %b\n" +
                "}\n",
                runId, seed,
                mpProbMate, mpPshbMove, mpPshbDirStdDev, mpPshbShouldIStay, mpPshbSpawn,
                mpPshbMortLarva, mpPshbMortPreovi, mpPshbMortAdultDisp, mpPshbMortAdultCol,
                weeklyOutput);

            try (java.io.FileWriter fw = new java.io.FileWriter(paramsFilePath)) {
                fw.write(json);
            }
            System.out.println("[PSHBHeadless] Parameters saved to: " + paramsFilePath);
        } catch (java.io.IOException e) {
            System.err.println("[PSHBHeadless] WARNING: could not write run_params.json: " + e.getMessage());
        }
    }

    // ---- argument parsers ----

    private static String parseString(String[] args, String key, String defaultVal) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(key)) return args[i + 1];
        }
        return defaultVal;
    }

    private static long parseLong(String[] args, String key, long defaultVal) {
        String v = parseString(args, key, null);
        return v == null ? defaultVal : Long.parseLong(v);
    }

    private static double parseDouble(String[] args, String key, double defaultVal) {
        String v = parseString(args, key, null);
        return v == null ? defaultVal : Double.parseDouble(v);
    }

    private static int parseInt(String[] args, String key, int defaultVal) {
        String v = parseString(args, key, null);
        return v == null ? defaultVal : Integer.parseInt(v);
    }

    private static boolean parseBoolean(String[] args, String key, boolean defaultVal) {
        String v = parseString(args, key, null);
        return v == null ? defaultVal : Boolean.parseBoolean(v);
    }
}