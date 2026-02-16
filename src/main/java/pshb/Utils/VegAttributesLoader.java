package pshb.Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    public Map<Integer, VegAttributes> getVegInformation(){
        //read in files
        List<String> lines; //read in data line by line, store as a String list
        path = Paths.get(fileDirectory);
        try{
            lines = Files.readAllLines(path); //read all lines
            data = lines.stream().skip(1).map(line -> line.split(",")).collect(Collectors.toList()); //extract the data from lines
        } catch (IOException e) {
//            throw new RuntimeException(e);
            e.printStackTrace();
        }

        int index = 0;
        //parse file information
        for(String[] lst:data){
            Integer patchID = Integer.valueOf(lst[0]); //1. key, patchID
            Integer terrID = Integer.valueOf(lst[1]); //2. terrID
            Double pointX = Double.valueOf(lst[2]); //3. POINT_X
            Double pointY = Double.valueOf(lst[3]); //4. POINT_Y
            Integer gisAcres = Integer.valueOf(lst[4]); //5. GISAcres
            Integer mapCode = Integer.valueOf(lst[5]); //6. MapCode
            String vegName = String.valueOf(lst[6]); //7. vegName
            Double pTrWillow = Double.valueOf(lst[7]); //8. pTrWillow
            Double pShWillowM = Double.valueOf(lst[8]); //9. pShWillowM
            Double pArundo = Double.valueOf(lst[9]); //10.pArundo
            Double pTamarisk = Double.valueOf(lst[10]); //11. pTamarisk
            Double pDieback = Double.valueOf(lst[11]); //12. pDieback
            Double elevM = Double.valueOf(lst[12]); //13. elevM
            Double slopeP = Double.valueOf(lst[13]); //14. slopeP
            String L1NetworkN = String.valueOf(lst[14]); //15. L1NetworkN
            Double sortOrder = Double.valueOf(lst[15]); //16. sortOrder
            String streamName = String.valueOf(lst[16]); //17. streamName
            Integer streamLeve = Integer.valueOf(lst[17]); //18. streamLevel
            String reservoirO = String.valueOf(lst[18]); //19. reservoirO
            Integer popID = Integer.valueOf(lst[19]); //20. popID
            String population = String.valueOf(lst[20]); //21. population
            String huc6Name = String.valueOf(lst[21]); //22. huc6Name
            String huc8Name = String.valueOf(lst[22]); //23. huc8Name
            String huc10Name = String.valueOf(lst[23]); //24. huc10Name
            String groundWate = String.valueOf(lst[24]); //25. groundWater
            String inSGMABasi = String.valueOf(lst[25]); //26. inSGMABasin
            String management = String.valueOf(lst[26]); //27. management
            String manageme1 = String.valueOf(lst[27]); //28. management_1
            String designatio = String.valueOf(lst[28]); //29. designatio
            String manageme2 = String.valueOf(lst[29]); //30. management_2
            String countyName = String.valueOf(lst[30]); //31. countyName
            String wwtpsubsid = String.valueOf(lst[31]); //32. WWTPSubside
            String wwtpName = String.valueOf(lst[32]); //33. WWTPName
            Double pLowQualit = Double.valueOf(lst[33]); //34. PLowQuality
            Double shapeLeng = Double.valueOf(lst[34]); //35. shape_Length
            Double shapeArea = Double.valueOf(lst[35]); //36. shape_Area
            VegAttributes groupInfo = new VegAttributes(patchID, terrID, pointX, pointY, gisAcres, mapCode, vegName, pTrWillow,
                    pShWillowM, pArundo, pTamarisk, pDieback, elevM, slopeP, L1NetworkN, sortOrder, streamName, streamLeve,
                    reservoirO, popID, population, huc6Name, huc8Name, huc10Name, groundWate, inSGMABasi, management,
                    manageme1, designatio, manageme2, countyName, wwtpsubsid, wwtpName, pLowQualit, shapeLeng, shapeArea); //create an vegAttibutes infoIdentifier called groupInfo from input data
            this.vegInfo.put(patchID, groupInfo); //add an index for each row
        }
        return this.vegInfo;

    }
}
