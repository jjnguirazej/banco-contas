package mz.contas.account;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.HeaderFooter;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import mz.contas.common.Money;
import mz.contas.domain.Account;
import mz.contas.domain.AccountType;
import mz.contas.domain.Movement;
import mz.contas.domain.MovementType;
import mz.contas.domain.Transfer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Gera o extracto de um período em PDF (A4) com a biblioteca OpenPDF. */
@Service
public class StatementPdfService {

    private static final Color INK = new Color(0x17, 0x29, 0x2A);
    private static final Color BRAND = new Color(0xC8, 0x10, 0x2E);
    private static final Color MUTED = new Color(0x58, 0x6A, 0x67);
    private static final Color SOFT = new Color(0xDC, 0xEB, 0xE5);
    private static final Color LINE = new Color(0xC9, 0xD3, 0xCF);
    private static final Color DEBIT = new Color(0xA0, 0x3C, 0x2C);
    private static final Color CREDIT = new Color(0x1D, 0x6D, 0x44);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ZoneId zone;

    public StatementPdfService(@Value("${app.timezone}") String timezone) {
        this.zone = ZoneId.of(timezone);
    }

    /**
     * @param openingBalance saldo da conta no início do período
     * @param movements      movimentos do período, do mais antigo para o mais recente
     */
    public byte[] render(Account account, LocalDate from, LocalDate to, BigDecimal openingBalance,
                         List<Movement> movements) {
        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (Movement m : movements) {
            if (m.getType().getNature() == MovementType.Nature.DEBITO) {
                debits = debits.add(m.getAmount());
            } else {
                credits = credits.add(m.getAmount());
            }
        }
        BigDecimal closing = movements.isEmpty()
                ? openingBalance
                : movements.get(movements.size() - 1).getBalanceAfter();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 44, 50);
        PdfWriter.getInstance(doc, out);

        // O rodapé tem de ser definido antes de abrir o documento.
        HeaderFooter footer = new HeaderFooter(
                new Phrase("Extracto da conta " + account.getAccountNumber() + "  ·  página ", font(8, Font.NORMAL, MUTED)),
                true);
        footer.setBorder(Rectangle.NO_BORDER);
        footer.setAlignment(Element.ALIGN_RIGHT);
        doc.setFooter(footer);

        doc.open();
        doc.add(new Paragraph("Contas", font(11, Font.BOLD, BRAND)));
        Paragraph title = new Paragraph("Extracto de conta", font(20, Font.BOLD, INK));
        title.setSpacingAfter(10);
        doc.add(title);

        PdfPTable info = new PdfPTable(new float[] {1.3f, 3f, 1.3f, 3f});
        info.setWidthPercentage(100);
        infoRow(info, "Titular", account.getCustomer().getFullName(), "NUIT", account.getCustomer().getNuit());
        infoRow(info, "Conta", account.getAccountNumber(), "Tipo",
                account.getType() == AccountType.ORDEM ? "À ordem" : "Poupança");
        infoRow(info, "Período", period(from, to), "Emitido em", DATE_TIME.format(Instant.now().atZone(zone)));
        doc.add(info);

        PdfPTable summary = new PdfPTable(4);
        summary.setWidthPercentage(100);
        summary.setSpacingBefore(14);
        for (String h : new String[] {"Saldo inicial", "Total de débitos", "Total de créditos", "Saldo final"}) {
            summary.addCell(cell(h, font(8, Font.BOLD, MUTED), Element.ALIGN_LEFT, SOFT));
        }
        summary.addCell(cell(Money.format(openingBalance) + " MZN", font(11, Font.BOLD, INK), Element.ALIGN_LEFT, null));
        summary.addCell(cell(Money.format(debits) + " MZN", font(11, Font.BOLD, DEBIT), Element.ALIGN_LEFT, null));
        summary.addCell(cell(Money.format(credits) + " MZN", font(11, Font.BOLD, CREDIT), Element.ALIGN_LEFT, null));
        summary.addCell(cell(Money.format(closing) + " MZN", font(11, Font.BOLD, INK), Element.ALIGN_LEFT, null));
        doc.add(summary);

