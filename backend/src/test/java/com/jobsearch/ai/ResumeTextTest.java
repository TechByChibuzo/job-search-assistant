package com.jobsearch.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.time.Instant;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import com.jobsearch.config.AppProperties;

class ResumeTextTest {

    @Test
    void readsTheTextOfAPdfResume() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PDDocument pdf = new PDDocument()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            try (PDPageContentStream text = new PDPageContentStream(pdf, page)) {
                text.beginText();
                text.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                text.newLineAtOffset(72, 700);
                text.showText("Cut query latency by 40% by adding PostgreSQL indexes");
                text.endText();
            }
            pdf.save(bytes);
        }
        var store = new ResumeStore(new AppProperties(null, null, null, null, null, null, new AppProperties.Ai("", "m", 30, ".", 150)));
        Resume resume = new Resume("r.pdf", Instant.now(), bytes.toByteArray(), null);

        assertThat(store.text(resume)).contains("Cut query latency by 40%");
        assertThat(new SourceCheck(store.text(resume)).quoteFound("Cut query latency by 40% by adding PostgreSQL indexes")).isTrue();
    }
}
