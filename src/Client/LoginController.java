package Client;

import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ResourceBundle;

/**
 * Created by Igor Klimov on 9/26/2015.
 */
public class LoginController implements Initializable {

    public TextField email;
    public TextField password;
    public VBox root;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    public void doLogin(ActionEvent actionEvent) {
        String email = this.email.getText();
        String password = this.password.getText();
        if (!email.equals("") && !password.equals("")) {
            try (Connection conn = DriverManager.getConnection("jdbc:mysql://192.168.1.166:3306/user1", "IgorKlimov", "pass")) {
                Account acc = new Account(conn);
                if (acc.userExists(email, password)) {
                    System.out.println("User found");
                    Controller.ID = acc.getUserID(email, password);
                    Controller.email = email;
                    acc.getFriends(Controller.ID);
                    Stage window = (Stage) root.getScene().getWindow();
                    window.close();
                    Stage newStage = new Stage();
                    try {
                        newStage.setScene(new Scene(FXMLLoader.load(getClass().getResource("view.fxml"))));
                        newStage.show();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    System.out.println("User wasn't found");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
