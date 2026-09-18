package io.github.ryanoviski.hestia.infrastructure.filesystem;

import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;
import java.io.IOException;import java.nio.file.*;import java.time.*;import java.util.UUID;

public final class SafeAttachmentStorage {
 private final Path root,temp;public SafeAttachmentStorage(Path root,Path temp){this.root=root.toAbsolutePath().normalize();this.temp=temp.toAbsolutePath().normalize();}
 public Path createTemporaryCopy(Path source)throws IOException{Files.createDirectories(temp);String name=source.getFileName().toString();int dot=name.lastIndexOf('.');String suffix=dot<0?".tmp":name.substring(dot);Path file=Files.createTempFile(temp,"attachment-",suffix);try{Files.copy(source,file,StandardCopyOption.REPLACE_EXISTING);return file;}catch(IOException e){Files.deleteIfExists(file);throw e;}}
 public String newStorageKey(long household,String extension,Clock clock){LocalDate date=LocalDate.now(clock);return "household-"+household+"/"+date.getYear()+"/"+String.format("%02d",date.getMonthValue())+"/"+UUID.randomUUID()+"."+extension;}
 public Path store(Path temporary,String key)throws IOException{Path destination=resolve(key);Files.createDirectories(destination.getParent());if(Files.exists(destination,LinkOption.NOFOLLOW_LINKS))throw new FileAlreadyExistsException(destination.toString());try{return Files.move(temporary,destination,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){return Files.move(temporary,destination);}}
 public Path resolve(String key){if(key==null||key.isBlank()||Path.of(key).isAbsolute()||key.contains("..")||key.indexOf('\0')>=0)throw unsafe();Path result=root.resolve(key).normalize();if(!result.startsWith(root))throw unsafe();Path cursor=result.getParent();while(cursor!=null&&cursor.startsWith(root)){if(Files.isSymbolicLink(cursor))throw unsafe();cursor=cursor.getParent();}return result;}
 public Path quarantine(Path file)throws IOException{Path safe=resolve(root.relativize(file.toAbsolutePath().normalize()).toString());Files.createDirectories(temp);return Files.move(safe,Files.createTempFile(temp,"quarantine-",".tmp"),StandardCopyOption.REPLACE_EXISTING);}
 public void restoreQuarantine(Path quarantine,String key)throws IOException{Path destination=resolve(key);Files.createDirectories(destination.getParent());Files.move(quarantine,destination,StandardCopyOption.REPLACE_EXISTING);}
 public Path root(){return root;}private ValidationException unsafe(){return new ValidationException("O caminho do anexo não é seguro.");}
}
