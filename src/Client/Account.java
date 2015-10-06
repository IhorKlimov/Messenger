package Client;

import res.Friend;

import java.sql.*;

import static java.sql.Types.INTEGER;

/**
 * Created by Igor Klimov on 9/27/2015.
 */
public class Account {
    private Connection connection;

    public Account(Connection connection) {
        this.connection = connection;
    }

    public boolean userExists(String email, String password) throws SQLException {
        String sql = "{ ? = call USER_EXISTS(?,?) }";
        CallableStatement statement = connection.prepareCall(sql);
        statement.setString(2, email);
        statement.setString(3, password);
        statement.registerOutParameter(1, INTEGER);

        statement.execute();
        int count = statement.getInt(1);
        return count != 0;
    }

    public int getUserID(String email, String password) throws SQLException {
        String sql = "{? = call GET_ID(?,?)}";
        CallableStatement statement = connection.prepareCall(sql);
        statement.setString(2, email);
        statement.setString(3, password);
        statement.registerOutParameter(1, INTEGER);
        statement.execute();
        return statement.getInt(1);
    }

    public void getFriends(int id) throws SQLException {
        String sql = "SELECT * FROM link WHERE user_id = ?";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setInt(1, id);
        ResultSet resultSet = statement.executeQuery();
        while (resultSet.next()) {
            int friendID = resultSet.getInt(2);
            String sql2 = "SELECT first_name FROM user WHERE user_id = ?";
            PreparedStatement statement1 = connection.prepareStatement(sql2);
            statement1.setInt(1, friendID);
            ResultSet resultSet1 = statement1.executeQuery();
            resultSet1.next();
            String name = resultSet1.getString(1);
            System.out.println(name);
            Controller.friends.add(new Friend(name, friendID));
        }
    }

    public String getName(int id) throws SQLException {
        String sql = "SELECT first_name FROM  user WHERE  user_id = ?";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setInt(1, id);
        ResultSet resultSet = statement.executeQuery();
        resultSet.next();
        return resultSet.getString(1);
    }

    public void addFriend(int userId, int friendId) {
        try {
            String sql = "CALL add_friend(?,?)";
            CallableStatement statement = connection.prepareCall(sql);
            statement.setInt(1,userId);
            statement.setInt(2, friendId);
            statement.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
