package dev.morling.onebrc;
import java.io.IOException;
import java.io.RandomAccessFile;

public class CalculateAverage_JustNawaf {
    private static final String FILE = "./measurements.txt";
    private static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors();



    private static record Measurement(String station, double value) {
        public Measurement(String [] parts){
            this(parts[0], 0.0);
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
        // TOOD: FOLLOW THESE:
        // 1- Split the file into <Number of threads>.
        // 2- Each thread read it's own file chunk.
        // 3- Each Thread calculate it's own min,max,average.
        // 4- Merge.

        RandomAccessFile file = new RandomAccessFile(FILE, "r");

        long chunkSize = file.length() / THREAD_COUNT;

        System.out.println("Chunk size: " + chunkSize);

        for (int i = 0; i < THREAD_COUNT; i++) {

            // TODO Find better way to get startPos and endPos
            long startPos = i * chunkSize;
            long endPos = startPos + chunkSize;

            System.out.println("Start: " + startPos);
            System.out.println("End: " + endPos);

            file.seek(startPos);

            System.out.println("Response: " + file.readLine());
        }

//        Collector<Measurement, MeasurementAggregator, ResultRow> collector = Collector.of(
//            MeasurementAggregator::new,
//          (a, m) -> {
//              a.min = Math.min(a.min, m.value);
//              a.max = Math.max(a.max, m.value);
//              a.sum += m.value;
//              a.count++;
//          },
//
//            (agg1, agg2) -> {
//                var a = new MeasurementAggregator();
//                a.max = Math.max(agg1.max, agg2.max);
//                a.min = Math.min(agg1.min, agg2.min);
//                a.count = agg1.count + agg2.count;
//                a.sum = agg1.sum + agg2.sum;
//
//                return a;
//            },
//
//            (agg) -> {
//                return new ResultRow(agg.min, (Math.round(agg.sum * 10.0) / 10.0) / agg.count, agg.max);
//            }
//        );
//
//
//        var startTime = System.currentTimeMillis();
//        Map<String, ResultRow> collect = new TreeMap<>(
//                Files.lines(Path.of(FILE)).parallel()
//                        .map(record -> new Measurement("test", 15.0))
//                        .collect(Collectors.groupingBy(Measurement::station, collector))
//        );
//
//        double time = Math.round(((System.currentTimeMillis() - startTime) / 1000.0) * 10.0) / 10.0;
//
//        System.out.println(collect);
//        System.out.print(STR."Total time taken: \{time} Second");
    }
}