        PdfPTable table = new PdfPTable(new float[] {1.55f, 4.2f, 1.45f, 1.45f, 1.6f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(16);
        table.setHeaderRows(1); // o cabeçalho repete-se em cada página
        Font head = font(8, Font.BOLD, MUTED);
        table.addCell(cell("Data e hora", head, Element.ALIGN_LEFT, SOFT));
        table.addCell(cell("Descrição", head, Element.ALIGN_LEFT, SOFT));
        table.addCell(cell("Débito", head, Element.ALIGN_RIGHT, SOFT));
        table.addCell(cell("Crédito", head, Element.ALIGN_RIGHT, SOFT));
        table.addCell(cell("Saldo", head, Element.ALIGN_RIGHT, SOFT));

        if (movements.isEmpty()) {
            PdfPCell empty = cell("Sem movimentos neste período.", font(9, Font.NORMAL, MUTED), Element.ALIGN_LEFT, null);
            empty.setColspan(5);
            table.addCell(empty);
        }
        Font body = font(8.5f, Font.NORMAL, INK);
        for (Movement m : movements) {
            boolean debit = m.getType().getNature() == MovementType.Nature.DEBITO;
            table.addCell(cell(DATE_TIME.format(m.getCreatedAt().atZone(zone)), body, Element.ALIGN_LEFT, null));
            table.addCell(cell(description(m), body, Element.ALIGN_LEFT, null));
            table.addCell(cell(debit ? Money.format(m.getAmount()) : "", font(8.5f, Font.NORMAL, DEBIT), Element.ALIGN_RIGHT, null));
            table.addCell(cell(debit ? "" : Money.format(m.getAmount()), font(8.5f, Font.NORMAL, CREDIT), Element.ALIGN_RIGHT, null));
            table.addCell(cell(Money.format(m.getBalanceAfter()), body, Element.ALIGN_RIGHT, null));
        }
        doc.add(table);

        Paragraph note = new Paragraph("Valores em meticais (MZN). Datas e horas no fuso de Moçambique. "
                + "Documento gerado automaticamente a partir do livro-razão da conta.", font(7.5f, Font.NORMAL, MUTED));
        note.setSpacingBefore(12);
        doc.add(note);
        doc.close();
        return out.toByteArray();
    }

    private String period(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return "Todo o histórico até " + DATE.format(LocalDate.now(zone));
        }
        String start = from == null ? "início" : DATE.format(from);
        String end = to == null ? DATE.format(LocalDate.now(zone)) : DATE.format(to);
        return start + " a " + end;
    }

    private static String description(Movement m) {
        Transfer t = m.getTransfer();
        if (t == null) {
            return m.getDescription();
        }
        boolean debit = m.getType() == MovementType.TRANSFERENCIA_DEBITO;
        String other = debit ? t.getTargetAccount().getAccountNumber() : t.getSourceAccount().getAccountNumber();
        return m.getDescription() + (debit ? "  (para " : "  (de ") + other + ")";
    }

    private static void infoRow(PdfPTable t, String k1, String v1, String k2, String v2) {
        t.addCell(cell(k1, font(8, Font.NORMAL, MUTED), Element.ALIGN_LEFT, null, false));
        t.addCell(cell(v1, font(9.5f, Font.BOLD, INK), Element.ALIGN_LEFT, null, false));
        t.addCell(cell(k2, font(8, Font.NORMAL, MUTED), Element.ALIGN_LEFT, null, false));
        t.addCell(cell(v2, font(9.5f, Font.BOLD, INK), Element.ALIGN_LEFT, null, false));
    }

    private static PdfPCell cell(String text, Font font, int align, Color background) {
        return cell(text, font, align, background, true);
    }

    private static PdfPCell cell(String text, Font font, int align, Color background, boolean bordered) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setHorizontalAlignment(align);
        c.setPadding(5);
        c.setBorder(bordered ? Rectangle.BOTTOM : Rectangle.NO_BORDER);
        c.setBorderColor(LINE);
        c.setBorderWidth(0.6f);
        if (background != null) {
            c.setBackgroundColor(background);
        }
        return c;
    }

    private static Font font(float size, int style, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, size, style, color);
    }
}
