package com.fan.lazyday.infrastructure.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.file.PathUtils;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author chenbin
 */
@Slf4j
public class Cleaner {
    private final List<FilePart> fileParts = new ArrayList<>();
    private final List<Path> paths = new ArrayList<>();
    private final List<File> files = new ArrayList<>();

    public void add(FilePart filePart) {
        this.fileParts.add(filePart);
    }

    public void add(Path path) {
        this.paths.add(path);
    }

    public void add(File file) {
        this.files.add(file);
    }

    public void commit() {
        commitAsync().subscribe();
    }

    public Mono<Void> commitAsync() {
        return Mono.defer(() -> {
            log.info("清理临时文件");

            Mono<Void> partMono = Mono.empty();
            for (FilePart part : fileParts) {
                partMono = partMono.then(part.delete());
            }

            Mono<Void> pathMono = Mono.empty();
            for (Path path : this.paths) {
                pathMono = pathMono.then(Mono.fromRunnable(() -> {
                    try {
                        PathUtils.delete(path);
                    } catch (IOException e) {
                        log.warn("删除文件失败: {}", path, e);
                    }
                }));
            }
            for (File file : this.files) {
                pathMono = pathMono.then(Mono.fromRunnable(() -> {
                    try {
                        PathUtils.delete(file.toPath());
                    } catch (IOException e) {
                        log.warn("删除文件失败: {}", file, e);
                    }
                }));
            }

            return Mono.when(
                    partMono,
                    pathMono
            );
        });
    }
}
