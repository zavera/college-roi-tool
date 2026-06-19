package com.example.collegeroitool.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * Generates a demo financial aid document PDF from KV extract data.
 * Used in place of Azure Blob Storage in demo/prod mode.
 */
@Service
public class DemoPdfGenerator {

    private static final Color NAVY      = new Color(45, 48, 64);
    private static final Color GOLD      = new Color(201, 168, 76);
    private static final Color LIGHT_GRAY = new Color(245, 245, 247);
    private static final Color MID_GRAY  = new Color(120, 120, 130);

    public byte[] generate(String studentName, String documentFilename,
                            Map<String, Object> kvData) throws Exception {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.LETTER, 60, 60, 60, 60);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        doc.open();

        // ── Watermark ─────────────────────────────────────────────────────
        PdfContentByte canvas = writer.getDirectContentUnder();
        canvas.saveState();
        canvas.setColorFill(new Color(230, 230, 235));
        canvas.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA_BOLD,
                BaseFont.WINANSI, false), 72);
        canvas.showTextAligned(PdfContentByte.ALIGN_CENTER, "DEMO",
                297, 380, 45);
        canvas.restoreState();

        // ── Header bar ────────────────────────────────────────────────────
        PdfContentByte cb = writer.getDirectContent();
        cb.saveState();
        cb.setColorFill(NAVY);
        cb.rectangle(60, 745, 492, 32);
        cb.fill();
        cb.restoreState();

        Font headerFont = new Font(Font.HELVETICA, 11, Font.BOLD,
                new Color(212, 180, 156));
        Paragraph headerTitle = new Paragraph(
                "ASTRA  ·  CALLISTO TECH  ·  DEMO DOCUMENT", headerFont);
        headerTitle.setAlignment(Element.ALIGN_CENTER);
        headerTitle.setSpacingBefore(6);
        doc.add(headerTitle);

        // ── Title block ───────────────────────────────────────────────────
        Font titleFont  = new Font(Font.HELVETICA, 16, Font.BOLD, NAVY);
        Font subFont    = new Font(Font.HELVETICA, 10, Font.NORMAL, MID_GRAY);
        Font labelFont  = new Font(Font.HELVETICA, 9, Font.BOLD, NAVY);
        Font valueFont  = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(40, 40, 50));
        Font warnFont   = new Font(Font.HELVETICA, 8, Font.BOLD, new Color(180, 80, 0));

        addSpacer(doc, 10);
        doc.add(new Paragraph("IRS Form 1040 — U.S. Individual Income Tax Return", titleFont));
        addSpacer(doc, 2);
        doc.add(new Paragraph("Tax Year 2023  ·  " + documentFilename, subFont));
        addSpacer(doc, 4);

        // Warning banner
        PdfPTable banner = new PdfPTable(1);
        banner.setWidthPercentage(100);
        PdfPCell bannerCell = new PdfPCell(
                new Phrase("FOR DEMONSTRATION PURPOSES ONLY — NOT A REAL TAX DOCUMENT", warnFont));
        bannerCell.setBackgroundColor(new Color(255, 247, 230));
        bannerCell.setBorderColor(new Color(249, 115, 22));
        bannerCell.setPadding(6);
        bannerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        banner.addCell(bannerCell);
        doc.add(banner);
        addSpacer(doc, 12);

        // Student info row
        doc.add(new Paragraph("Taxpayer / Student Information", labelFont));
        addSpacer(doc, 4);
        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setWidths(new float[]{1, 1});
        addInfoRow(infoTable, "Student Name", studentName, labelFont, valueFont);
        addInfoRow(infoTable, "Document File", documentFilename, labelFont, valueFont);
        doc.add(infoTable);
        addSpacer(doc, 14);

        // ── KV data table ─────────────────────────────────────────────────
        doc.add(new Paragraph("Extracted Financial Data", labelFont));
        addSpacer(doc, 4);

        PdfPTable kvTable = new PdfPTable(2);
        kvTable.setWidthPercentage(100);
        kvTable.setWidths(new float[]{1.6f, 1f});

        // Table header
        PdfPCell hdrKey = headerCell("Field", labelFont, NAVY);
        PdfPCell hdrVal = headerCell("Value", labelFont, NAVY);
        kvTable.addCell(hdrKey);
        kvTable.addCell(hdrVal);

        boolean alt = false;
        for (Map.Entry<String, Object> entry : kvData.entrySet()) {
            Color bg = alt ? LIGHT_GRAY : Color.WHITE;
            PdfPCell keyCell = dataCell(entry.getKey(), valueFont, bg, Element.ALIGN_LEFT);
            PdfPCell valCell = dataCell(String.valueOf(entry.getValue()), valueFont, bg, Element.ALIGN_RIGHT);
            kvTable.addCell(keyCell);
            kvTable.addCell(valCell);
            alt = !alt;
        }
        doc.add(kvTable);

        // ── Footer ────────────────────────────────────────────────────────
        addSpacer(doc, 20);
        Font footerFont = new Font(Font.HELVETICA, 7, Font.ITALIC, MID_GRAY);
        Paragraph footer = new Paragraph(
                "This document was generated by Astra, an AI financial aid assistant by Callisto Consulting Group LLC DBA Callisto Tech. " +
                "It is a demonstration document containing synthetic data and does not represent a real IRS tax filing. " +
                "For actual financial aid purposes, use official IRS documents.", footerFont);
        footer.setAlignment(Element.ALIGN_CENTER);
        doc.add(footer);

        doc.close();
        return out.toByteArray();
    }

    private void addSpacer(Document doc, float height) throws DocumentException {
        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingAfter(height);
        doc.add(spacer);
    }

    private PdfPCell headerCell(String text, Font font, Color bg) {
        Font f = new Font(font);
        f.setColor(Color.WHITE);
        PdfPCell cell = new PdfPCell(new Phrase(text, f));
        cell.setBackgroundColor(bg);
        cell.setPadding(6);
        cell.setBorder(Rectangle.NO_BORDER);
        return cell;
    }

    private PdfPCell dataCell(String text, Font font, Color bg, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setPadding(5);
        cell.setBorderColor(new Color(220, 220, 225));
        cell.setBorderWidth(0.5f);
        cell.setHorizontalAlignment(align);
        return cell;
    }

    private void addInfoRow(PdfPTable table, String label, String value,
                             Font labelFont, Font valueFont) {
        PdfPCell lCell = new PdfPCell(new Phrase(label, labelFont));
        lCell.setBorder(Rectangle.BOTTOM);
        lCell.setBorderColor(new Color(220, 220, 225));
        lCell.setPadding(4);
        PdfPCell vCell = new PdfPCell(new Phrase(value, valueFont));
        vCell.setBorder(Rectangle.BOTTOM);
        vCell.setBorderColor(new Color(220, 220, 225));
        vCell.setPadding(4);
        table.addCell(lCell);
        table.addCell(vCell);
    }
}
