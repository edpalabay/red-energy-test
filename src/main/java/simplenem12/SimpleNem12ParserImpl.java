package simplenem12;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;

/**
 * Simple NEM12 parser supporting local files and JAR resources.
 * Reads records (100, 200, 300, 900), builds MeterRead entries,
 * and groups interval volumes by NMI and date.
 * Throws IllegalArgumentException on invalid format or missing file/resource.
 */
public class SimpleNem12ParserImpl implements SimpleNem12Parser {
    private static final Logger LOG = Logger.getLogger(SimpleNem12ParserImpl.class.getName());
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Pattern DECIMAL = Pattern.compile("-?\\d+(\\.\\d+)?");

    /** Maintains parsing context */
    protected static class Cursor {
        MeterRead active;
        int line = 0;
        String lastRecord;
    }

    /**
     * Parses a NEM12 file from disk or JAR classpath.
     * @param file input file (not null, must exist)
     * @return parsed MeterRead records
     * @throws IllegalArgumentException on file/resource or format errors
     */
    @Override
    public Collection<MeterRead> parseSimpleNem12(File file) {
        if (file == null) {
            throw new IllegalArgumentException("Input file must not be null");
        }

        String path = file.getPath();

        // Only treat as JAR resource if it REALLY contains '!'
        if (path.contains("!")) {
            LOG.info("Detected JAR classpath resource path");
            String resource = path.substring(path.indexOf("!") + 1);

            // Clean leading slashes or backslashes
            while (resource.startsWith("/") || resource.startsWith("\\") || resource.startsWith("!")) {
                resource = resource.substring(1);
            }

            InputStream jarStream = getClass().getClassLoader().getResourceAsStream(resource);
            if (jarStream == null) {
                throw new IllegalArgumentException("Resource not found inside JAR: " + resource);
            }
            return parseFromStream(jarStream);
        }

        // Otherwise it's a normal disk file
        if (!file.exists()) {
            throw new IllegalArgumentException("File does not exist: " + path);
        }

        try {
            return parseFromStream(Files.newInputStream(file.toPath()));
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to open file: " + path, e);
        }
    }

    /**
     * Parses NEM12 records from an InputStream.
     * Reads non-empty rows, routes them by record type, and builds MeterRead entries.
     * @param inputStream source stream (UTF-8)
     * @return parsed MeterRead records
     * @throws IllegalArgumentException on read or format errors
     */
    private Collection<MeterRead> parseFromStream(InputStream inputStream) {
        LinkedHashSet<MeterRead> reads = new LinkedHashSet<>();
        Cursor cur = new Cursor();
        List<String> lines = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String raw;
            while ((raw = br.readLine()) != null) {
                cur.line++;
                String text = raw.trim();
                if (!text.isEmpty()) {
                    lines.add(text);
                    cur.lastRecord = text;
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read NEM12 stream", e);
        }

        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Input contains no valid NEM12 records");
        }

        Map<String, Consumer<Record>> router = buildRoutes(reads, cur);

        cur.line = 0;
        for (String text : lines) {
            cur.line++;
            Record record = Record.from(text, cur.line);
            Consumer<Record> handler = router.getOrDefault(record.type,
                    r -> { throw new IllegalArgumentException("Unsupported record type " + r.type + " at line " + cur.line); });
            handler.accept(record);
        }

        return reads;
    }

    /** Route table for record types */
    private Map<String, Consumer<Record>> buildRoutes(final Set<MeterRead> reads, final Cursor cur) {
        Map<String, Consumer<Record>> routes = new HashMap<>();

        routes.put("100", r -> {
            if (cur.line != 1) throw bad("Record 100 must appear on first line");
            LOG.fine("Processed 100 record at line " + cur.line);
        });

        routes.put("200", r -> {
            MeterRead m = r.toMeterRead();
            reads.add(m);
            cur.active = m;
            LOG.fine("Added MeterRead NMI=" + m.getNmi());
        });

        routes.put("300", r -> {
            if (cur.active == null) throw bad("Record 300 found without active 200 context at line " + cur.line);
            cur.active.appendVolume(r.date(), r.toMeterVolume());
            LOG.fine("Appended volume to NMI=" + cur.active.getNmi() + " on " + r.date());
        });

        routes.put("900", r -> {
            if (!r.text.equals(cur.lastRecord)) throw bad("Record 900 must be the last non-empty record at line " + cur.line);
            cur.active = null;
            LOG.fine("Processed 900, parsing complete");
        });

        return routes;
    }

    /** Shortcut for throwing parsing errors */
    private static IllegalArgumentException bad(String msg) {
        return new IllegalArgumentException(msg);
    }

    /**
     * Represents a parsed NEM12 row.
     */
    protected static class Record {
        final String type;
        final String text;
        final int line;
        final String[] v;

        private Record(String type, String text, int line, String[] v) {
            this.type = type;
            this.text = text;
            this.line = line;
            this.v = v;
        }

        /** Creates a Record from a CSV line */
        static Record from(String line, int lineNo) {
            if (line == null) throw new IllegalArgumentException("Null row at line " + lineNo);
            String[] parts = line.split(",", -1);
            if (parts.length == 0) throw new IllegalArgumentException("Empty record at line " + lineNo);
            return new Record(parts[0], line, lineNo, parts);
        }

        /** Parses and returns the record date */
        LocalDate date() {
            if (v.length < 2 || v[1] == null) throw new IllegalArgumentException("Missing date at line " + line);
            try {
                return LocalDate.parse(v[1], DATE_FORMAT);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid date '" + v[1] + "' at line " + line);
            }
        }

        /** Converts a 200 record into a MeterRead */
        MeterRead toMeterRead() {
            if (v.length != 3) throw bad("Invalid 200 format at line " + line);
            String nmi = v[1];
            if (nmi == null || nmi.length() != 10) throw bad("Invalid NMI at line " + line + ": " + nmi);
            if (!"KWH".equalsIgnoreCase(v[2])) throw bad("Unsupported unit at line " + line + ": " + v[2]);
            return new MeterRead(nmi, EnergyUnit.KWH);
        }

        /** Converts a 300 record into a MeterVolume */
        MeterVolume toMeterVolume() {
            if (v.length != 4) throw bad("Invalid 300 format at line " + line);
            if (!DECIMAL.matcher(v[2]).matches()) throw bad("Invalid volume at line " + line + ": " + v[2]);
            if (!"A".equals(v[3]) && !"E".equals(v[3])) throw bad("Invalid quality at line " + line + ": " + v[3]);
            return new MeterVolume(new BigDecimal(v[2]), Quality.valueOf(v[3]));
        }

        /** Error shortcut */
        private static IllegalArgumentException bad(String msg) {
            return new IllegalArgumentException(msg);
        }
    }

}
