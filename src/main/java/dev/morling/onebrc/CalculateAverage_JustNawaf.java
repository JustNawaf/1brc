package dev.morling.onebrc;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;

public class CalculateAverage_JustNawaf {
    private static final String FILE = "./measurements.txt";
    private static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors();
//    private static final int THREAD_COUNT = 1;
    private static Map<String, Integer> index = new HashMap<>();
    private static ArrayList<Result> results = new ArrayList<>();


    private static record Measurement(String station, double value) {
        public Measurement(String [] parts){
            this(parts[0].trim(), Double.parseDouble(parts[1]));
        }
    }

    private static class Result {
        private String station;
        private double min = Double.POSITIVE_INFINITY;
        private double max = Double.NEGATIVE_INFINITY;
        private double average = 0;
        private double sum = 0;
        private int count = 0;

        public Result(String station) {
            this.station = station;
        }

        public void addTemparture(double temp){
            this.min = Math.min(this.min, temp);
            this.max = Math.max(this.max, temp);
            this.sum += temp;
            this.count++;
            this.average = Math.round((this.sum / this.count) * 10.0) / 10.0;
        }

        public String toString() {
            return STR."\{station}=\{min}/\{average}/\{max}";
        }
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

    private static ArrayList<MappedByteBuffer> getBuffers() throws IOException {
        ArrayList<MappedByteBuffer> buffers = new ArrayList<MappedByteBuffer>();
        RandomAccessFile file = new RandomAccessFile(FILE, "r");
        FileChannel channel = file.getChannel();

        long chunkSize = channel.size() / (THREAD_COUNT);
        long startPos = 0;

        for (int i = 0; i < (THREAD_COUNT); i++) {
            long endPos = getEndPosition(startPos, chunkSize);
            long size = Math.min(endPos, channel.size()) - startPos;

            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, startPos, size);
            buffers.add(buffer);
            startPos = endPos;
        }

        return buffers;
    }

    public static void main(String[] args) throws IOException {
        ArrayList<MappedByteBuffer> buffers = getBuffers();

        // TODO: FINISH IN 29 SECONDS, NEEDS ENHANCEMENTS.

        long startTime = System.currentTimeMillis();

        buffers.parallelStream().forEach(buffer -> {
            StringBuilder line = new StringBuilder();

            while (buffer.hasRemaining()) {
                try{
                    char c = (char) buffer.get();
                    if(c == '\n'){
                        Measurement mgr = new Measurement(line.toString().split(";"));

                        if(index.containsKey(mgr.station())){
                            Result result = results.get(index.get(mgr.station()));
                            result.addTemparture(mgr.value());
                        } else {
                            Result r = new Result(mgr.station());
                            r.addTemparture(mgr.value());
                            results.add(r);
                            index.put(mgr.station(), results.size() - 1);
                        }

                        line = new StringBuilder();
                    }

                    line.append(c);
                }
                 catch (Exception e){
                    break;
                 }
            }
        });

        long endTime = System.currentTimeMillis();
        for (Result r : results) {
            System.out.println(r);
        }

        System.out.println((endTime - startTime) / 1000 + " seconds");
        System.out.println("Total measurements: " + results.size());
    }
}
