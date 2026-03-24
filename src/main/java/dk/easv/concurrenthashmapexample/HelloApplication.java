package dk.easv.concurrenthashmapexample;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("hello-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 860, 620);
        stage.setTitle("ConcurrentHashMap — Thread Safety Demo");
        stage.setScene(scene);
        stage.show();
    }
}
