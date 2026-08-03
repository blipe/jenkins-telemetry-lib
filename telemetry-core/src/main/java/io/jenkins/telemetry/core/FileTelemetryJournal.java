package io.jenkins.telemetry.core;

import io.jenkins.telemetry.api.JournalEntry;
import io.jenkins.telemetry.api.Signal;
import io.jenkins.telemetry.api.TelemetryEnvelope;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.zip.CRC32;

final class FileTelemetryJournal implements TelemetryJournal {
    private static final String SEGMENT_SUFFIX = ".seg";

    private final Path directory;
    private final Path cursorsFile;
    private final Path statsFile;
    private final long maxBytes;
    private final long segmentBytes;
    private final boolean forceWrites;
    private final Map<Signal, Long> latest = new EnumMap<>(Signal.class);
    private final Map<Signal, Long> totals = new EnumMap<>(Signal.class);
    private final Map<String, JournalCursor> cursors = new LinkedHashMap<>();
    private final Map<Signal, Set<String>> collectorsBySignal = new EnumMap<>(Signal.class);
    private long bytes;

    FileTelemetryJournal(Path directory, long maxBytes, long segmentBytes, boolean forceWrites)
        throws IOException {
        this.directory = directory;
        this.cursorsFile = directory.resolve("cursors.properties");
        this.statsFile = directory.resolve("stats.properties");
        this.maxBytes = maxBytes <= 0 ? 512L * 1024 * 1024 : maxBytes;
        this.segmentBytes = Math.max(1024, Math.min(
            segmentBytes <= 0 ? 8L * 1024 * 1024 : segmentBytes, this.maxBytes));
        this.forceWrites = forceWrites;
        Files.createDirectories(directory);
        for (Signal signal : Signal.values()) {
            latest.put(signal, 0L);
            totals.put(signal, 0L);
            collectorsBySignal.put(signal, new LinkedHashSet<>());
        }
        loadSegments();
        loadStats();
        loadCursors();
    }

