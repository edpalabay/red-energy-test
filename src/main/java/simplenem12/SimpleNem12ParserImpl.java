package simplenem12;

import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

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

    @Override
    public Collection<MeterRead> parseSimpleNem12(File file) {
        if (file == null) {
            throw new IllegalArgumentException("Input file must not be null");
        }

        LOG.info("Starting NEM12 parsing: " + file);
        LinkedHashSet<MeterRead> reads = new LinkedHashSet<>();
        Cursor cur = new Cursor();

        // Track last non-empty record while reading
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String raw;
            while ((raw = br.readLine()) != null) {
                cur.line++;
                String text = raw.trim();
                if (text.isEmpty()) continue;
                lines.add(text);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("File access failed at line " + cur.line + ": " + e.getMessage(), e);
        }

        if (lines.isEmpty()) throw new IllegalArgumentException("File is empty");
        cur.lastRecord = lines.get(lines.size() - 1);

        Map<String, Consumer<Record>> router = buildRoutes(reads, cur);

        // Parse each line
        cur.line = 0;
        for (String text : lines) {
            cur.line++;
            try {
                Record record = Record.from(text, cur.line);
                Consumer<Record> handler = router.getOrDefault(record.type, r -> {
                    throw new IllegalArgumentException("Unsupported record type " + r.type + " at line " + cur.line);
                });
                handler.accept(record);
            } catch (IllegalArgumentException ex) {
                LOG.log(Level.SEVERE, "Parse error at line " + cur.line + ": " + ex.getMessage(), ex);
                throw ex;
            }
        }

        LOG.info("Parsing completed successfully. Total meters: " + reads.size());
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
            if (cur.active == null) throw bad("Record 300 found without active 200 context");
            cur.active.appendVolume(r.date(), r.toMeterVolume());
            LOG.fine("Appended volume to NMI=" + cur.active.getNmi() + " on " + r.date());
        });

        routes.put("900", r -> {
            if (!r.text.equals(cur.lastRecord)) throw bad("Record 900 must be the last non-empty record");
            cur.active = null;
            LOG.fine("Processed 900 record, parsing complete");
        });

        return Collections.unmodifiableMap(routes);
    }

    /** Shortcut for throwing parsing errors */
    private static IllegalArgumentException bad(String msg) {
        return new IllegalArgumentException(msg);
    }

    /** Represents a parsed row */
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

        static Record from(String line, int lineNo) {
            if (line == null) {
                throw new IllegalArgumentException("Null row found at line " + lineNo);
            }

            String[] v = line.split(",", -1);
            if (v.length == 0) throw new IllegalArgumentException("Empty record at line " + lineNo);
            return new Record(v[0], line, lineNo, v);
        }

        LocalDate date() {
            if (v.length < 2 || v[1] == null) {
                throw bad("Missing date field at line " + line);
            }

            try {
                return LocalDate.parse(v[1], DATE_FORMAT);
            } catch (Exception ex) {
                throw new IllegalArgumentException("Invalid date '" + v[1] + "' at line " + line);
            }
        }

        MeterRead toMeterRead() {
            if (v.length != 3) throw bad("Invalid 200 record format at line " + line);
            String nmi = v[1];
            if (nmi == null || nmi.length() != 10)
                throw bad("Invalid NMI in 200 record at line " + line + ": " + nmi);
            if (!"KWH".equalsIgnoreCase(v[2]))
                throw bad("Unsupported energy unit in 200 record at line " + line + ": " + v[2]);
            return new MeterRead(nmi, EnergyUnit.KWH);
        }

        MeterVolume toMeterVolume() {
            if (v == null || v.length != 4) throw bad("Invalid 300 record format at line " + line);
            String vol = v[2];
            if (vol == null) {
                throw bad("Volume value is null at line " + line);
            }
            if (!DECIMAL.matcher(v[2]).matches())
                throw bad("Invalid decimal volume in 300 record at line " + line + ": " + v[2]);
            if (!"A".equals(v[3]) && !"E".equals(v[3]))
                throw bad("Invalid quality flag in 300 record at line " + line + ": " + v[3]);
            return new MeterVolume(new BigDecimal(v[2]), Quality.valueOf(v[3]));
        }
    }
}
