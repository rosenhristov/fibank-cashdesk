package bg.fibank.cashdesk.repository;

import bg.fibank.cashdesk.exception.FileFormatException;
import bg.fibank.cashdesk.model.CashierBalance;
import bg.fibank.cashdesk.model.Currency;
import bg.fibank.cashdesk.model.Denomination;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static bg.fibank.cashdesk.model.Currency.BGN;
import static bg.fibank.cashdesk.model.Currency.EUR;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import bg.fibank.cashdesk.config.AppProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

    /**
     * Persists and retrieves cashier balance state using {@code cash_balances.txt}.
     *
     * <h2>Runtime model</h2>
     * <p>The file is loaded once into a {@code HashMap} on application startup.
     * All reads are served from memory. Every {@link #save} atomically rewrites
     * the full file so the on-disk state always reflects the live in-memory state.</p>
     *
     * <h2>Atomic write strategy</h2>
     * <ol>
     *   <li>Write all rows to {@code cash_balances.txt.tmp}.</li>
     *   <li>Call {@link Files#move} with {@link StandardCopyOption#ATOMIC_MOVE} and
     *       {@link StandardCopyOption#REPLACE_EXISTING}.</li>
     * </ol>
     * <p>This guarantees the balance file is never half-written. If the JVM dies
     * mid-write the {@code .tmp} file is left behind, the original file is
     * untouched, and the next startup reads the last consistent state.</p>
     *
     * <h2>Thread safety</h2>
     * <p>A {@link ReentrantReadWriteLock} guards all access to the in-memory cache
     * and file writes. Multiple concurrent reads are served in parallel; a write
     * excludes all other readers and writers. The service layer additionally
     * synchronises per-cashier-name to keep the read-mutate-save window atomic.</p>
     */
    @Slf4j
    @Repository
    @RequiredArgsConstructor
    public class BalanceFileRepository {

        private final AppProperties appProperties;

        /**
         * In-memory cache. Keyed by upper-cased cashier name.
         */
        private final Map<String, CashierBalance> cache = new LinkedHashMap<>();

        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

        private Path balancesPath;
        private Path balancesTmpPath;


        @PostConstruct
        public void init() throws IOException {
            balancesPath = Path.of(appProperties.getData().getBalancesFile());
            balancesTmpPath = balancesPath.resolveSibling(balancesPath.getFileName() + ".tmp");

            ensureFileExists(balancesPath);
            loadFromFile();

            log.info("BALANCE_REPO | Initialised | file={} | cashiers={}",
                      balancesPath, cache.keySet());
        }

        /**
         * Returns all cashier balances currently held in memory, in insertion order.
         *
         * @return unmodifiable snapshot of the cache values
         */
        public List<CashierBalance> findAll() {
            lock.readLock().lock();
            try {
                return List.copyOf(cache.values());
            } finally {
                lock.readLock().unlock();
            }
        }

        /**
         * Returns the balance for a single cashier, or {@link Optional#empty()} if
         * the cashier is not in the system.
         *
         * @param cashierName case-insensitive cashier name
         */
        public Optional<CashierBalance> findByCashier(String cashierName) {
            lock.readLock().lock();
            try {
                return Optional.ofNullable(cache.get(cashierName.toUpperCase()));
            } finally {
                lock.readLock().unlock();
            }
        }

        /**
         * Returns {@code true} if a cashier with the given name exists in the system.
         *
         * @param cashierName case-insensitive cashier name
         */
        public boolean exists(String cashierName) {
            lock.readLock().lock();
            try {
                return cache.containsKey(cashierName.toUpperCase());
            } finally {
                lock.readLock().unlock();
            }
        }

        /**
         * Persists an updated {@link CashierBalance} back to the cache and rewrites
         * the balance file atomically.
         *
         * <p>Called by the service layer after every deposit or withdrawal.
         * The caller must hold its own per-cashier lock to keep the
         * read→mutate→save window atomic.</p>
         *
         * @param balance the updated balance to persist
         * @throws IOException if the file write fails
         */
        public void save(CashierBalance balance) throws IOException {
            lock.writeLock().lock();
            try {
                cache.put(balance.getCashierName().toUpperCase(), balance);
                writeToFile();
                log.info("BALANCE_REPO | Saved | cashier={} | BGN={} | EUR={}",
                        balance.getCashierName(),
                        balance.getTotalForCurrency(BGN),
                        balance.getTotalForCurrency(EUR));
            } finally {
                lock.writeLock().unlock();
            }
        }

        /**
         * Saves a brand-new cashier entry (used by {@code DataInitializer}).
         * Identical to {@link #save} but logs at DEBUG to keep startup output clean.
         *
         * @param balance the initial balance to persist
         * @throws IOException if the file write fails
         */
        public void saveInitial(CashierBalance balance) throws IOException {
            lock.writeLock().lock();
            try {
                cache.put(balance.getCashierName().toUpperCase(), balance);
                writeToFile();
                log.debug("BALANCE_REPO | Initial save | cashier={}", balance.getCashierName());
            } finally {
                lock.writeLock().unlock();
            }
        }


        /**
         * Creates the balance file (with the header comment) if it does not exist.
         * Parent directories are also created as needed.
         */
        private void ensureFileExists(Path path) throws IOException {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, FileFormat.BAL_HEADER + System.lineSeparator());
                log.info("BALANCE_REPO | Created new balance file | path={}", path);
            }
        }

        /**
         * Reads every non-skippable line from the balance file and reconstructs
         * the in-memory cache. Called once in {@link #init()}.
         */
        private void loadFromFile() throws IOException {
            List<String> lines = Files.readAllLines(balancesPath);
            int loaded = 0;

            for (String line : lines) {
                if (FileFormat.isSkippable(line)) {
                    continue;
                }

                try {
                    FileFormat.BalanceLine balanceLine = FileFormat.decodeBalanceLine(line);
                    String key = balanceLine.cashierName().toUpperCase();

                    CashierBalance balance = cache.computeIfAbsent(
                            key, k -> new CashierBalance(balanceLine.cashierName()));

                    balance.addDenominations(
                            balanceLine.currency(),
                            List.of(new Denomination(balanceLine.faceValue(), balanceLine.count())));

                    loaded++;
                } catch (FileFormatException e) {
                    log.warn("BALANCE_REPO | Skipping malformed line: {} | error: {}",
                            line, e.getMessage());
                }
            }

            log.info("BALANCE_REPO | Loaded {} denomination rows for {} cashier(s)",
                    loaded, cache.size());
        }

        /**
         * Writes the entire cache to {@link #balancesTmpPath}, then atomically
         * moves it over {@link #balancesPath}. Must be called while holding the
         * write lock.
         */
        private void writeToFile() throws IOException {
            try (BufferedWriter writer = Files.newBufferedWriter(balancesTmpPath)) {
                writer.write(FileFormat.BAL_HEADER);
                writer.newLine();

                for (CashierBalance balance : cache.values()) {
                    for (Currency currency : Currency.values()) {
                        for (Denomination d : balance.getDenominationsForCurrency(currency)) {
                            writer.write(FileFormat.encodeBalanceLine(
                                    balance.getCashierName(), currency, d));
                            writer.newLine();
                        }
                    }
                }
            }

            Files.move(balancesTmpPath, balancesPath, ATOMIC_MOVE, REPLACE_EXISTING);
        }
    }
