package pshb.Utils;

/**
 * VegAttributes is an immutable data container class that stores detailed vegetation patch attributes parsed
 * from the input dataset, including spatial, ecological, hydrological, and management-related information used
 * by the PSHB model.
 */
public class VegAttributes {
    // --- numeric ---
    public final int patchID; //#1
    public final Integer terrID; //#2 nullable (CSV has blanks -> NaN)
    public final double pointX; //#3
    public final double pointY; //#4
    public final double gisAcres; //#5
    public final int mapCode; //#6
    public final String vegName; //#7

    public final double pTrWillow; //#8
    public final double pShWillowM; //#9
    public final double pArundo; //#10
    public final double pTamarisk; //#111
    public final double pDieback; //#12

    public final double elevM; //#13
    public final double slopeP; //#14
    public final String l1NetworkN; //#15
    public final double sortOrder; //#16 in the file it's float-like
    public final String streamName; //#17
    public final int streamLeve; //#18
    public final String reservoirO; //#19
    public final int popID; //#20
    public final String population; //#21

    public final String huc6Name; //#22
    public final String huc8Name; //#23
    public final String huc10Name; //#24

    public final String groundwate; //#25
    public final String inSGMABasi; //#26
    public final String management; //#27
    public final String manageme1; //#28
    public final String designatio; //#29
    public final String manageme2; //#30
    public final String countyName; //#31
    public final String wwtpSubsid; //#32
    public final String wwtpName; //#33
    public final double pLowQualit; //#34
    public final double shapeLeng; //#35
    public final double shapeArea; //#36

    public VegAttributes(int patchID, Integer terrID, double pointX, double pointY, double gisAcres, int mapCode,
                         String vegName, double pTrWillow, double pShWillowM, double pArundo, double pTamarisk,
                         double pDieback, double elevM, double slopeP, String l1NetworkN, double sortOrder,
                         String streamName, int streamLeve, String reservoirO, int popID, String population,
                         String huc6Name, String huc8Name, String huc10Name, String groundwate, String inSGMABasi,
                         String management, String manageme1, String designatio, String manageme2, String countyName,
                         String wwtpSubsid, String wwtpName, double pLowQualit, double shapeLeng, double shapeArea) {
        this.patchID = patchID;
        this.terrID = terrID;
        this.pointX = pointX;
        this.pointY = pointY;
        this.gisAcres = gisAcres;
        this.mapCode = mapCode;
        this.vegName = vegName;
        this.pTrWillow = pTrWillow;
        this.pShWillowM = pShWillowM;
        this.pArundo = pArundo;
        this.pTamarisk = pTamarisk;
        this.pDieback = pDieback;
        this.elevM = elevM;
        this.slopeP = slopeP;
        this.l1NetworkN = l1NetworkN;
        this.sortOrder = sortOrder;
        this.streamName = streamName;
        this.streamLeve = streamLeve;
        this.reservoirO = reservoirO;
        this.popID = popID;
        this.population = population;
        this.huc6Name = huc6Name;
        this.huc8Name = huc8Name;
        this.huc10Name = huc10Name;
        this.groundwate = groundwate;
        this.inSGMABasi = inSGMABasi;
        this.management = management;
        this.manageme1 = manageme1;
        this.designatio = designatio;
        this.manageme2 = manageme2;
        this.countyName = countyName;
        this.wwtpSubsid = wwtpSubsid;
        this.wwtpName = wwtpName;
        this.pLowQualit = pLowQualit;
        this.shapeLeng = shapeLeng;
        this.shapeArea = shapeArea;
    }
}
