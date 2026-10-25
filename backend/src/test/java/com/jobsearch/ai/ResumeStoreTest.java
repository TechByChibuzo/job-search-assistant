package com.jobsearch.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jobsearch.config.AppProperties;

class ResumeStoreTest {

    @TempDir
    Path dir;

    private ResumeStore store() {
        return new ResumeStore(new AppProperties(null, null, null, null, null, null, new AppProperties.Ai("", "m", 30, dir.toString())));
    }

    @Test
    void usesTheNewestPdfOrTextFile() throws Exception {
        Path old = Files.writeString(dir.resolve("resume-2025.md"), "Old resume");
        Files.setLastModifiedTime(old, FileTime.from(Instant.parse("2025-01-01T00:00:00Z")));
        Files.write(dir.resolve("resume-2026.pdf"), "%PDF-1.4".getBytes());
        Files.writeString(dir.resolve(".DS_Store"), "x");

        Resume resume = store().current().orElseThrow();

        assertThat(resume.fileName()).isEqualTo("resume-2026.pdf");
        assertThat(resume.isPdf()).isTrue();
    }

    @Test
    void readsATextResume() throws Exception {
        Files.writeString(dir.resolve("resume.md"), "# Jane Doe");

        assertThat(store().current().orElseThrow().text()).isEqualTo("# Jane Doe");
    }

    @Test
    void explainsWhatIsMissing() throws Exception {
        assertThat(store().current()).isEmpty();
        assertThat(store().describeFolder()).contains("PDF");

        Files.writeString(dir.resolve("resume.docx"), "word");
        assertThat(store().current()).isEmpty();
        assertThat(store().describeFolder()).contains("save it as PDF");
    }
}
