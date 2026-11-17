package com.group3.operator;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.group3.data.DataType;
import com.group3.data.Row;
import com.group3.data.Table;
import com.group3.data.Value;

public class GraceHashJoinOperator extends JoinOperator {
    private final int partitionCount;
    private final int maxBuildRows;
    private final Path tempDirectory;

    public GraceHashJoinOperator() {
        this(16, 100_000, null);
    }

    public GraceHashJoinOperator(int partitionCount, int maxBuildRows, Path tempDirectory) {
        if (partitionCount < 2 || partitionCount > 256) {
            throw new IllegalArgumentException("Partition count must be between 2 and 256");
        }
        if (maxBuildRows < 1) {
            throw new IllegalArgumentException("Build-row limit must be positive");
        }
        this.partitionCount = partitionCount;
        this.maxBuildRows = maxBuildRows;
        this.tempDirectory = tempDirectory;
    }

    @Override
    protected List<Row> joinRows(Table left, Table right, int leftIndex, int rightIndex) {
        if (left.size() == 0 || right.size() == 0) {
            return new ArrayList<>();
        }

        try (TemporaryPartitions files = new TemporaryPartitions()) {
            int[] leftRowCounts = partition(left, leftIndex, files.directory, "left");
            int[] rightRowCounts = partition(right, rightIndex, files.directory, "right");
            checkSizes(leftRowCounts, rightRowCounts);

            List<Row> result = new ArrayList<>();
            for (int partitionIndex = 0; partitionIndex < partitionCount; partitionIndex++) {
                if (leftRowCounts[partitionIndex] == 0 || rightRowCounts[partitionIndex] == 0) {
                    continue;
                }
                boolean buildLeftSide = leftRowCounts[partitionIndex] < rightRowCounts[partitionIndex];
                joinPartition(files.directory, partitionIndex, leftIndex, rightIndex, buildLeftSide, result);
            }
            return result;
        } catch (IOException | ClassNotFoundException exception) {
            throw new IllegalStateException("Unable to execute disk-partitioned hash join", exception);
        }
    }

    private void checkSizes(int[] leftRowCounts, int[] rightRowCounts) {
        for (int partitionIndex = 0; partitionIndex < partitionCount; partitionIndex++) {
            int buildRows = Math.min(leftRowCounts[partitionIndex], rightRowCounts[partitionIndex]);
            // splitting an oversized partition again would require more than two passes.
            if (buildRows > maxBuildRows) {
                throw new IllegalStateException("Grace hash join partition " + partitionIndex
                    + " exceeds the build-row limit on both sides; increase partitions or "
                    + "the limit. A heavily skewed key may require another join strategy.");
            }
        }
    }

    private void joinPartition(Path directory, int partitionIndex, int leftIndex, int rightIndex,
            boolean buildLeftSide, List<Row> result) throws IOException, ClassNotFoundException {
        Path buildFile;
        Path probeFile;
        int buildKeyIndex;
        int probeKeyIndex;
        if (buildLeftSide) {
            buildFile = filePath(directory, "left", partitionIndex);
            probeFile = filePath(directory, "right", partitionIndex);
            buildKeyIndex = leftIndex;
            probeKeyIndex = rightIndex;
        } else {
            buildFile = filePath(directory, "right", partitionIndex);
            probeFile = filePath(directory, "left", partitionIndex);
            buildKeyIndex = rightIndex;
            probeKeyIndex = leftIndex;
        }

        Map<Object, List<Row>> rowsByKey = loadPartition(buildFile, buildKeyIndex);
        try (ObjectInputStream input = openInput(probeFile)) {
            Row probeRow;
            while ((probeRow = readRow(input)) != null) {
                List<Row> matches = rowsByKey.get(keyOf(probeRow.get(probeKeyIndex)));
                if (matches == null) {
                    continue;
                }
                for (Row buildRow : matches) {
                    // output columns must stay left-first even when the left side builds the hash table.
                    if (buildLeftSide) {
                        result.add(combine(buildRow, probeRow));
                    } else {
                        result.add(combine(probeRow, buildRow));
                    }
                }
            }
        }
    }

