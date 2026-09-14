package com.attendance.course.imports;

import java.util.List;
import java.util.Map;

record ImportTable(List<TableRow> rows, List<Merge> merges) {
    record Value(String text, boolean invalid) {
        static final Value EMPTY = new Value("", false);
    }

    record TableRow(int number, Map<Integer, Value> cells) {
        Value raw(int column) {
            return cells.getOrDefault(column, Value.EMPTY);
        }
    }

    record Merge(int firstRow, int lastRow, int firstColumn, int lastColumn) {
    }

    Value cell(int row, int column) {
        for (Merge merge : merges) {
            if (row >= merge.firstRow && row <= merge.lastRow
                    && column >= merge.firstColumn && column <= merge.lastColumn) {
                row = merge.firstRow;
                column = merge.firstColumn;
                break;
            }
        }
        if (row < 0 || row >= rows.size()) {
            return Value.EMPTY;
        }
        return rows.get(row).raw(column);
    }
}
