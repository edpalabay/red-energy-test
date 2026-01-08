SimpleNem12Parser
A Java 8 implementation of a parser for simplified NEM12 files, which represent meter read data from smart meters.

Overview:
This project contains a parser that reads simplified NEM12 files and builds MeterRead objects with associated volumes (MeterVolume) and qualities (Quality).
The parser ensures:
Record 100 is first and 900 is last.
Record 200 defines a meter read block (NMI and EnergyUnit).
Record 300 defines daily volume readings for the preceding 200 block.
Only valid data (e.g., correct NMI length, date format, volume, quality) is accepted.

Requirements
Java 8 (source and target compatibility).
Solution implemented in Java.
Includes a TestHarness to run the parser.
Unit tests written with JUnit 5 (Jupiter).

CSV Format Specification
CSV data has no quotes or extra commas.
First column: RecordType (100, 200, 300, 900).
100 record: Must be the first line (file header).
900 record: Must be the last line (file footer).
200 record (MeterRead block start):
NMI: 10-character string (MeterRead.nmi)
EnergyUnit: enum (EnergyUnit.KWH) (MeterRead.energyUnit)
300 record (MeterVolume):
Date: yyyyMMdd (MeterRead.volumes key)
Volume: signed number (MeterVolume.volume)

Implementation
Class SimpleNem12ParserImpl implements the SimpleNem12Parser interface.
Parsing is done via parseSimpleNem12(File file), returning Collection<MeterRead>.

Handles:
File and resource existence checks.
Empty file detection.
Invalid record format detection.
Routing by record type (100, 200, 300, 900).
Throws IllegalArgumentException on errors.

Domain model classes:
EnergyUnit
MeterRead
MeterVolume
Quality
Quality: Actual (A) or Estimate (E) (MeterVolume.quality)

Assumption: No duplicate 200 records with the same NMI per file. All other .csv files strictly follow the format and sequence of keys in the given SimpleNem12.csv file.

Running the Project

Clone the repository:
git clone https://github.com/edpalabay/red-energy-test.git

cd SimpleNem12Parser

Build the project with Gradle:
./gradlew build

Run the test harness (example):
./gradlew run

Or run tests:
./gradlew test

Tests
Unit tests written using JUnit 5 (Jupiter).

Tests cover:
Positive cases: valid NEM12 file parsing, verifying NMI, EnergyUnit, volumes, and qualities.
Negative cases: null file, missing file, empty file, invalid 200/300 records, 300 without 200, 900 not last.
Edge cases: multiple 200 records, multiple 300 volumes, extra whitespace lines.

Tests use @TempDir to create temporary CSV files, ensuring isolation and reproducibility.

The assumption is that we are only dealing with the files given in this exam. 
However below are the Questions / Clarifications that I had in mind:
  - Are there multiple 200 records with the same NMI across files or never?
  - Should 300 records always follow a 200 record?
  - Are quality values limited strictly to 'A' and 'E'?
  - Should volumes be parsed strictly as BigDecimal?
  - How to handle future expansions of NEM12 record types?

Thank you!
