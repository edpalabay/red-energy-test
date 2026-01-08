package simplenem12;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.io.FileWriter;
import java.util.Collection;
import static org.junit.jupiter.api.Assertions.*;

public class SimpleNem12ParseImplTest {

    @TempDir
    File tempDir;

    private File write(String name, String content) throws Exception {
        File f = new File(tempDir, name);
        try (FileWriter w = new FileWriter(f)) {
            w.write(content);
        }
        return f;
    }

    @Test
    public void parsesValidFile() throws Exception {
        String data =
                "100,NEM12\n" +
                        "200,1234567890,KWH\n" +
                        "300,20250101,10.5,A\n" +
                        "300,20250102,5,E\n" +
                        "900\n";

        File f = write("ok.csv", data);
        SimpleNem12Parser parser = new SimpleNem12ParserImpl();
        Collection<MeterRead> out = parser.parseSimpleNem12(f);

        assertEquals(1, out.size());
        MeterRead m = out.iterator().next();
        assertEquals("1234567890", m.getNmi());
        assertEquals(EnergyUnit.KWH, m.getEnergyUnit());
        assertEquals(2, m.getVolumes().size());
    }

    @Test
    public void failsOnNullFile() {
        SimpleNem12Parser parser = new SimpleNem12ParserImpl();
        assertThrows(IllegalArgumentException.class, () -> parser.parseSimpleNem12(null));
    }

    @Test
    public void failsOnEmptyFile() throws Exception {
        File f = write("empty.csv", "");
        SimpleNem12Parser parser = new SimpleNem12ParserImpl();
        assertThrows(IllegalArgumentException.class, () -> parser.parseSimpleNem12(f));
    }

    @Test
    public void failsIf100NotFirst() throws Exception {
        String data = "200,1234567890,KWH\n100,NEM12\n900";
        File f = write("bad100.csv", data);
        SimpleNem12Parser parser = new SimpleNem12ParserImpl();
        assertThrows(IllegalArgumentException.class, () -> parser.parseSimpleNem12(f));
    }

    @Test
    public void failsIf300Before200() throws Exception {
        String data = "200,NEM12\n300,20250101,1.0,A\n900";
        File f = write("bad300.csv", data);
        SimpleNem12Parser parser = new SimpleNem12ParserImpl();
        assertThrows(IllegalArgumentException.class, () -> parser.parseSimpleNem12(f));
    }

    @Test
    public void failsIf900NotLastNonEmpty() throws Exception {
        String data = "100,NEM12\n200,1234567890,KWH\n900\n300,20250101,1.0,A";
        File f = write("bad900.csv", data);
        SimpleNem12Parser parser = new SimpleNem12ParserImpl();
        assertThrows(IllegalArgumentException.class, () -> parser.parseSimpleNem12(f));
    }

    @Test
    public void failsOnBadNmiLength() throws Exception {
        String data = "100,NEM12\n200,123,KWH\n900";
        File f = write("badnmi.csv", data);
        SimpleNem12Parser parser = new SimpleNem12ParserImpl();
        assertThrows(IllegalArgumentException.class, () -> parser.parseSimpleNem12(f));
    }

    @Test
    public void failsOnUnsupportedUnit() throws Exception {
        String data = "100,NEM12\n200,1234567890,MWH\n900";
        File f = write("badunit.csv", data);
        SimpleNem12Parser parser = new SimpleNem12ParserImpl();
        assertThrows(IllegalArgumentException.class, () -> parser.parseSimpleNem12(f));
    }

    @Test
    public void failsOnInvalidVolumeDecimal() throws Exception {
        String data = "100,NEM12\n200,1234567890,KWH\n300,20250101,XYZ,A\n900";
        File f = write("badvol.csv", data);
        SimpleNem12Parser parser = new SimpleNem12ParserImpl();
        assertThrows(IllegalArgumentException.class, () -> parser.parseSimpleNem12(f));
    }

    @Test
    public void failsOnBadQualityFlag() throws Exception {
        String data = "100,NEM12\n200,1234567890,KWH\n300,20250101,1.0,X\n900";
        File f = write("badqual.csv", data);
        SimpleNem12Parser parser = new SimpleNem12ParserImpl();
        assertThrows(IllegalArgumentException.class, () -> parser.parseSimpleNem12(f));
    }

    @Test
    public void failsOnMalformedRecord() throws Exception {
        String data = "100,NEM12\n200,1234567890,KWH\nTHIS_IS_NOT_A_VALID_ROW\n900";
        File f = write("malformed.csv", data);
        SimpleNem12Parser parser = new SimpleNem12ParserImpl();
        assertThrows(IllegalArgumentException.class, () -> parser.parseSimpleNem12(f));
    }

    @Test
    public void failsOnMissingFields() throws Exception {
        String data = "100,NEM12\n200,1234567890,KWH\n300,20250101,,A\n900";
        File f = write("missing.csv", data);
        SimpleNem12Parser parser = new SimpleNem12ParserImpl();
        assertThrows(IllegalArgumentException.class, () -> parser.parseSimpleNem12(f));
    }

    @Test
    public void failsOnExtraFieldsIn200() throws Exception {
        String data = "100,NEM12\n200,1234567890,KWH,EXTRA\n900";
        File f = write("extra200.csv", data);
        SimpleNem12Parser parser = new SimpleNem12ParserImpl();
        assertThrows(IllegalArgumentException.class, () -> parser.parseSimpleNem12(f));
    }

    @Test
    public void failsOnInvalidDateFormat() throws Exception {
        String data = "100,NEM12\n200,1234567890,KWH\n300,01-01-2025,10.0,A\n900";
        File f = write("badDate.csv", data);
        SimpleNem12Parser parser = new SimpleNem12ParserImpl();
        assertThrows(IllegalArgumentException.class, () -> parser.parseSimpleNem12(f));
    }
}
