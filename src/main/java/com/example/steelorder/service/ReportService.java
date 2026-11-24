package com.example.steelorder.service;

import com.example.steelorder.entity.Order;
import com.example.steelorder.entity.OrderAllocation;
import com.example.steelorder.entity.enums.OrderStatus;
import com.example.steelorder.repository.OrderAllocationRepository;
import com.example.steelorder.repository.OrderRepository;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.opencsv.CSVWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ReportService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private final OrderRepository orderRepository;
    private final OrderAllocationRepository orderAllocationRepository;

    public ReportService(OrderRepository orderRepository,
                         OrderAllocationRepository orderAllocationRepository) {
        this.orderRepository = orderRepository;
        this.orderAllocationRepository = orderAllocationRepository;
    }

    @Transactional(readOnly = true)
    public byte[] generateMillSheetPdf(Long orderId) {
        Order order = findReportTargetOrder(orderId);
        List<OrderAllocation> allocations = orderAllocationRepository.findByOrderOrderByAllocationIdAsc(order);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, outputStream);
            document.open();

            BaseFont baseFont = buildJapaneseBaseFont();
            Font titleFont = new Font(baseFont, 16, Font.BOLD);
            Font headerFont = new Font(baseFont, 11, Font.BOLD);
            Font textFont = new Font(baseFont, 10);
            Font smallFont = new Font(baseFont, 9);

            Paragraph title = new Paragraph("鋼材検査証明書 / MILL SHEET", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(14f);
            document.add(title);

            document.add(buildBasicInfoTable(order, headerFont, textFont));
            document.add(buildProductSpecTable(order, headerFont, textFont));
            document.add(buildTraceabilityTable(allocations, headerFont, textFont));
            document.add(buildQualityTable(order.getSteelSheet().getSteelGrade(), headerFont, smallFont));

            Paragraph footer = new Paragraph("本書はシステム出力のサンプル帳票です。", smallFont);
            footer.setSpacingBefore(10f);
            document.add(footer);

            document.close();
            return outputStream.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("ミルシートPDFの生成に失敗しました", ex);
        }
    }

    @Transactional(readOnly = true)
    public byte[] generateShipmentCsv() {
        List<Order> shippedOrders = orderRepository.findByStatusOrderByShippedAtDesc(OrderStatus.SHIPPED);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
             CSVWriter csvWriter = new CSVWriter(outputStreamWriter)) {

            csvWriter.writeNext(new String[]{
                    "注文番号", "出荷日時", "取引先名", "鋼板コード", "鋼板名",
                    "板厚(mm)", "板幅(mm)", "出荷重量(kg)", "使用コイル番号"
            });

            for (Order order : shippedOrders) {
                List<OrderAllocation> allocations = orderAllocationRepository.findByOrderOrderByAllocationIdAsc(order);
                String coilNumbers = allocations.stream()
                        .map(a -> a.getCoil().getCoilNumber())
                        .distinct()
                        .reduce((a, b) -> a + " / " + b)
                        .orElse("-");

                csvWriter.writeNext(new String[]{
                        order.getOrderNumber(),
                        order.getShippedAt() == null ? "" : DATETIME_FORMAT.format(order.getShippedAt()),
                        order.getCustomerName(),
                        order.getSteelSheet().getSteelCode(),
                        order.getSteelSheet().getSteelName(),
                        order.getSteelSheet().getThickness().toPlainString(),
                        String.valueOf(order.getSteelSheet().getWidth()),
                        String.valueOf(order.getOrderWeight()),
                        coilNumbers
                });
            }

            csvWriter.flush();
            byte[] csvBody = outputStream.toByteArray();
            byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
            byte[] result = new byte[bom.length + csvBody.length];
            System.arraycopy(bom, 0, result, 0, bom.length);
            System.arraycopy(csvBody, 0, result, bom.length, csvBody.length);
            return result;
        } catch (Exception ex) {
            throw new IllegalStateException("出荷実績CSVの生成に失敗しました", ex);
        }
    }

    private Order findReportTargetOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("注文が見つかりません"));

        if (order.getStatus() != OrderStatus.ALLOCATED && order.getStatus() != OrderStatus.SHIPPED) {
            throw new IllegalStateException("ミルシートは出荷待ち/出荷済の注文のみ発行できます");
        }
        return order;
    }

    private PdfPTable buildBasicInfoTable(Order order, Font headerFont, Font textFont) {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setSpacingAfter(10f);
        table.setWidths(new float[]{2.1f, 2.9f, 2.1f, 2.9f});

        addHeaderCell(table, "発行日", headerFont);
        addTextCell(table, DATE_FORMAT.format(LocalDate.now()), textFont);
        addHeaderCell(table, "注文番号", headerFont);
        addTextCell(table, order.getOrderNumber(), textFont);

        addHeaderCell(table, "取引先名", headerFont);
        addTextCell(table, order.getCustomerName(), textFont);
        addHeaderCell(table, "希望納期/出荷日", headerFont);
        LocalDateTime shippedAt = order.getShippedAt();
        String dueOrShip = shippedAt == null
                ? DATE_FORMAT.format(order.getDesiredDeliveryDate())
                : DATETIME_FORMAT.format(shippedAt);
        addTextCell(table, dueOrShip, textFont);

        return table;
    }

    private PdfPTable buildProductSpecTable(Order order, Font headerFont, Font textFont) {
        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);
        table.setSpacingAfter(10f);

        addHeaderCell(table, "鋼板コード", headerFont);
        addHeaderCell(table, "鋼板名", headerFont);
        addHeaderCell(table, "鋼種", headerFont);
        addHeaderCell(table, "用途区分", headerFont);
        addHeaderCell(table, "板厚(mm)", headerFont);
        addHeaderCell(table, "板幅(mm)", headerFont);
        addHeaderCell(table, "総出荷重量(kg)", headerFont);

        addTextCell(table, order.getSteelSheet().getSteelCode(), textFont);
        addTextCell(table, order.getSteelSheet().getSteelName(), textFont);
        addTextCell(table, order.getSteelSheet().getSteelGrade(), textFont);
        addTextCell(table, order.getSteelSheet().getUsageCategory(), textFont);
        addTextCell(table, order.getSteelSheet().getThickness().toPlainString(), textFont);
        addTextCell(table, String.valueOf(order.getSteelSheet().getWidth()), textFont);
        addTextCell(table, String.format("%,d", order.getOrderWeight()), textFont);
        return table;
    }

    private PdfPTable buildTraceabilityTable(List<OrderAllocation> allocations, Font headerFont, Font textFont) {
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setSpacingAfter(10f);

        addHeaderCell(table, "引当コイル番号", headerFont);
        addHeaderCell(table, "入庫日", headerFont);
        addHeaderCell(table, "出荷切り出し重量(kg)", headerFont);

        if (allocations.isEmpty()) {
            PdfPCell cell = new PdfPCell(new Phrase("引当明細がありません", textFont));
            cell.setColspan(3);
            cell.setPadding(6f);
            table.addCell(cell);
            return table;
        }

        for (OrderAllocation allocation : allocations) {
            addTextCell(table, allocation.getCoil().getCoilNumber(), textFont);
            addTextCell(table, DATE_FORMAT.format(allocation.getCoil().getArrivalDate()), textFont);
            addTextCell(table, String.format("%,d", allocation.getAllocatedWeight()), textFont);
        }

        return table;
    }

    private PdfPTable buildQualityTable(String steelGrade, Font headerFont, Font textFont) {
        QualitySample sample = buildQualitySample(steelGrade);

        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);

        PdfPCell titleCell = new PdfPCell(new Phrase("品質検査値（サンプル）", headerFont));
        titleCell.setColspan(7);
        titleCell.setPadding(6f);
        table.addCell(titleCell);

        addHeaderCell(table, "鋼種", headerFont);
        addHeaderCell(table, "C", headerFont);
        addHeaderCell(table, "Si", headerFont);
        addHeaderCell(table, "Mn", headerFont);
        addHeaderCell(table, "P", headerFont);
        addHeaderCell(table, "S", headerFont);
        addHeaderCell(table, "引張強さ / 伸び", headerFont);

        addTextCell(table, sample.steelGrade(), textFont);
        addTextCell(table, sample.carbon(), textFont);
        addTextCell(table, sample.silicon(), textFont);
        addTextCell(table, sample.manganese(), textFont);
        addTextCell(table, sample.phosphorus(), textFont);
        addTextCell(table, sample.sulfur(), textFont);
        addTextCell(table, sample.tensileStrength() + " N/mm2 / " + sample.elongation() + " %", textFont);

        return table;
    }

    private QualitySample buildQualitySample(String steelGrade) {
        if (steelGrade == null) {
            return new QualitySample("UNKNOWN", "0.060", "0.010", "0.300", "0.015", "0.010", "310", "36");
        }

        String grade = steelGrade.toUpperCase();
        if (grade.contains("JAC")) {
            return new QualitySample(steelGrade, "0.070", "0.020", "1.100", "0.018", "0.006", "430", "30");
        }
        if (grade.contains("SGCC") || grade.contains("SECC")) {
            return new QualitySample(steelGrade, "0.050", "0.010", "0.400", "0.018", "0.012", "335", "34");
        }
        return new QualitySample(steelGrade, "0.040", "0.010", "0.280", "0.016", "0.010", "315", "38");
    }

    private BaseFont buildJapaneseBaseFont() throws DocumentException {
        try {
            return BaseFont.createFont("HeiseiKakuGo-W5", "UniJIS-UCS2-H", BaseFont.NOT_EMBEDDED);
        } catch (Exception ex) {
            try {
                return BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            } catch (Exception fallbackEx) {
                throw new DocumentException("日本語フォントの読み込みに失敗しました");
            }
        }
    }

    private void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(6f);
        cell.setBackgroundColor(new Color(236, 242, 247));
        table.addCell(cell);
    }

    private void addTextCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(6f);
        table.addCell(cell);
    }

    private record QualitySample(
            String steelGrade,
            String carbon,
            String silicon,
            String manganese,
            String phosphorus,
            String sulfur,
            String tensileStrength,
            String elongation
    ) {
    }
}
