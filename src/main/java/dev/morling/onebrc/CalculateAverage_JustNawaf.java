package dev.morling.onebrc;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class CalculateAverage_JustNawaf {
    private static final String FILE = "./measurements.txt";
//    private static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int THREAD_COUNT = 1;


    // TODO: CREATE A CLASS TO COLLECT MEASUREMENTS AND CALCULATING IT.
    // TODO: REWRITE THE CODE.
    // TODO: IDEA: SPLIT THE FILES INTO CHUNKS AND PROCESS THEM IN PARALLEL THEN MERGE IT.
    
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

    private static String getX(RandomAccessFile file) throws IOException {
        return file.readLine();
    }

    private static ArrayList<MappedByteBuffer> getBuffers() throws IOException {
        ArrayList<MappedByteBuffer> buffers = new ArrayList<MappedByteBuffer>();
        RandomAccessFile file = new RandomAccessFile(FILE, "r");
        long chunkSize = file.length() / THREAD_COUNT;
        long startPos = 0;
        FileChannel channel = file.getChannel();

        for (int i = 0; i < THREAD_COUNT; i++) {
            long endPos = getEndPosition(startPos, chunkSize);
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, startPos, (int) endPos - startPos);
            buffers.add(buffer);
            startPos = endPos;
        }

        return buffers;
    }

    private static ArrayList<ResultRow> processMeasurements(ArrayList<MappedByteBuffer> buffers) throws InterruptedException, ExecutionException {
        ArrayList<ResultRow> results = new ArrayList<>();

        System.out.println(buffers);

        List<ArrayList<MeasurementAggregator>> l = buffers.stream().map(buffer -> {
            System.out.println("Starting measurements");
            System.out.println(buffer.hasRemaining());

            Map<String, Integer> index = new HashMap<>();
            ArrayList<MeasurementAggregator> aggs = new ArrayList<>();

            StringBuilder line = new StringBuilder();

            while (buffer.hasRemaining()) {
                char c = (char) buffer.get();

                if(c == '\n'){
                    Measurement mgr = new Measurement(line.toString().split(";"));

                    if(index.containsKey(mgr.station())){
                        MeasurementAggregator agg = aggs.get(index.get(mgr.station));
                        agg.min = Math.min(agg.min, mgr.value());
                        agg.max = Math.max(agg.max, mgr.value());
                        agg.count++;
                        agg.sum += mgr.value();
                    } else {
                        MeasurementAggregator agg  = new MeasurementAggregator();
                        agg.min = mgr.value();
                        agg.max = mgr.value();
                        agg.count++;
                        agg.sum += mgr.value();
                        aggs.add(agg);

                        index.put(mgr.station(), aggs.indexOf(agg));
                    }

                    line = new StringBuilder();
                }

                line.append(c);
            }

            System.out.println("Total measurements: " + aggs.size());
            return aggs;
        }).toList();

//        for (ArrayList<MeasurementAggregator> aggs : l) {
//            for(MeasurementAggregator agg : aggs){
//                System.out.println(agg.max);
//            }
//        }
        return results;
    }

    public static void main(String[] args) throws IOException {
        var startTime = System.currentTimeMillis();

        RandomAccessFile file = new RandomAccessFile(FILE, "r");

        ArrayList<MappedByteBuffer> buffers = getBuffers();

        try {
            ArrayList<ResultRow> measurements = processMeasurements(buffers);

//            Map<String, ResultRow> collect  = measurements.stream().collect(Collectors.groupingBy(Measurement::station, collector));

//            System.out.println(collect);

        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
//
//

        double time = Math.round(((System.currentTimeMillis() - startTime) / 1000.0) * 10.0) / 10.0;

        System.out.print(STR."Total time taken: \{time} Second");
    }
}
