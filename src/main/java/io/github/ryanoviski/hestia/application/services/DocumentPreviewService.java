package io.github.ryanoviski.hestia.application.services;

import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;import org.apache.pdfbox.Loader;import org.apache.pdfbox.rendering.PDFRenderer;
import javax.imageio.ImageIO;import java.awt.image.BufferedImage;import java.io.IOException;import java.nio.file.Path;

public final class DocumentPreviewService {
 public int pdfPageCount(Path file){try(var document=Loader.loadPDF(file.toFile())){return document.getNumberOfPages();}catch(IOException e){throw new ValidationException("O PDF não pôde ser aberto.");}}
 public BufferedImage renderPdf(Path file,int page,float dpi){try(var document=Loader.loadPDF(file.toFile())){if(page<0||page>=document.getNumberOfPages())throw new ValidationException("Página inválida.");return new PDFRenderer(document).renderImageWithDPI(page,dpi);}catch(IOException e){throw new ValidationException("Não foi possível renderizar o PDF.");}}
 public BufferedImage loadImage(Path file,int maxWidth,int maxHeight){try{BufferedImage image=ImageIO.read(file.toFile());if(image==null)throw new ValidationException("A imagem é inválida.");double scale=Math.min(1d,Math.min((double)maxWidth/image.getWidth(),(double)maxHeight/image.getHeight()));if(scale==1d)return image;BufferedImage resized=new BufferedImage(Math.max(1,(int)(image.getWidth()*scale)),Math.max(1,(int)(image.getHeight()*scale)),BufferedImage.TYPE_INT_ARGB);var g=resized.createGraphics();try{g.drawImage(image,0,0,resized.getWidth(),resized.getHeight(),null);}finally{g.dispose();}return resized;}catch(IOException e){throw new ValidationException("Não foi possível carregar a imagem.");}}
}
