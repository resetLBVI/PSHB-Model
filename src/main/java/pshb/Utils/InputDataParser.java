package pshb.Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;



public class InputDataParser {
    String fileDirectory;
    Path path;
    List<String[]> data;
    Map<Integer, InfoIdentifier> info = new HashMap<Integer, InfoIdentifier>();

    public InputDataParser(String fileDirectory) {
        this.fileDirectory = fileDirectory;
    }

    public Map<Integer, InfoIdentifier> getDataInformation(){
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
            index ++; //key
            Double inputX = Double.valueOf(lst[0]); //first column
            Double inputY = Double.valueOf(lst[1]); //second column
            Integer numOfPSHBAgents = Integer.valueOf(lst[2]); //third column
            InfoIdentifier groupInfo = new InfoIdentifier(inputX, inputY, numOfPSHBAgents); //create an infoidentifier called groupInfo from input data
            this.info.put(index, groupInfo); //add an index for each row
        }
        return this.info;

    }
}
