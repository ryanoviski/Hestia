package io.github.ryanoviski.hestia.infrastructure.filesystem;

import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;import java.nio.file.*;import static org.assertj.core.api.Assertions.*;

class SafeAttachmentStorageTest {
 @TempDir Path directory;
 @Test void resolvesOnlyRelativePathsInsideStorage(){var storage=new SafeAttachmentStorage(directory.resolve("attachments"),directory.resolve("temp"));assertThat(storage.resolve("household-1/2026/01/file.pdf").toString()).startsWith(directory.resolve("attachments").toAbsolutePath().normalize().toString());assertThatThrownBy(()->storage.resolve("../outside.pdf")).isInstanceOf(ValidationException.class);assertThatThrownBy(()->storage.resolve(directory.resolve("absolute.pdf").toString())).isInstanceOf(ValidationException.class);}
 @Test void physicalNameDoesNotUseOriginalFilename(){var storage=new SafeAttachmentStorage(directory.resolve("attachments"),directory.resolve("temp"));String key=storage.newStorageKey(7,"pdf",java.time.Clock.systemUTC());assertThat(key).startsWith("household-7/").endsWith(".pdf").doesNotContain("holerite");}
}
