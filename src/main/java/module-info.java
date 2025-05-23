module org.example.groupchatsocketfull {
    requires javafx.controls;
    requires javafx.fxml;


    opens org.example.groupchatsocketfull to javafx.fxml;
    exports org.example.groupchatsocketfull;
}