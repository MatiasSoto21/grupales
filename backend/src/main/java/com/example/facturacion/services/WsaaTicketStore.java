package com.example.facturacion.services;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.Instant;
import java.util.Properties;

final class WsaaTicketStore {
    record Ticket(String token, String sign, Instant expiration) {
        boolean valid() {
            return token != null && !token.isBlank() && sign != null && !sign.isBlank()
                    && expiration != null && Instant.now().plusSeconds(60).isBefore(expiration);
        }
    }
    private final Path directory;
    private final Path file;
    WsaaTicketStore(Path directory, String fingerprint) throws IOException {
        this.directory = directory;
        Files.createDirectories(directory);
        protect(directory, "rwx------");
        file = directory.resolve("wsfe-" + fingerprint + ".properties");
    }
    FileChannel openLock() throws IOException {
        Path lock = directory.resolve(file.getFileName().toString() + ".lock");
        FileChannel channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        protect(lock, "rw-------");
        return channel;
    }
    Ticket read() throws IOException {
        if (!Files.exists(file)) return null;
        Properties properties = new Properties();
        try (var input = Files.newInputStream(file)) { properties.load(input); }
        try {
            var ticket = new Ticket(properties.getProperty("token"), properties.getProperty("sign"),
                    Instant.parse(properties.getProperty("expiration", "")));
            return ticket.valid() ? ticket : null;
        } catch (java.time.format.DateTimeParseException exception) { return null; }
    }
    void write(Ticket ticket) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("token", ticket.token());
        properties.setProperty("sign", ticket.sign());
        properties.setProperty("expiration", ticket.expiration().toString());
        Path temporary = Files.createTempFile(directory, "wsaa-", ".tmp");
        try {
            protect(temporary, "rw-------");
            try (var output = Files.newOutputStream(temporary)) { properties.store(output, "WSAA access ticket - private"); }
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            protect(file, "rw-------");
        } finally { Files.deleteIfExists(temporary); }
    }
    private static void protect(Path path, String permissions) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString(permissions));
        }
    }
}
