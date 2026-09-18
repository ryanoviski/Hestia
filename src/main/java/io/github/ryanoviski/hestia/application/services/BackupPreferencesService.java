package io.github.ryanoviski.hestia.application.services;

import java.io.*;import java.nio.file.*;import java.time.Instant;import java.util.Properties;

public final class BackupPreferencesService {
 public record Settings(boolean enabled,String directory,int retention,Instant lastBackup,String lastResult){}
 private final Path file;public BackupPreferencesService(Path dataDirectory){file=dataDirectory.resolve("backup.properties");}
 public Settings load(){Properties p=new Properties();if(Files.exists(file))try(var in=Files.newInputStream(file)){p.load(in);}catch(IOException ignored){}return new Settings(Boolean.parseBoolean(p.getProperty("enabled","false")),p.getProperty("directory",""),Integer.parseInt(p.getProperty("retention","10")),instant(p.getProperty("lastBackup")),p.getProperty("lastResult","Nunca executado"));}
 public void save(Settings s){Properties p=new Properties();p.setProperty("enabled",Boolean.toString(s.enabled()));p.setProperty("directory",s.directory()==null?"":s.directory());p.setProperty("retention",Integer.toString(Math.max(1,s.retention())));if(s.lastBackup()!=null)p.setProperty("lastBackup",s.lastBackup().toString());p.setProperty("lastResult",s.lastResult()==null?"":s.lastResult());try{Files.createDirectories(file.getParent());try(var out=Files.newOutputStream(file)){p.store(out,"Hestia backup settings - no passwords are stored");}}catch(IOException e){throw new IllegalStateException("Could not save backup settings",e);}}
 private Instant instant(String value){try{return value==null?null:Instant.parse(value);}catch(Exception e){return null;}}
 public Path file(){return file;}
}
