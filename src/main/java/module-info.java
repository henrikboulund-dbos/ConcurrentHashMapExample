module dk.easv.concurrenthashmapexample {
    requires javafx.controls;
    requires javafx.fxml;


    opens dk.easv.concurrenthashmapexample to javafx.fxml;
    exports dk.easv.concurrenthashmapexample;
}