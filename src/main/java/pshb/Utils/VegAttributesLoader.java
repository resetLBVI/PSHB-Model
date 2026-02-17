package pshb.Utils;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * VegAttributesLoader is a utility class that reads a vegetation attributes CSV file and parses each row into
 * VegAttributes objects, returning a patchID-indexed map for spatial lookup within the PSHB model.
 */
public class VegAttributesLoader {
    String fileDirectory;
    Path path;
    List<String[]> data;
    Map<Integer, VegAttributes> vegInfo = new HashMap<Integer, VegAttributes>();

    public VegAttributesLoader(String fileDirectory) {
        this.fileDirectory = fileDirectory;
    }


    public Map<Integer, VegAttributes> getVegInformation() {
        path = Paths.get(fileDirectory);

        // Collect all problems
        List<String> problems = new ArrayList<>();
        Map<Integer, VegAttributes> vegInfoLocal = new HashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(path);
             CSVParser parser = CSVFormat.DEFAULT
                     .builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord r : parser) {
                long rec = r.getRecordNumber();      // 1-based record count (data rows only)
                int csvRow = (int) rec + 1;          // file row number (row 1 is header)

                // We'll try to extract patchID as a string for better error messages
                String patchRaw = safeGet(r, 0);
                String xRaw = safeGet(r, 2);
                String yRaw = safeGet(r, 3);

                // ---- Required fields validation (collect errors) ----
                Integer patchID = parseIntFlexible(patchRaw, csvRow, "patchID");
                if (patchID == null) {
                    problems.add("Row " + csvRow + " (record " + rec + "): patchID missing/invalid raw=[" + patchRaw + "]");
                    continue; // can't key the map without patchID
                }

                Double xObj = parseDoubleSafe(xRaw);
                if (xObj == null) {
                    problems.add("Row " + csvRow + " (record " + rec + ", patchID=" + patchID + "): POINT_X missing/invalid raw=[" + xRaw + "]");
                    continue;
                }

                Double yObj = parseDoubleSafe(yRaw);
                if (yObj == null) {
                    problems.add("Row " + csvRow + " (record " + rec + ", patchID=" + patchID + "): POINT_Y missing/invalid raw=[" + yRaw + "]");
                    continue;
                }

                // Now safe to unbox
                double pointX = xObj;
                double pointY = yObj;

                // ---- Optional fields (can be null) ----
                Integer terrID    = parseIntFlexible(safeGet(r, 1), csvRow, "terrID");
                Double gisAcres   = parseDoubleSafe(safeGet(r, 4));
                Integer mapCode   = parseIntFlexible(safeGet(r, 5), csvRow, "mapCode");
                String vegName    = safeGet(r, 6);

                Double pTrWillow  = parseDoubleSafe(safeGet(r, 7));
                Double pShWillowM = parseDoubleSafe(safeGet(r, 8));
                Double pArundo    = parseDoubleSafe(safeGet(r, 9));
                Double pTamarisk  = parseDoubleSafe(safeGet(r, 10));
                Double pDieback   = parseDoubleSafe(safeGet(r, 11));
                Double elevM      = parseDoubleSafe(safeGet(r, 12));
                Double slopeP     = parseDoubleSafe(safeGet(r, 13));
                String L1NetworkN = safeGet(r, 14);
                Double sortOrder  = parseDoubleSafe(safeGet(r, 15));
                String streamName = safeGet(r, 16);

                Integer streamLeve = parseIntFlexible(safeGet(r, 17), csvRow, "streamLevel");
                String reservoirO  = safeGet(r, 18);
                Integer popID      = parseIntFlexible(safeGet(r, 19), csvRow, "popID");
                String population  = safeGet(r, 20);

                String huc6Name    = safeGet(r, 21);
                String huc8Name    = safeGet(r, 22);
                String huc10Name   = safeGet(r, 23);
                String groundWate  = safeGet(r, 24);
                String inSGMABasi  = safeGet(r, 25);

                String management  = safeGet(r, 26);
                String manageme1   = safeGet(r, 27);
                String designatio  = safeGet(r, 28);
                String manageme2   = safeGet(r, 29);

                String countyName  = safeGet(r, 30);
                String wwtpsubsid  = safeGet(r, 31);
                String wwtpName    = safeGet(r, 32);

                Double pLowQualit  = parseDoubleSafe(safeGet(r, 33));
                Double shapeLeng   = parseDoubleSafe(safeGet(r, 34));
                Double shapeArea   = parseDoubleSafe(safeGet(r, 35));

                // If you want to enforce more "required" fields, validate them here and add to problems.

                VegAttributes groupInfo = new VegAttributes(
                        patchID, terrID, pointX, pointY, gisAcres, mapCode, vegName, pTrWillow,
                        pShWillowM, pArundo, pTamarisk, pDieback, elevM, slopeP, L1NetworkN, sortOrder,
                        streamName, streamLeve, reservoirO, popID, population, huc6Name, huc8Name, huc10Name,
                        groundWate, inSGMABasi, management, manageme1, designatio, manageme2, countyName,
                        wwtpsubsid, wwtpName, pLowQualit, shapeLeng, shapeArea
                );

                vegInfoLocal.put(patchID, groupInfo);
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to read veg attributes CSV: " + path.toAbsolutePath(), e);
        }

        // After reading all records, throw once if anything was bad
//        if (!problems.isEmpty()) {
//            StringBuilder sb = new StringBuilder();
//            sb.append("VegAttributes CSV validation failed: ").append(problems.size()).append(" bad row(s).\n");
//            sb.append("File: ").append(path.toAbsolutePath()).append("\n");
//            sb.append("Showing up to first 50 problems:\n");
//            for (int i = 0; i < Math.min(50, problems.size()); i++) {
//                sb.append("  - ").append(problems.get(i)).append("\n");
//            }
//            throw new IllegalArgumentException(sb.toString());
//        }
        if (!problems.isEmpty()) {
            System.out.println("VegAttributes CSV validation warning: "
                    + problems.size() + " bad row(s) skipped.");
            System.out.println("File: " + path.toAbsolutePath());

            for (int i = 0; i < Math.min(20, problems.size()); i++) {
                System.out.println("  - " + problems.get(i));
            }

            if (problems.size() > 20) {
                System.out.println("  ... (" + (problems.size() - 20) + " more not shown)");
            }
        }


        // If everything is good, update the instance map and return it
        this.vegInfo.clear();
        this.vegInfo.putAll(vegInfoLocal);
        return this.vegInfo;
    }

    /**
     * Safe getter: returns "" instead of throwing if column missing.
     * (With Commons CSV and correct file, you usually won't hit missing columns,
     *  but this keeps error reporting graceful.)
     */
    private static String safeGet(CSVRecord r, int idx) {
        if (idx >= r.size()) return "";
        String v = r.get(idx);
        return v == null ? "" : v.trim();
    }

    private static Integer parseIntFlexible(String s, int row, String colName) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty() || s.equalsIgnoreCase("NA") || s.equalsIgnoreCase("null")) return null;

        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            // if it's a decimal like "1.5807", parse as double then convert
            try {
                double d = Double.parseDouble(s);
                return (int) Math.round(d);   // or (int) Math.floor(d)
            } catch (NumberFormatException e2) {
                throw new NumberFormatException(
                        "Row " + row + " col " + colName + " cannot be parsed as int: [" + s + "]"
                );
            }
        }
    }

    private static Double parseDoubleSafe(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty() || s.equalsIgnoreCase("NA") || s.equalsIgnoreCase("null")) return null;
        return Double.valueOf(s);
    }




}
