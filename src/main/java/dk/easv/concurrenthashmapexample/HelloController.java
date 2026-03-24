package dk.easv.concurrenthashmapexample;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.util.Duration;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class HelloController {

    @FXML private Spinner<Integer> thread_count_spinner;
    @FXML private Button start_button;
    @FXML private Button stop_button;
    @FXML private Button clear_button;
    @FXML private Label status_label;
    @FXML private Label threads_label;
    @FXML private Label operations_label;
    @FXML private TableView<WordEntry> map_table;
    @FXML private TableColumn<WordEntry, String> key_column;
    @FXML private TableColumn<WordEntry, Integer> value_column;
    @FXML private TableColumn<WordEntry, String> thread_column;
    @FXML private TextArea log_area;

    // ConcurrentHashMap: thread-safe, no explicit synchronization needed
    private final ConcurrentHashMap<String, Integer> word_counts  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String>  last_updater = new ConcurrentHashMap<>();

    // AtomicLong ensures increment is also thread-safe
    private final AtomicLong total_operations = new AtomicLong(0);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final List<Thread> worker_threads = new ArrayList<>();

    private static final String[] WORDS = {
        "apple", "banana", "cherry", "date",
        "elderberry", "fig", "grape", "honeydew"
    };

    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    record WordEntry(String word, int count, String last_thread) {}

    @FXML
    public void initialize() {
        // Wire TableView columns to the WordEntry record fields
        key_column.setCellValueFactory(
            data -> new SimpleStringProperty(data.getValue().word()));
        value_column.setCellValueFactory(
            data -> new SimpleIntegerProperty(data.getValue().count()).asObject());
        thread_column.setCellValueFactory(
            data -> new SimpleStringProperty(data.getValue().last_thread()));

        // Thread count spinner: range 1–20, default 5
        thread_count_spinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20, 5));

        stop_button.setDisable(true);

        // Refresh the table on the JavaFX thread every 400 ms.
        // Reading from a ConcurrentHashMap during iteration is always safe —
        // it never throws ConcurrentModificationException.
        Timeline refresh_timer = new Timeline(
            new KeyFrame(Duration.millis(400), e -> refresh_table())
        );
        refresh_timer.setCycleCount(Timeline.INDEFINITE);
        refresh_timer.play();

        append_log("Ready. Adjust thread count and press Start.");
    }

    @FXML
    private void on_start_clicked() {
        int num_threads = thread_count_spinner.getValue();
        running.set(true);
        worker_threads.clear();

        for (int i = 1; i <= num_threads; i++) {
            String thread_name = "Worker-" + i;
            Thread t = new Thread(() -> worker_loop(thread_name), thread_name);
            t.setDaemon(true);  // stops automatically when the JavaFX app closes
            t.start();
            worker_threads.add(t);
        }

        start_button.setDisable(true);
        stop_button.setDisable(false);
        status_label.setText("Running");
        status_label.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
        append_log("Started " + num_threads + " worker thread(s).");
    }

    @FXML
    private void on_stop_clicked() {
        // Setting the flag to false signals every worker to exit its loop
        running.set(false);
        start_button.setDisable(false);
        stop_button.setDisable(true);
        status_label.setText("Stopped");
        status_label.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
        append_log("Stop signal sent — threads will finish their current iteration.");
    }

    @FXML
    private void on_clear_clicked() {
        // clear() on ConcurrentHashMap is also thread-safe
        word_counts.clear();
        last_updater.clear();
        total_operations.set(0);
        append_log("Map cleared.");
    }

    private void worker_loop(String thread_name) {
        Random rng       = new Random();
        long   local_ops = 0;

        while (running.get()) {
            String word = WORDS[rng.nextInt(WORDS.length)];

            // It reads the current value, applies the remapping function, and stores the result — all as a single, uninterruptible operation.
            word_counts.merge(word, 1, Integer::sum);

            // Record which thread last touched this key (separate ConcurrentHashMap)
            last_updater.put(word, thread_name);

            total_operations.incrementAndGet();
            local_ops++;

            // Log a sample to the UI every 100 local operations — avoids flooding Platform.runLater with thousands of calls
            if (local_ops % 100 == 0) {
                int snapshot = word_counts.getOrDefault(word, 0);
                String msg = thread_name + " » '" + word + "' is now " + snapshot;
                Platform.runLater(() -> append_log(msg));
            }

            try {
                Thread.sleep(rng.nextInt(20) + 5);  // 5–25 ms between updates
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        Platform.runLater(() -> append_log(thread_name + " has stopped."));
    }

    private void refresh_table() {
        // Take a snapshot of the map entries, sort by count descending
        var entries = word_counts.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .map(e -> new WordEntry(
                e.getKey(),
                e.getValue(),
                last_updater.getOrDefault(e.getKey(), "-")
            ))
            .toList();

        map_table.setItems(FXCollections.observableArrayList(entries));

        operations_label.setText(String.valueOf(total_operations.get()));

        long active = worker_threads.stream().filter(Thread::isAlive).count();
        threads_label.setText(String.valueOf(active));
    }

    // Must only be called from the JavaFX thread (or via Platform.runLater)
    private void append_log(String message) {
        String time = LocalTime.now().format(TIME_FMT);
        log_area.appendText("[" + time + "] " + message + "\n");
        log_area.setScrollTop(Double.MAX_VALUE);  // auto-scroll to bottom
    }
}