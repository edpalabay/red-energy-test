// Copyright Red Energy Limited 2017

package simplenem12;

import java.io.File;

import java.util.Collection;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Simple test harness for trying out SimpleNem12Parser implementation
 */
public class TestHarness {
    private static final Logger LOG = Logger.getLogger(TestHarness.class.getName());
    // Constant for the CSV file
    private static final String CSV_FILE = "SimpleNem12.csv";

  public static void main(String[] args) {

      ClassLoader classLoader = TestHarness.class.getClassLoader();
      File simpleNem12File = new File(Objects.requireNonNull(classLoader.getResource(CSV_FILE)).getFile());
      LOG.info("Parsing file: " + simpleNem12File.getPath());

      // Uncomment below to try out test harness.
      Collection<MeterRead> meterReads = new SimpleNem12ParserImpl().parseSimpleNem12(simpleNem12File);

      MeterRead read6123456789 = meterReads.stream().filter(mr -> mr.getNmi().equals("6123456789")).findFirst().get();
      System.out.printf("Total volume for NMI 6123456789 is %f%n", read6123456789.getTotalVolume());  // Should be -36.84

      MeterRead read6987654321 = meterReads.stream().filter(mr -> mr.getNmi().equals("6987654321")).findFirst().get();
      System.out.printf("Total volume for NMI 6987654321 is %f%n", read6987654321.getTotalVolume());  // Should be 14.33
  }
}
