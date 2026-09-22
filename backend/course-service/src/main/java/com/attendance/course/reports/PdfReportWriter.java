package com.attendance.course.reports;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

final class PdfReportWriter {
    private static final float LEFT = 40;
    private final PDDocument document;
    private final PDType0Font font;
    private final PDRectangle pageSize;
    private final String title;
    private final float[] columns;
    private PDPageContentStream content;
    private float y;
    private int pageNumber;
    private String[] headers;

    PdfReportWriter(PDDocument document, PDType0Font font, PDRectangle pageSize,
            String title, float[] columns) {
        this.document = document;
        this.font = font;
        this.pageSize = pageSize;
        this.title = title;
        this.columns = columns.clone();
    }

    void start() throws IOException {
        newPage();
    }

    private void newPage() throws IOException {
        if (content != null) closePage();
        PDPage page = new PDPage(pageSize);
        document.addPage(page);
        pageNumber++;
        content = new PDPageContentStream(document, page);
        y = pageSize.getHeight() - 48;
        text(LEFT, y, pageNumber == 1 ? title : title + " - nastavak", 16);
        y -= 30;
    }

    void detail(String label, String value) throws IOException {
        List<String> lines = wrap(value, pageSize.getWidth() - 197, 9);
        ensureSpace(lines.size() * 14 + 2);
        text(LEFT, y, label + ":", 9);
        for (String line : lines) {
            text(157, y, line, 9);
            y -= 14;
        }
        y -= 2;
    }

    void headers(String... labels) throws IOException {
        if (labels.length != columns.length) throw new IllegalArgumentException("Broj kolona nije ispravan.");
        headers = labels.clone();
        tableHeader();
    }

    private void tableHeader() throws IOException {
        ensureSpace(45);
        y -= 12;
        content.setNonStrokingColor(232f / 255, 239f / 255, 246f / 255);
        content.addRect(LEFT, y - 8, tableWidth(), 22);
        content.fill();
        content.setNonStrokingColor(20f / 255, 35f / 255, 50f / 255);
        float x = LEFT + 2;
        for (int column = 0; column < headers.length; column++) {
            text(x, y, headers[column], 9);
            x += columns[column];
        }
        y -= 20;
    }

    void row(String... values) throws IOException {
        if (values.length != columns.length) throw new IllegalArgumentException("Broj kolona nije ispravan.");
        List<List<String>> cells = new ArrayList<>();
        int maxLines = 1;
        for (int column = 0; column < values.length; column++) {
            List<String> lines = wrap(values[column], columns[column] - 4, 8);
            cells.add(lines);
            maxLines = Math.max(maxLines, lines.size());
        }
        int rowHeight = Math.max(19, maxLines * 11 + 7);
        if (y - rowHeight < 55) {
            newPage();
            tableHeader();
        }
        float x = LEFT + 2;
        for (int column = 0; column < cells.size(); column++) {
            float lineY = y - 10;
            for (String line : cells.get(column)) {
                text(x, lineY, line, 8);
                lineY -= 11;
            }
            x += columns[column];
        }
        content.setStrokingColor(218f / 255, 226f / 255, 234f / 255);
        content.moveTo(LEFT, y - rowHeight + 1);
        content.lineTo(LEFT + tableWidth(), y - rowHeight + 1);
        content.stroke();
        y -= rowHeight;
    }

    private float tableWidth() {
        float width = 0;
        for (float column : columns) width += column;
        return width;
    }

    private List<String> wrap(String value, float width, int size) throws IOException {
        if (value == null || value.isBlank()) return List.of("-");
        value = value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').strip();
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String next = new String(Character.toChars(codePoint));
            if (!line.isEmpty() && font.getStringWidth(line + next) * size / 1000 > width) {
                lines.add(line.toString().stripTrailing());
                line.setLength(0);
            }
            line.append(next);
            offset += Character.charCount(codePoint);
        }
        if (!line.isEmpty()) lines.add(line.toString().stripTrailing());
        return lines;
    }

    private void ensureSpace(float height) throws IOException {
        if (y - height < 55) newPage();
    }

    private void text(float x, float baseline, String value, int size) throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, baseline);
        content.showText(value);
        content.endText();
    }

    private void closePage() throws IOException {
        text(pageSize.getWidth() - 95, 30, "Strana " + pageNumber, 8);
        content.close();
    }

    void finish() throws IOException {
        closePage();
    }
}
