package io.github.ryanoviski.hestia.infrastructure.filesystem;

import io.github.ryanoviski.hestia.application.dto.ValidatedFile;
import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;
import java.io.*;import java.nio.file.*;import java.security.*;import java.util.HexFormat;import java.util.Locale;

public final class FileValidationService {
 public static final long DEFAULT_MAX_SIZE=20L*1024*1024;private final long maxSize;
 public FileValidationService(){this(Long.getLong("hestia.attachment.max.bytes",DEFAULT_MAX_SIZE));}
 public FileValidationService(long maxSize){this.maxSize=maxSize;}
 public ValidatedFile validate(Path file){try{if(file==null||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(file))throw new ValidationException("Selecione um arquivo comum e acessível.");long size=Files.size(file);if(size==0)throw new ValidationException("O arquivo está vazio.");if(size>maxSize)throw new ValidationException("O arquivo excede o limite de "+(maxSize/1024/1024)+" MB.");String extension=extension(file.getFileName().toString());byte[] header=new byte[8];try(var in=Files.newInputStream(file)){if(in.read(header)<3)throw new ValidationException("O conteúdo do arquivo é inválido.");}String media=switch(extension){case "pdf"->{if(!(header[0]=='%'&&header[1]=='P'&&header[2]=='D'&&header[3]=='F'&&header[4]=='-'))throw mismatch();yield "application/pdf";}case "png"->{byte[] sig={(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a};if(!java.util.Arrays.equals(header,sig))throw mismatch();yield "image/png";}case "jpg","jpeg"->{if(!((header[0]&255)==0xff&&(header[1]&255)==0xd8&&(header[2]&255)==0xff))throw mismatch();yield "image/jpeg";}default->throw new ValidationException("Formato não permitido. Use PDF, PNG, JPG ou JPEG.");};return new ValidatedFile(extension,media,size,sha256(file));}catch(IOException e){throw new ValidationException("Não foi possível ler o arquivo selecionado.");}}
 public String sha256(Path file)throws IOException{try{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(file);var din=new DigestInputStream(in,digest)){din.transferTo(OutputStream.nullOutputStream());}return HexFormat.of().formatHex(digest.digest());}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
 private String extension(String name){int dot=name.lastIndexOf('.');return dot<0?"":name.substring(dot+1).toLowerCase(Locale.ROOT);}private ValidationException mismatch(){return new ValidationException("A extensão não corresponde ao conteúdo real do arquivo.");}
}
