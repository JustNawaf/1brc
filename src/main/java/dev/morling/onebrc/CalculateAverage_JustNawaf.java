package dev.morling.onebrc;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;

public class CalculateAverage_JustNawaf {
    private static final String FILE = "./measurements.txt";
    private static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors();
    public static final int CITY_NAME_MAX_CHARACTERS = 128;


    private static record Measurement(String station, int value) {}

    private static int readNumberFromBuffer(ByteBuffer buffer) {
        var number = 0;
        var sign = 1;
        while (buffer.hasRemaining()) {
            var numberByte = buffer.get();
            if (numberByte == '-')
                sign = -1;
            else if (numberByte == '\n')
                break;
            else if (numberByte != '.')
                number = number * 10 + (numberByte - '0');
        }
        return sign * number;
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

        public Result merge(Result partialResult) {
            Result n = new Result(this.station);
            n.min = Math.min(this.min, partialResult.min);
            n.max = Math.max(this.max, partialResult.max);
            n.sum += partialResult.sum;
            n.count += partialResult.count;
            n.average = Math.round((this.sum / this.count) * 10.0) / 10.0;

            return n;
        }

        public String toString() {
            return station + "=%.1f/%.1f/%.1f".formatted(min / 10.0, sum / 10.0 / count, max / 10.0);
        }
    }

    private static class ResultAgg {
        Map<String, Result> results = new HashMap<>(10_000, 1);


        public void add(Measurement measurement) {
            if(results.containsKey(measurement.station())){
                Result result = results.get(measurement.station());
                result.addTemparture(measurement.value());
            } else {
                Result r = new Result(measurement.station());
                r.addTemparture(measurement.value());
                results.put(measurement.station(), r);
            }
        }

        public void merge(ResultAgg other) {
            other.results.forEach((station, result) -> results.merge(station, result, (existing, incoming) -> {
                existing.min = Math.min(existing.min, incoming.min);
                existing.max = Math.max(existing.max, incoming.max);
                existing.sum += incoming.sum;
                existing.count += incoming.count;
                existing.average = Math.round((existing.sum / existing.count) * 10.0) / 10.0;
                return existing;
            }));
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

        ResultAgg values = buffers.parallelStream().map(buffer -> {
            byte[] name = new byte[CITY_NAME_MAX_CHARACTERS];
            int value = 0;
            
            boolean pass = false;

            ResultAgg agg = new ResultAgg();
            int lastIndexName = 0;

            while (buffer.hasRemaining()) {

                try{
                    byte c = buffer.get();
                    if(pass){
                        agg.add(new Measurement(new String(name, 0, lastIndexName), value));
                        lastIndexName = 0;
                        pass = false;
                    }

                    if((char) c == ';'){
                        int sign = 1;
                        int number = 0;

                        while(buffer.hasRemaining()){
                            byte b = buffer.get();

                            if((char) b == '\n'){
                                pass = true;
                                break;
                            }

                            if((char) b == '-'){
                                sign = -1;
                                continue;
                            }

                            if((char) b != '.'){
                                number = number * 10 + (b - '0');
                            }
                        }

                        value = sign * number;

                    } else {
                        name[lastIndexName++] = c;
                    }
                }
                 catch (Exception e){
                    break;
                 }
            }

            return agg;
        }).reduce(new ResultAgg(), (agg1, agg2) -> {
            ResultAgg result = new ResultAgg();
            result.merge(agg1);
            result.merge(agg2);
            return result;
        });

        long endTime = System.currentTimeMillis();
        for (Result r : values.results.values()) {
            System.out.println(r);
        }

        System.out.println((endTime - startTime) / 1000 + " seconds");
        System.out.println("Total measurements: " + values.results.size());
    }
}