    private Map<Object, List<Row>> loadPartition(Path file, int keyIndex)
            throws IOException, ClassNotFoundException {
        Map<Object, List<Row>> rowsByKey = new HashMap<>();
        try (ObjectInputStream input = openInput(file)) {
            Row row;
            while ((row = readRow(input)) != null) {
                Object key = keyOf(row.get(keyIndex));
                List<Row> matches = rowsByKey.get(key);
                if (matches == null) {
                    matches = new ArrayList<>();
                    rowsByKey.put(key, matches);
                }
                matches.add(row);
            }
        }
        return rowsByKey;
    }

    private int[] partition(Table table, int keyIndex, Path directory, String side) throws IOException {
        int[] rowCounts = new int[partitionCount];
        try (PartitionWriters writers = new PartitionWriters()) {
            for (int partitionIndex = 0; partitionIndex < partitionCount; partitionIndex++) {
                Path file = filePath(directory, side, partitionIndex);
                writers.streams.add(new ObjectOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(file))));
            }
            for (Row row : table.getRows()) {
                // null join keys never match, including other null keys.
                if (row.get(keyIndex).isNull()) {
                    continue;
                }
                int partitionIndex = partitionIndex(row.get(keyIndex));
                writeRow(writers.streams.get(partitionIndex), row);
                rowCounts[partitionIndex]++;
            }
        }
        return rowCounts;
    }

    private int partitionIndex(Value key) {
        int hash = keyOf(key).hashCode();
        // numeric keys often have zero low bits; mix all bits to avoid one crowded partition.
        hash ^= hash >>> 16;
        hash *= 0x7feb352d;
        hash ^= hash >>> 15;
        hash *= 0x846ca68b;
        hash ^= hash >>> 16;
        return Math.floorMod(hash, partitionCount);
    }

    private static Path filePath(Path directory, String side, int partitionIndex) {
        return directory.resolve(side + "-" + partitionIndex + ".bin");
    }

    private static ObjectInputStream openInput(Path file) throws IOException {
        return new ObjectInputStream(new BufferedInputStream(Files.newInputStream(file)));
    }

    private static void writeRow(ObjectOutputStream output, Row row) throws IOException {
        output.writeInt(row.size());
        for (Value value : row.getValues()) {
            output.writeInt(value.getType().ordinal());
            output.writeObject(value.getValue());
        }
        // reset stops the stream from keeping every written object in memory.
        output.reset();
    }

    private static Row readRow(ObjectInputStream input) throws IOException, ClassNotFoundException {
        int columnCount;
        try {
            columnCount = input.readInt();
        } catch (EOFException end) {
            return null;
        }
        List<Value> values = new ArrayList<>(columnCount);
        for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
            DataType type = DataType.values()[input.readInt()];
            values.add(new Value(type, input.readObject()));
        }
        return new Row(values);
    }

    private static class PartitionWriters implements AutoCloseable {
        private final List<ObjectOutputStream> streams = new ArrayList<>();

        @Override
        public void close() throws IOException {
            IOException failure = null;
            for (ObjectOutputStream stream : streams) {
                try {
                    stream.close();
                } catch (IOException exception) {
                    // keep closing the remaining files if one close fails.
                    if (failure == null) {
                        failure = exception;
                    } else {
                        failure.addSuppressed(exception);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    private class TemporaryPartitions implements AutoCloseable {
        private final Path directory;

        TemporaryPartitions() throws IOException {
            if (tempDirectory == null) {
                directory = Files.createTempDirectory("grace-join-");
            } else {
                directory = Files.createTempDirectory(tempDirectory, "grace-join-");
            }
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            try (var files = Files.list(directory)) {
                for (Path file : files.toList()) {
                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException exception) {
                        if (failure == null) {
                            failure = exception;
                        } else {
                            failure.addSuppressed(exception);
                        }
                    }
                }
            }
            try {
                Files.deleteIfExists(directory);
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }
}
