package dev.morling.onebrc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class CalculateAverage_JustNawaf {
    private static final String FILE = "./measurements.txt";

    private static record Measurement(String station, double value) {
        public Measurement(String [] parts){
            this(parts[0], Double.parseDouble(parts[1]));
        }
    }

    private static record ResultRow(double min, double mean, double max) {

        public String toString() {
            return STR."\{round(min)}/\{round(mean)}/\{round(max)}";
        }

        private double round(double value) {
            return Math.round(value * 10.0) / 10.0;
        }
    };

    private static class MeasurementAggregator {
        private double min = Double.POSITIVE_INFINITY;
        private double max = Double.NEGATIVE_INFINITY;
        private double sum;
        private long count;
    }

    public static void main(String[] args) throws IOException {

        Collector<Measurement, MeasurementAggregator, ResultRow> collector = Collector.of(
            MeasurementAggregator::new,
          (a, m) -> {
              a.min = Math.min(a.min, m.value);
              a.max = Math.max(a.max, m.value);
              a.sum += m.value;
              a.count++;
          },

            (agg1, agg2) -> {
                var a = new MeasurementAggregator();
                a.max = Math.max(agg1.max, agg2.max);
                a.min = Math.min(agg1.min, agg2.min);
                a.count = agg1.count + agg2.count;
                a.sum += agg1.sum;

                return a;
            },

            (agg) -> {
                return new ResultRow(agg.min, (Math.round(agg.sum * 10.0) / 10.0) / agg.count, agg.max);
            }
        );


        var startTime = System.currentTimeMillis();
        Map<String, ResultRow> collect = new ConcurrentHashMap<>(
                Files.lines(Path.of(FILE)).parallel()
                        .map(record -> new Measurement(record.split(";")))
                        .collect(Collectors.groupingByConcurrent(Measurement::station, collector))
        );

        double time = Math.round(((System.currentTimeMillis() - startTime) / 1000.0) * 10.0) / 10.0;

        System.out.println(collect);
        System.out.print(STR."Total time taken: \{time} Second");
    }
}
