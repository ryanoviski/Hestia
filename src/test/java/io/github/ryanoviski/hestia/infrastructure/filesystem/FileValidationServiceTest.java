package io.github.ryanoviski.hestia.infrastructure.filesystem;

import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;import org.apache.pdfbox.pdmodel.*;import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;import javax.imageio.ImageIO;import java.awt.image.BufferedImage;import java.nio.file.*;import static org.assertj.core.api.Assertions.*;

class FileValidationServiceTest {
 @TempDir Path directory;private final FileValidationService service=new FileValidationService(1024*1024);
 @Test void acceptsRealPdf()throws Exception{Path p=pdf("holerite.pdf");assertThat(service.validate(p).mediaType()).isEqualTo("application/pdf");}
 @Test void acceptsRealPng()throws Exception{assertThat(service.validate(image("foto.png","png")).mediaType()).isEqualTo("image/png");}
 @Test void acceptsRealJpegWithEitherExtension()throws Exception{assertThat(service.validate(image("comprovante.jpeg","jpg")).mediaType()).isEqualTo("image/jpeg");assertThat(service.validate(image("outro.jpg","jpg")).extension()).isEqualTo("jpg");}
 @Test void rejectsFakeExtensionAndInvalidContent()throws Exception{Path p=directory.resolve("falso.pdf");Files.writeString(p,"não é pdf");assertThatThrownBy(()->service.validate(p)).isInstanceOf(ValidationException.class).hasMessageContaining("extensão");}
 @Test void rejectsEmptyAndOversizedFiles()throws Exception{Path empty=directory.resolve("empty.png");Files.createFile(empty);assertThatThrownBy(()->service.validate(empty)).isInstanceOf(ValidationException.class).hasMessageContaining("vazio");Path large=directory.resolve("large.png");Files.write(large,new byte[1024*1024+1]);assertThatThrownBy(()->service.validate(large)).isInstanceOf(ValidationException.class).hasMessageContaining("limite");}
 @Test void calculatesStableSha256AndAcceptsSpecialNames()throws Exception{Path file=image("comprovante ç 01.png","png");var first=service.validate(file);var second=service.validate(file);assertThat(first.sha256()).hasSize(64).isEqualTo(second.sha256());}
 private Path pdf(String name)throws Exception{Path p=directory.resolve(name);try(PDDocument d=new PDDocument()){d.addPage(new PDPage());d.save(p.toFile());}return p;}private Path image(String name,String format)throws Exception{Path p=directory.resolve(name);ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),format,p.toFile());return p;}
}
