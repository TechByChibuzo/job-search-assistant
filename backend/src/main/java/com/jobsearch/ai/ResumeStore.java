package com.jobsearch.ai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import com.jobsearch.config.AppProperties;

/**
 * Reads the resume from the private folder (git-ignored, mounted read-only in Docker). The newest PDF,
 * Markdown or text file wins, so replacing the resume is just dropping in a new file.
 */
@Component
public class ResumeStore {

    /** The API accepts PDFs up to 32 MB per request; a resume anywhere near that is a mistake. */
    private static final long MAX_BYTES = 5 * 1024 * 1024;

    private final Path dir;
    private volatile Resume cached;
    private volatile String cachedText;
    private volatile Resume textOf;

    ResumeStore(AppProperties properties) {
        this.dir = Path.of(properties.ai().resumeDir());
    }

    public Optional<Resume> current() {
        Optional<Path> newest = newestFile();
        if (newest.isEmpty()) {
            return Optional.empty();
        }
        Path file = newest.get();
        try {
            Instant modified = Files.getLastModifiedTime(file).toInstant();
            Resume c = cached;
            if (c != null && c.fileName().equals(file.getFileName().toString()) && c.modifiedAt().equals(modified)) {
                return Optional.of(c);
            }
            if (Files.size(file) > MAX_BYTES) {
                throw new IllegalStateException(file.getFileName() + " is larger than 5 MB; save a smaller copy of your resume.");
            }
            String name = file.getFileName().toString();
            Resume resume = name.toLowerCase(Locale.ROOT).endsWith(".pdf")
                    ? new Resume(name, modified, Files.readAllBytes(file), null)
                    : new Resume(name, modified, null, Files.readString(file, StandardCharsets.UTF_8));
            cached = resume;
            return Optional.of(resume);
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read the resume " + file.getFileName(), e);
        }
    }

    /**
     * The resume's plain text, to check what Claude writes against it. Empty when the PDF has no text layer
     * (a scan), in which case checks can't be made and the interface says so.
     */
    public String text(Resume resume) {
        if (!resume.isPdf()) {
            return resume.text();
        }
        if (resume == textOf && cachedText != null) {
            return cachedText;
        }
        try (PDDocument pdf = Loader.loadPDF(resume.pdf())) {
            String text = new PDFTextStripper().getText(pdf);
            cachedText = text;
            textOf = resume;
            return text;
        } catch (IOException e) {
            return "";
        }
    }

    /** What the folder holds, to explain a missing resume: "resume.docx" isn't readable, a PDF is. */
    String describeFolder() {
        if (!Files.isDirectory(dir)) {
            return "The resume folder " + dir + " doesn't exist.";
        }
        try (Stream<Path> files = Files.list(dir)) {
            boolean hasWord = files.anyMatch(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".docx"));
            return hasWord
                    ? "Your resume is a Word file; save it as PDF in the same folder."
                    : "Put your resume (PDF, .md or .txt) in the private folder.";
        } catch (IOException e) {
            return "Couldn't read the resume folder.";
        }
    }

    private Optional<Path> newestFile() {
        if (!Files.isDirectory(dir)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return !n.startsWith(".") && (n.endsWith(".pdf") || n.endsWith(".md") || n.endsWith(".txt"));
                    })
                    .max(Comparator.comparing(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toInstant();
                        } catch (IOException e) {
                            return Instant.EPOCH;
                        }
                    }));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