    @Override
    public synchronized long append(TelemetryEnvelope envelope) throws IOException {
        Signal signal = envelope.signal();
        long sequence = latest.getOrDefault(signal, 0L) + 1L;
        TelemetryEnvelope sequenced = envelope.withSequence(sequence);
        String json = TelemetryMapper.encode(sequenced);
        byte[] encoded = encodeRecord(json);

        compact(signal);
        if (bytes + encoded.length > maxBytes) {
            throw new JournalCapacityException(
                "Telemetry journal quota exceeded: " + (bytes + encoded.length) + " > " + maxBytes);
        }

        Path segment = writableSegment(signal, sequence, encoded.length);
        try (FileChannel channel = FileChannel.open(segment,
            StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            ByteBuffer buffer = ByteBuffer.wrap(encoded);
            while (buffer.hasRemaining()) channel.write(buffer);
            if (forceWrites) channel.force(false);
        }
        bytes += encoded.length;
        latest.put(signal, sequence);
        totals.merge(signal, 1L, Long::sum);
        saveStats();
        return sequence;
    }

    @Override
    public synchronized JournalRead read(String collectorId, Signal signal, int maxRecords, int maxBatchBytes)
        throws IOException {
        JournalCursor cursor = cursors.getOrDefault(cursorKey(collectorId, signal), JournalCursor.ZERO);
        List<JournalEntry> entries = new ArrayList<>();
        int batchBytes = 0;
        long nextSequence = cursor.sequence();

        for (Path segment : segments(signal)) {
            SegmentRange range = inspectSegment(segment, false);
            if (range.lastSequence() <= cursor.sequence()) continue;
            try (RandomAccessFile input = new RandomAccessFile(segment.toFile(), "r")) {
                while (entries.size() < Math.max(1, maxRecords)) {
                    long lineStart = input.getFilePointer();
                    String raw = input.readLine();
                    if (raw == null) break;
                    long lineEnd = input.getFilePointer();
                    if (raw.isBlank()) continue;
                    String line = new String(raw.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                    DecodedRecord decoded = decodeRecord(line);
                    TelemetryEnvelope envelope = TelemetryMapper.decode(decoded.json());
                    if (envelope.sequence() <= cursor.sequence()) continue;
                    int encodedBytes = Math.toIntExact(lineEnd - lineStart);
                    if (!entries.isEmpty() && batchBytes + encodedBytes > Math.max(1, maxBatchBytes)) {
                        return new JournalRead(entries, nextSequence, 0);
                    }
                    entries.add(new JournalEntry(envelope.sequence(), signal, envelope, decoded.json()));
                    batchBytes += encodedBytes;
                    nextSequence = envelope.sequence();
                }
            }
            if (entries.size() >= Math.max(1, maxRecords)) break;
        }
        return new JournalRead(entries, nextSequence, 0);
    }

    @Override
    public synchronized void acknowledge(String collectorId, Signal signal, long sequence, long offset)
        throws IOException {
        String key = cursorKey(collectorId, signal);
        JournalCursor previous = cursors.getOrDefault(key, JournalCursor.ZERO);
        if (sequence < previous.sequence()) {
            throw new IllegalArgumentException("Cannot move telemetry cursor backwards");
        }
        cursors.put(key, new JournalCursor(sequence, 0));
        saveCursors();
        compact(signal);
    }

    @Override
    public synchronized void configureCollectors(Map<Signal, Set<String>> configured) throws IOException {
        for (Signal signal : Signal.values()) {
            Set<String> ids = configured == null
                ? Set.of() : configured.getOrDefault(signal, Set.of());
            collectorsBySignal.put(signal, new LinkedHashSet<>(ids));
            for (String id : ids) {
                cursors.putIfAbsent(cursorKey(id, signal), JournalCursor.ZERO);
            }
        }
        Set<String> valid = new LinkedHashSet<>();
        collectorsBySignal.forEach((signal, ids) ->
            ids.forEach(id -> valid.add(cursorKey(id, signal))));
        cursors.keySet().removeIf(key -> !valid.contains(key));
        saveCursors();
        for (Signal signal : Signal.values()) compact(signal);
    }

    @Override
    public synchronized long latestSequence(Signal signal) {
        return latest.getOrDefault(signal, 0L);
    }

    @Override
    public synchronized long acknowledgedSequence(String collectorId, Signal signal) {
        return cursors.getOrDefault(cursorKey(collectorId, signal), JournalCursor.ZERO).sequence();
    }

    @Override
    public synchronized long sizeBytes() {
        return bytes;
    }

    @Override
    public synchronized Map<Signal, Long> recordCounts() {
        return Map.copyOf(totals);
    }

    @Override
    public void close() {
    }

    private void loadSegments() throws IOException {
        bytes = 0;
        for (Signal signal : Signal.values()) {
            long last = 0;
            for (Path segment : segments(signal)) {
                SegmentRange range = inspectSegment(segment, true);
                bytes += Files.size(segment);
                last = Math.max(last, range.lastSequence());
            }
            latest.put(signal, last);
        }
    }

    private SegmentRange inspectSegment(Path segment, boolean recoverTail) throws IOException {
        long first = Long.MAX_VALUE;
        long last = 0;
        try (RandomAccessFile input = new RandomAccessFile(segment.toFile(), recoverTail ? "rw" : "r")) {
            long length = input.length();
            while (true) {
                long lineStart = input.getFilePointer();
                String raw = input.readLine();
                if (raw == null) break;
                long lineEnd = input.getFilePointer();
                if (raw.isBlank()) continue;
                try {
                    String line = new String(raw.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                    DecodedRecord decoded = decodeRecord(line);
                    TelemetryEnvelope envelope = TelemetryMapper.decode(decoded.json());
                    first = Math.min(first, envelope.sequence());
                    last = Math.max(last, envelope.sequence());
                } catch (RuntimeException failure) {
                    if (recoverTail && lineEnd >= length) {
                        input.setLength(lineStart);
                        break;
                    }
                    throw new IOException("Corrupt telemetry segment " + segment + " at byte " + lineStart,
                        failure);
                }
            }
        }
        return new SegmentRange(first == Long.MAX_VALUE ? 0 : first, last);
    }

    private void loadStats() throws IOException {
        if (!Files.exists(statsFile)) {
            for (Signal signal : Signal.values()) {
                long count = 0;
                for (Path segment : segments(signal)) {
                    try (var lines = Files.lines(segment, StandardCharsets.UTF_8)) {
                        count += lines.filter(line -> !line.isBlank()).count();
                    }
                }
                totals.put(signal, count);
            }
            saveStats();
            return;
        }
        Properties properties = new Properties();
        try (var input = Files.newInputStream(statsFile)) {
            properties.load(input);
        }
        for (Signal signal : Signal.values()) {
            totals.put(signal, Long.parseLong(properties.getProperty(signal.wireName(), "0")));
        }
    }

    private void saveStats() throws IOException {
        Properties properties = new Properties();
        totals.forEach((signal, count) -> properties.setProperty(signal.wireName(), Long.toString(count)));
        saveProperties(statsFile, properties, "Jenkins telemetry journal totals");
    }

    private void loadCursors() throws IOException {
        if (!Files.exists(cursorsFile)) return;
        Properties properties = new Properties();
        try (var input = Files.newInputStream(cursorsFile)) {
            properties.load(input);
        }
        for (String key : properties.stringPropertyNames()) {
            String raw = properties.getProperty(key, "0");
            String sequenceText = raw.contains(",") ? raw.substring(0, raw.indexOf(',')) : raw;
            cursors.put(key, new JournalCursor(Long.parseLong(sequenceText), 0));
        }
    }

    private void saveCursors() throws IOException {
        Properties properties = new Properties();
        cursors.forEach((key, cursor) ->
            properties.setProperty(key, Long.toString(cursor.sequence())));
        saveProperties(cursorsFile, properties, "Jenkins telemetry collector cursors");
    }

    private void saveProperties(Path target, Properties properties, String comment) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try (var output = Files.newOutputStream(temporary,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            properties.store(output, comment);
        }
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path writableSegment(Signal signal, long sequence, int recordBytes) throws IOException {
        List<Path> segments = segments(signal);
        if (!segments.isEmpty()) {
            Path last = segments.get(segments.size() - 1);
            if (Files.size(last) + recordBytes <= segmentBytes) return last;
        }
        return directory.resolve(segmentName(signal, sequence));
    }

    private void compact(Signal signal) throws IOException {
        Set<String> collectors = collectorsBySignal.getOrDefault(signal, Set.of());
        if (collectors.isEmpty()) return;
        long minimum = Long.MAX_VALUE;
        for (String collector : collectors) {
            minimum = Math.min(minimum, acknowledgedSequence(collector, signal));
        }
        if (minimum <= 0 || minimum == Long.MAX_VALUE) return;
        for (Path segment : segments(signal)) {
            SegmentRange range = inspectSegment(segment, false);
            if (range.lastSequence() > 0 && range.lastSequence() <= minimum) {
                long size = Files.size(segment);
                Files.deleteIfExists(segment);
                bytes = Math.max(0, bytes - size);
            }
        }
    }

    private List<Path> segments(Signal signal) throws IOException {
        List<Path> result = new ArrayList<>();
        String glob = signal.wireName() + "-*" + SEGMENT_SUFFIX;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, glob)) {
            for (Path path : stream) result.add(path);
        }
        result.sort(Comparator.comparingLong(FileTelemetryJournal::segmentFirstSequence));
        return result;
    }

    private static byte[] encodeRecord(String json) {
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        CRC32 crc = new CRC32();
        crc.update(jsonBytes);
        String prefix = String.format("%08x ", crc.getValue());
        byte[] prefixBytes = prefix.getBytes(StandardCharsets.US_ASCII);
        byte[] result = new byte[prefixBytes.length + jsonBytes.length + 1];
        System.arraycopy(prefixBytes, 0, result, 0, prefixBytes.length);
        System.arraycopy(jsonBytes, 0, result, prefixBytes.length, jsonBytes.length);
        result[result.length - 1] = '\n';
        return result;
    }

    private static DecodedRecord decodeRecord(String line) {
        if (line.length() < 10 || line.charAt(8) != ' ') {
            throw new IllegalArgumentException("Invalid telemetry record framing");
        }
        long expected = Long.parseUnsignedLong(line.substring(0, 8), 16);
        String json = line.substring(9);
        CRC32 crc = new CRC32();
        crc.update(json.getBytes(StandardCharsets.UTF_8));
        if (crc.getValue() != expected) {
            throw new IllegalArgumentException("Telemetry record checksum mismatch");
        }
        return new DecodedRecord(json);
    }

    private static String segmentName(Signal signal, long firstSequence) {
        return signal.wireName() + "-" + String.format("%020d", firstSequence) + SEGMENT_SUFFIX;
    }

    private static long segmentFirstSequence(Path path) {
        String name = path.getFileName().toString();
        int dash = name.lastIndexOf('-');
        int suffix = name.length() - SEGMENT_SUFFIX.length();
        return Long.parseLong(name.substring(dash + 1, suffix));
    }

    private static String cursorKey(String collectorId, Signal signal) {
        return collectorId.replace("=", "_") + "." + signal.wireName();
    }

    private record SegmentRange(long firstSequence, long lastSequence) {}
    private record DecodedRecord(String json) {}
}
