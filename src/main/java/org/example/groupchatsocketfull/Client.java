package org.example.groupchatsocketfull;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;

public class Client {
    @FXML
    private ListView<Object> messageView;

    @FXML
    private Button btnSend;

    @FXML
    private TextField txtMessage;

    @FXML
    private Button btnImage;

    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String clientName;
    private boolean nameAccepted = false;

    public void initialize() {
        messageView.setCellFactory(listView -> new ListCell<Object>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else if (item instanceof String) {
                    setText((String) item);
                    setGraphic(null);
                } else if (item instanceof Image) {
                    ImageView imageView = new ImageView((Image) item);
                    imageView.setFitHeight(100);
                    imageView.setPreserveRatio(true);
                    setGraphic(imageView);
                    setText(null);
                }
            }
        });

        try {
            Socket socket = new Socket("localhost", 1000);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            Thread thread = new Thread(this::listenForMessages);
            thread.setDaemon(true);
            thread.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void promptForName() {
        Platform.runLater(() -> {
            TextInputDialog dialog = new TextInputDialog("");
            dialog.setTitle("Welcome to Group Chat");
            dialog.setHeaderText("Enter Your Display Name");
            dialog.setContentText("Name:");

            try {
                String cssPath = "/org/example/groupchatsocketfull/dialog.css";
                URL cssUrl = getClass().getResource(cssPath);

                System.out.println("Attempting to load CSS from: " + cssPath);
                System.out.println("CSS URL: " + cssUrl);

                if (cssUrl != null) {
                    String cssString = cssUrl.toExternalForm();
                    System.out.println("CSS External Form: " + cssString);
                    dialog.getDialogPane().getStylesheets().add(cssString);

                    dialog.getDialogPane().setStyle("-fx-border-color:  linear-gradient(to right, #800080, #ffc0cb); -fx-border-width: 2px;");
                } else {
                    System.err.println("CSS not found at: " + cssPath);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            dialog.showAndWait().ifPresentOrElse(name -> {
                clientName = name.trim();
                try {
                    out.writeObject(clientName);
                    out.flush();
                    nameAccepted = true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }, () -> {
                Platform.exit();
            });
        });
    }
    private void listenForMessages() {
        try {
            while (true) {
                Object message = in.readObject();
                if (message == null) break;

                if (message instanceof String text) {
                    if (text.startsWith("SUBMITNAME")) {
                        if (!nameAccepted) {
                            promptForName();
                        }
                    } else if (text.startsWith("NAMEACCEPTED")) {
                        nameAccepted = true;
                    } else if (text.startsWith("TEXT ")) {
                        Platform.runLater(() -> {
                            String rawMessage = text.substring(5);
                            if (rawMessage.startsWith(clientName + ": ")) {
                                messageView.getItems().add("You: " + rawMessage.substring(clientName.length() + 2));
                            } else {
                                messageView.getItems().add(rawMessage);
                            }
                        });
                    } else if (text.startsWith("IMAGE")) {
                        byte[] imageData = (byte[]) in.readObject();
                        Image image = new Image(new ByteArrayInputStream(imageData));
                        Platform.runLater(() -> {
                            messageView.getItems().add(text.substring(6) + " sent an image");
                            messageView.getItems().add(image);
                        });
                    }
                }
            }
        } catch (IOException | ClassNotFoundException e) {
        } finally {
            closeConnection();
        }
    }

    public void closeConnection() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    void btnSendOnAction(ActionEvent event) {
        String message = txtMessage.getText().trim();
        if (message.isEmpty()) return;

        try {
            out.writeObject(message);
            out.flush();
            txtMessage.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    void btnImageOnAction(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );

        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                out.writeObject("IMAGE " + clientName);
                out.flush();

                byte[] imageData = Files.readAllBytes(file.toPath());
                out.writeObject(imageData);
                out.flush();
            } catch (IOException e) {
                Platform.runLater(() -> messageView.getItems().add("Error sending image."));
            }
        }
    }
}
