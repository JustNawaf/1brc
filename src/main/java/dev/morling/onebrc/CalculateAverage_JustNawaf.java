package dev.morling.onebrc;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class CalculateAverage_JustNawaf {
    private static final String FILE = "./measurements.txt";
    private static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors();


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


    private static long getEndPosition(long startPos, long chunkSize) throws IOException {
        long endPos = startPos + chunkSize;
        RandomAccessFile file = new RandomAccessFile(FILE, "r");

        file.seek(endPos); // Move to the chunk end position
        int ch;

        while ((ch = file.read()) != -1) {
            if (ch == '\n') {
                endPos++;
                break;
            }
            endPos++;
        }

        file.close();
        return endPos;
    }

    private static ArrayList<Measurement> measurements(FileChannel channel, long startPos, long endPos) throws IOException {
        ArrayList<Measurement> measurements = new ArrayList<>();

        MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, startPos, endPos);

        StringBuilder line = new StringBuilder();

        while (buffer.hasRemaining()) {
            char c = (char) buffer.get();

            if(c == '\n'){
                measurements.add(new Measurement(line.toString().split(";")));
                line = new StringBuilder();
            }

            line.append(c);
        }

        channel.close();
        return measurements;
    }

    private static HashMap<Long, Long> getPositions(long startPos, long chunkSize) throws IOException {
        HashMap<Long, Long> positions = new HashMap<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            long endPos = getEndPosition(startPos, chunkSize);
            positions.put(startPos, endPos);
            startPos = endPos;
        }

        return positions;
    }

    private static ArrayList<Measurement> processMeasurements(FileChannel channel, Map<Long, Long> positions) throws InterruptedException, ExecutionException, FileNotFoundException {
        RandomAccessFile file = new RandomAccessFile(FILE, "r");
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        ArrayList<Future<ArrayList<Measurement>>> futures = new ArrayList<>();

        for (Map.Entry<Long, Long> entry : positions.entrySet()) {
            long startPos = entry.getKey();
            long endPos = entry.getValue();

            // Submit each measurement processing task
            Future<ArrayList<Measurement>> future = executor.submit(() -> {
                try {
                    return measurements(file.getChannel(), startPos, endPos);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            futures.add(future);
        }

        // Merge all thread outputs into a single list
        ArrayList<Measurement> mergedResults = new ArrayList<>();
        for (Future<ArrayList<Measurement>> future : futures) {
            mergedResults.addAll(future.get());
        }

        executor.shutdown();
        return mergedResults;
    }

    public static void main(String[] args) throws IOException {
        var startTime = System.currentTimeMillis();

        RandomAccessFile file = new RandomAccessFile(FILE, "r");
        System.out.println(file.length());
        FileChannel channel = file.getChannel();

        long chunkSize = file.length() / THREAD_COUNT;

        HashMap<Long, Long> positions = getPositions(0, chunkSize);


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
                a.sum = agg1.sum + agg2.sum;

                return a;
            },

            (agg) -> {
                return new ResultRow(agg.min, (Math.round(agg.sum * 10.0) / 10.0) / agg.count, agg.max);
            }
        );
        System.out.println(positions);
        positions.forEach((startPos, endPos) -> {

            try {
                ArrayList<Measurement> measurements = processMeasurements(channel, positions);

                Map<String, ResultRow> collect  = measurements.stream().collect(Collectors.groupingBy(Measurement::station, collector));
                System.out.println(collect);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
//
//

        double time = Math.round(((System.currentTimeMillis() - startTime) / 1000.0) * 10.0) / 10.0;

        System.out.print(STR."Total time taken: \{time} Second");
    }
}
