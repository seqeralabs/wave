package io.seqera.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.NoSuchAlgorithmException;
import java.util.TreeSet;

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 **/
public class LayerAssembler {

    public final static String SOURCE_DIR = ".layer";

    public final static String LAYER_ROOT = "pack";
    public final static String LAYER_DIR = "layers";
    public final static String LAYER_TAR = "layer.tar";
    public final static String LAYER_GZIP = "layer.tar.gzip";
    public final static String LAYER_JSON = "layer.json";

    final String sourceDir;
    final String destinationDir;

    private LayerAssembler(String source, String destination){
        this.sourceDir = source;
        this.destinationDir = destination;
    }

    public static LayerAssembler newInstance(){
        return newInstance(SOURCE_DIR, LAYER_ROOT);
    }

    public static LayerAssembler newInstance(String source){
        return newInstance(source, LAYER_ROOT);
    }

    public static LayerAssembler newInstance(String source, String destination){
        return new LayerAssembler(source, destination);
    }

    public File buildLayer() throws IOException, NoSuchAlgorithmException {
        createDestination();
        chownSource();
        tarSource();
        gzipTar();
        return generateJson();
    }

    protected void createDestination() throws IOException {
        Files.deleteIfExists(Paths.get(destinationDir, LAYER_DIR));
        Files.createDirectories(Paths.get(destinationDir, LAYER_DIR));
    }

    protected void chownSource() throws IOException {
        Files.find(Paths.get(sourceDir),
                        Integer.MAX_VALUE,
                        (filePath, fileAttr) -> fileAttr.isRegularFile())
                .map( p -> p.toFile())
                .forEach( f ->{
                    f.setExecutable(true);
                    f.setReadable(true);
                    f.setWritable(true);
                });
    }

    protected File tarSource() throws IOException {
        Path source = Paths.get(sourceDir);
        Path output = Paths.get(destinationDir, LAYER_DIR, LAYER_TAR);

        try (OutputStream fOut = Files.newOutputStream(output);
             BufferedOutputStream buffOut = new BufferedOutputStream(fOut);
             TarArchiveOutputStream tOut = new TarArchiveOutputStream(buffOut)) {

            TreeSet<Path> paths = new TreeSet<>();
            Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if( dir != source)
                        paths.add(dir);
                    return super.preVisitDirectory(source, attrs);
                }
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    paths.add(file);
                    return super.visitFile(source, attrs);
                }
            });
            for (Path file : paths) {
                Path targetFile = source.relativize(file);
                TarArchiveEntry tarEntry = new TarArchiveEntry(file.toFile(), targetFile.toString());
                tarEntry.setIds(0,0);
                tarEntry.setGroupName("root");
                tarEntry.setUserName("root");
                tarEntry.setMode( file.toFile().isDirectory() ? 040777 : 0100777); // Apache magic numbers
                tOut.putArchiveEntry(tarEntry);
                if( !file.toFile().isDirectory()) {
                    Files.copy(file, tOut);
                }
                tOut.closeArchiveEntry();
            }
            tOut.finish();
        }
        return output.toFile();
    }


    protected void gzipTar() throws IOException {
        Path source = Paths.get(destinationDir, LAYER_DIR, LAYER_TAR);
        Path output = Paths.get(destinationDir, LAYER_DIR, LAYER_GZIP);

        try (OutputStream fOut = Files.newOutputStream(output);
             GzipCompressorOutputStream gzOut = new GzipCompressorOutputStream(fOut)) {
            Files.copy(source, gzOut);
            gzOut.flush();
            gzOut.finish();
        }
    }

    protected File generateJson() throws IOException, NoSuchAlgorithmException {

        LayerInfo layerInfo = new LayerInfo();
        layerInfo.location = Paths.get(destinationDir, LAYER_DIR, LAYER_GZIP).toString();

        Path gzip = Paths.get(layerInfo.location);
        layerInfo.gzipDigest = DigestFunctions.digest(gzip);
        layerInfo.gzipSize = Files.size(gzip);

        Path tar = Paths.get(destinationDir, LAYER_DIR, LAYER_TAR);
        layerInfo.tarDigest = DigestFunctions.digest(tar);

        ObjectMapper mapper = new ObjectMapper();
        String layerInfojson = mapper.writeValueAsString(layerInfo);

        String json = new String(this.getClass().getResourceAsStream("/layer.json").readAllBytes());
        json = json.replace("${APPEND}", layerInfojson);

        Files.writeString(
                Paths.get(destinationDir, LAYER_DIR, "layer.json"),
                json,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        return Paths.get(destinationDir, LAYER_DIR, "layer.json").toFile();
    }

    static class LayerInfo{
        String location;
        String gzipDigest;
        long gzipSize;
        String tarDigest;

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public String getGzipDigest() {
            return gzipDigest;
        }

        public void setGzipDigest(String gzipDigest) {
            this.gzipDigest = gzipDigest;
        }

        public long getGzipSize() {
            return gzipSize;
        }

        public void setGzipSize(long gzipSize) {
            this.gzipSize = gzipSize;
        }

        public String getTarDigest() {
            return tarDigest;
        }

        public void setTarDigest(String tarDigest) {
            this.tarDigest = tarDigest;
        }
    }
}
