package com.coherentnetworksolutions.reson8.audio.sound;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import com.coherentnetworksolutions.reson8.audio.sound.WavCache.CachedWav;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;

import io.quarkus.logging.Log;

public class SoundHitBundle {
    
    private WavCache wavCache;
    Reson8Config config;
    
    private final List<String> filePaths;
    private final String directory;
    private final Path resolvedDirectory;
    
    public SoundHitBundle(String directory, Reson8Config config, WavCache wavCache) {
        this.directory = directory;
        this.config = config;
        this.wavCache = wavCache;
        this.resolvedDirectory = resolveDirectoryPath(directory);
        this.filePaths = scanAndCacheDirectory();
        Log.debugf("Initialized SoundHitBundle for directory: %s (resolved to: %s) with %d files", directory, resolvedDirectory, filePaths.size());
    }
    
    private Path resolveDirectoryPath(String directory) {
        if (Paths.get(directory).isAbsolute()) {
            return Paths.get(directory);
        } else {
            return Paths.get(config.audioPath()).resolve(directory);
        }
    }
    
    private List<String> scanAndCacheDirectory() {
        try {
            if (!Files.exists(resolvedDirectory) || !Files.isDirectory(resolvedDirectory)) {
                Log.warnf("Directory does not exist or is not a directory: %s (resolved to: %s)", directory, resolvedDirectory);
                return Collections.emptyList();
            }
            
            // Recursively find all files
            List<String> foundFiles = Files.walk(resolvedDirectory)
                .filter(Files::isRegularFile)
                .map(file -> resolvedDirectory.relativize(file).toString())
                .collect(Collectors.toList());
            
            Log.infof("Found %d files in bundle directory: %s (resolved to: %s)", foundFiles.size(), directory, resolvedDirectory);
            
            // Pre-cache all files in WavCache
            for (String relativePath : foundFiles) {
                String fullPath = resolvedDirectory.resolve(relativePath).toString();
                wavCache.getOrLoad(fullPath);
                Log.debugf("Cached sound file: %s", fullPath);
            }
            
            return foundFiles;
            
        } catch (IOException e) {
            Log.errorf("Failed to scan directory %s: %s", directory, e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    public List<String> getFilePaths() {
        return new ArrayList<>(filePaths);
    }
    
    public int getCount() {
        return filePaths.size();
    }
    
    public boolean isEmpty() {
        return filePaths.isEmpty();
    }
    
    public String getRandomFilePath() {
        if (filePaths.isEmpty()) {
            return null;
        }
        int randomIndex = ThreadLocalRandom.current().nextInt(filePaths.size());
        return resolvedDirectory.resolve(filePaths.get(randomIndex)).toString();
    }
    
    public CachedWav getRandomCachedWav() {
        String randomPath = getRandomFilePath();
        return randomPath != null ? wavCache.getDataFor(randomPath) : null;
    }
    
    public String getFilePathAt(int index) {
        if (index < 0 || index >= filePaths.size()) {
            return null;
        }
        return resolvedDirectory.resolve(filePaths.get(index)).toString();
    }
    
    public CachedWav getCachedWavAt(int index) {
        String path = getFilePathAt(index);
        return path != null ? wavCache.getDataFor(path) : null;
    }
    
    public String getDirectory() {
        return resolvedDirectory.toString();
    }
}
