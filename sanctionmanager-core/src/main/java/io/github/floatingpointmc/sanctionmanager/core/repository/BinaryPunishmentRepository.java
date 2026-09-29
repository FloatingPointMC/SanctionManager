package io.github.floatingpointmc.sanctionmanager.core.repository;

import io.github.floatingpointmc.sanctionmanager.api.punishment.Punishment;
import io.github.floatingpointmc.sanctionmanager.api.punishment.Type;
import io.github.floatingpointmc.sanctionmanager.core.model.PunishmentRecord;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class BinaryPunishmentRepository implements PunishmentRepository, AutoCloseable {
    private static final byte[] MAGIC = {'S', 'M', 'D', 'B'};
    private static final short VERSION = 1;

    private final Path dataFile;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final AtomicInteger nextPunishmentId = new AtomicInteger(1);
    private final AtomicInteger nextBanId = new AtomicInteger(1);
    private final AtomicInteger nextMuteId = new AtomicInteger(1);

    private final Map<Integer, PunishmentRecord> punishmentsById = new LinkedHashMap<>();
    private final Map<Integer, PunishmentRecord> bansById = new LinkedHashMap<>();
    private final Map<Integer, PunishmentRecord> mutesById = new LinkedHashMap<>();

    public BinaryPunishmentRepository(@NotNull Path dataDir) {
        try {
            Files.createDirectories(dataDir);
        } catch (IOException ignored) {
        }
        this.dataFile = dataDir.resolve("sanction-data.bin");
        load();
    }

    private void load() {
        if (!Files.exists(dataFile)) {
            return;
        }
        lock.writeLock().lock();
        try (InputStream fis = Files.newInputStream(dataFile);
             DataInputStream dis = new DataInputStream(new BufferedInputStream(fis))) {
            byte[] magic = new byte[4];
            dis.readFully(magic);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new IOException("Invalid magic bytes in binary storage file");
            }
            short version = dis.readShort();
            if (version != VERSION) {
                throw new IOException("Unsupported binary storage version: " + version);
            }

            nextPunishmentId.set(dis.readInt());
            nextBanId.set(dis.readInt());
            nextMuteId.set(dis.readInt());

            int banCount = dis.readInt();
            for (int i = 0; i < banCount; i++) {
                PunishmentRecord ban = readDetailRecord(dis, Type.BAN);
                bansById.put(ban.getRelId(), ban);
            }

            int muteCount = dis.readInt();
            for (int i = 0; i < muteCount; i++) {
                PunishmentRecord mute = readDetailRecord(dis, Type.MUTE);
                mutesById.put(mute.getRelId(), mute);
            }

            int punishmentCount = dis.readInt();
            for (int i = 0; i < punishmentCount; i++) {
                int id = dis.readInt();
                int relId = dis.readInt();
                byte typeVal = dis.readByte();
                Type type = Type.values()[typeVal];
                PunishmentRecord detail = type == Type.BAN ? bansById.get(relId) : mutesById.get(relId);
                if (detail != null) {
                    PunishmentRecord record = new PunishmentRecord(
                            id, relId, detail.getTarget(), detail.getExecutor(),
                            detail.getOperatorName(), detail.getExecutingTime(), detail.getExpiryTime(),
                            detail.isOverridden(), detail.getOverriddenBy(), detail.isOverriding(),
                            detail.getOverriddenPunishment(), detail.isWithdrawn(), detail.getWithdrawnBy(),
                            detail.getReason(), type
                    );
                    punishmentsById.put(id, record);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load binary storage", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private @NotNull PunishmentRecord readDetailRecord(@NotNull DataInputStream dis, @NotNull Type type) throws IOException {
        int id = dis.readInt();
        UUID target = readUuid(dis);
        UUID executor = readNullableUuid(dis);
        String operatorName = dis.readUTF();
        LocalDateTime executingTime = readLocalDateTime(dis);
        LocalDateTime expiryTime = readNullableLocalDateTime(dis);
        boolean overridden = dis.readBoolean();
        dis.readInt();
        boolean overriding = dis.readBoolean();
        dis.readInt();
        boolean withdrawn = dis.readBoolean();
        UUID withdrawnBy = readNullableUuid(dis);
        String reason = readNullableString(dis);
        PunishmentRecord record = new PunishmentRecord(
                0, id, target, executor, operatorName, executingTime, expiryTime,
                overridden, null, overriding, null, withdrawn, withdrawnBy, reason, type
        );
        record.setProcessed(true);
        return record;
    }

    private void saveToFile() {
        lock.readLock().lock();
        try {
            Path tempFile = dataFile.resolveSibling("sanction-data.bin.tmp");
            try (OutputStream fos = Files.newOutputStream(tempFile);
                 DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(fos))) {
                dos.write(MAGIC);
                dos.writeShort(VERSION);
                dos.writeInt(nextPunishmentId.get());
                dos.writeInt(nextBanId.get());
                dos.writeInt(nextMuteId.get());

                dos.writeInt(bansById.size());
                for (PunishmentRecord ban : bansById.values()) {
                    writeDetailRecord(dos, ban);
                }

                dos.writeInt(mutesById.size());
                for (PunishmentRecord mute : mutesById.values()) {
                    writeDetailRecord(dos, mute);
                }

                dos.writeInt(punishmentsById.size());
                for (PunishmentRecord p : punishmentsById.values()) {
                    dos.writeInt(p.getId());
                    dos.writeInt(p.getRelId());
                    dos.writeByte((byte) p.getType().ordinary());
                }
                dos.flush();
            }
            Files.move(tempFile, dataFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save binary storage", e);
        } finally {
            lock.readLock().unlock();
        }
    }

    private void writeDetailRecord(@NotNull DataOutputStream dos, @NotNull PunishmentRecord p) throws IOException {
        dos.writeInt(p.getRelId());
        writeUuid(dos, p.getTarget());
        writeNullableUuid(dos, p.getExecutor());
        dos.writeUTF(p.getOperatorName());
        writeLocalDateTime(dos, p.getExecutingTime());
        writeNullableLocalDateTime(dos, p.getExpiryTime());
        dos.writeBoolean(p.isOverridden());
        dos.writeInt(p.getOverriddenBy() != null ? p.getOverriddenBy().getId() : 0);
        dos.writeBoolean(p.isOverriding());
        dos.writeInt(p.getOverriddenPunishment() != null ? p.getOverriddenPunishment().getId() : 0);
        dos.writeBoolean(p.isWithdrawn());
        writeNullableUuid(dos, p.getWithdrawnBy());
        writeNullableString(dos, p.getReason());
    }

    @Override
    public @Nullable Punishment findById(int id) {
        lock.readLock().lock();
        try {
            return punishmentsById.get(id);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public @NotNull Collection<Punishment> findByTarget(@NotNull UUID target) {
        lock.readLock().lock();
        try {
            List<Punishment> result = new ArrayList<>();
            for (PunishmentRecord p : punishmentsById.values()) {
                if (p.getTarget().equals(target)) {
                    result.add(p);
                }
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public @NotNull Collection<Punishment> findActiveByTarget(@NotNull UUID target) {
        lock.readLock().lock();
        try {
            List<Punishment> result = new ArrayList<>();
            for (PunishmentRecord p : punishmentsById.values()) {
                if (p.getTarget().equals(target) && isActive(p)) {
                    result.add(p);
                }
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public @NotNull Collection<Punishment> findActiveBansByTarget(@NotNull UUID target) {
        lock.readLock().lock();
        try {
            List<Punishment> result = new ArrayList<>();
            for (PunishmentRecord p : punishmentsById.values()) {
                if (p.getType() == Type.BAN && p.getTarget().equals(target) && isActive(p)) {
                    result.add(p);
                }
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public @NotNull Collection<Punishment> findActiveMutesByTarget(@NotNull UUID target) {
        lock.readLock().lock();
        try {
            List<Punishment> result = new ArrayList<>();
            for (PunishmentRecord p : punishmentsById.values()) {
                if (p.getType() == Type.MUTE && p.getTarget().equals(target) && isActive(p)) {
                    result.add(p);
                }
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    private boolean isActive(@NotNull PunishmentRecord p) {
        if (p.isOverridden() || p.isWithdrawn()) return false;
        return p.getExpiryTime() == null || !p.getExpiryTime().isBefore(LocalDateTime.now());
    }

    @Override
    public void save(@NotNull Punishment punishment) {
        lock.writeLock().lock();
        try {
            if (!(punishment instanceof PunishmentRecord)) {
                throw new IllegalArgumentException("Punishment must be a PunishmentRecord");
            }
            PunishmentRecord record = (PunishmentRecord) punishment;

            int relId;
            if (record.getType() == Type.BAN) {
                relId = nextBanId.getAndIncrement();
            } else {
                relId = nextMuteId.getAndIncrement();
            }
            int punishmentId = nextPunishmentId.getAndIncrement();

            PunishmentRecord saved = new PunishmentRecord(
                    punishmentId, relId, record.getTarget(), record.getExecutor(),
                    record.getOperatorName(), record.getExecutingTime(), record.getExpiryTime(),
                    record.isOverridden(), record.getOverriddenBy(), record.isOverriding(),
                    record.getOverriddenPunishment(), record.isWithdrawn(), record.getWithdrawnBy(),
                    record.getReason(), record.getType()
            );

            punishmentsById.put(punishmentId, saved);
            if (saved.getType() == Type.BAN) {
                bansById.put(relId, saved);
            } else {
                mutesById.put(relId, saved);
            }

            record.setId(punishmentId);
            record.setRelId(relId);
            record.setProcessed(true);

            saveToFile();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void update(@NotNull Punishment punishment) {
        lock.writeLock().lock();
        try {
            if (!(punishment instanceof PunishmentRecord)) {
                throw new IllegalArgumentException("Punishment must be a PunishmentRecord");
            }
            PunishmentRecord record = (PunishmentRecord) punishment;
            int id = record.getId();
            int relId = record.getRelId();

            PunishmentRecord existing = punishmentsById.get(id);
            if (existing == null) {
                throw new IllegalArgumentException("Punishment not found: " + id);
            }

            punishmentsById.put(id, record);
            if (record.getType() == Type.BAN) {
                bansById.put(relId, record);
            } else {
                mutesById.put(relId, record);
            }

            saveToFile();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void delete(int id) {
        lock.writeLock().lock();
        try {
            PunishmentRecord existing = punishmentsById.remove(id);
            if (existing != null) {
                int relId = existing.getRelId();
                if (existing.getType() == Type.BAN) {
                    bansById.remove(relId);
                } else {
                    mutesById.remove(relId);
                }
            }
            saveToFile();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void close() {
        saveToFile();
    }

    private static void writeUuid(@NotNull DataOutputStream dos, @NotNull UUID uuid) throws IOException {
        dos.writeLong(uuid.getMostSignificantBits());
        dos.writeLong(uuid.getLeastSignificantBits());
    }

    private static @NotNull UUID readUuid(@NotNull DataInputStream dis) throws IOException {
        long most = dis.readLong();
        long least = dis.readLong();
        return new UUID(most, least);
    }

    private static void writeNullableUuid(@NotNull DataOutputStream dos, @Nullable UUID uuid) throws IOException {
        if (uuid != null) {
            dos.writeBoolean(true);
            writeUuid(dos, uuid);
        } else {
            dos.writeBoolean(false);
        }
    }

    private static @Nullable UUID readNullableUuid(@NotNull DataInputStream dis) throws IOException {
        if (dis.readBoolean()) {
            return readUuid(dis);
        }
        return null;
    }

    private static void writeLocalDateTime(@NotNull DataOutputStream dos, @NotNull LocalDateTime ldt) throws IOException {
        dos.writeLong(ldt.toEpochSecond(ZoneOffset.UTC));
        dos.writeInt(ldt.getNano());
    }

    private static @NotNull LocalDateTime readLocalDateTime(@NotNull DataInputStream dis) throws IOException {
        long epochSecond = dis.readLong();
        int nano = dis.readInt();
        return LocalDateTime.ofEpochSecond(epochSecond, nano, ZoneOffset.UTC);
    }

    private static void writeNullableLocalDateTime(@NotNull DataOutputStream dos, @Nullable LocalDateTime ldt) throws IOException {
        if (ldt != null) {
            dos.writeBoolean(true);
            writeLocalDateTime(dos, ldt);
        } else {
            dos.writeBoolean(false);
        }
    }

    private static @Nullable LocalDateTime readNullableLocalDateTime(@NotNull DataInputStream dis) throws IOException {
        if (dis.readBoolean()) {
            return readLocalDateTime(dis);
        }
        return null;
    }

    private static void writeNullableString(@NotNull DataOutputStream dos, @Nullable String s) throws IOException {
        if (s != null) {
            dos.writeBoolean(true);
            dos.writeUTF(s);
        } else {
            dos.writeBoolean(false);
        }
    }

    private static @Nullable String readNullableString(@NotNull DataInputStream dis) throws IOException {
        if (dis.readBoolean()) {
            return dis.readUTF();
        }
        return null;
    }
}
