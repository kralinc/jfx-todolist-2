package sample;

import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;

public class Main extends Application {

//    @FXML
//    private static DatePicker datePicker;
//    @FXML
//    private static Button addNewItemsButton;
//    @FXML
//    private static Button closeListBuilderButton;
//    @FXML
//    private static ChoiceBox priorityButton;
//    @FXML
//    private static ScrollPane listScrollPane;
//
    //This marks the location of resources for the program within the filesystem.
    //This ensures that the resources can always be found even if the program's location is moved.
    public static final String RESOURCES_LOCATION = new File(Main.class.getProtectionDomain()
            .getCodeSource()
            .getLocation()
            .getPath())
            .getParentFile()
            .getAbsolutePath()
            + "/resources/";

    //It's convenient to add gui elements as static global variables since there's only one of each.
    static Connection conn;
    static Scene mainScene;

    @Override
    public void start(Stage primaryStage) throws Exception {

        if (!Files.exists(Paths.get(RESOURCES_LOCATION)))
        {
            new File(RESOURCES_LOCATION).mkdir();
        }

        //Database initialization
        conn = loadSQLDatabase(conn);
        removeAllOldLists();
        addRegularEventsToList();

        //JavaFX Controller setup
        Controller controller = new Controller();
        FXMLLoader loader = new FXMLLoader();
        loader.setController(controller);

        //JavaFX Stage setup
        Parent root = loader.load(getClass().getResource("list.fxml"));
        primaryStage.setTitle("To Do List");
        mainScene = new Scene(root, 600, 700);

        //Stylesheet loading
        File styleSheet = new File(RESOURCES_LOCATION + "lightTheme.css");
        mainScene.getStylesheets().add("File://" + styleSheet.getAbsolutePath());

        //Stage initiation
        primaryStage.setScene(mainScene);

        primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent windowEvent) {
                System.out.println("Stage is closing");
                Controller.saveCheckMarks();
            }
        });

        primaryStage.show();
    }

    private Connection loadSQLDatabase(Connection connection)
    {
        //Open a new sqlite database and test the connection.
        //Kill the program if the connection fails.
        connection = null;
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection( "jdbc:sqlite:" + RESOURCES_LOCATION + "todolists.db");
        }catch(Exception e) {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() );
            System.exit(0);
        }
        System.out.println("Database opened successfully.");

        return connection;
    }

    private static void removeAllOldLists()
    {
        try {
            Statement selectAllListsStatement = conn.createStatement();
            String selectAllListsStatementText = "SELECT name FROM sqlite_master "
                                                 + "WHERE type = 'table' "
                                                 + "AND name NOT LIKE 'sqlite_%' "
                                                 + "ORDER BY 1";
            ResultSet allTablesSet = selectAllListsStatement.executeQuery(selectAllListsStatementText);

            ArrayList<String> tablesToDrop = new ArrayList<>();

            while (allTablesSet.next())
            {
                String tableName = allTablesSet.getString(1);
                if (!tableName.startsWith("T"))
                {
                    continue;
                }
                System.out.println(tableName);
                java.util.Date date = new Date();
                String today = String.format("%1$tY%1$tm%1$td", date);
                try {
                    int todayAsInt = Integer.parseInt(today);

                    String tableNameStripped = tableName.substring(1);
                    int tableNameAsInt = Integer.parseInt(tableNameStripped);

                    int todayMinusTable = todayAsInt - tableNameAsInt;

                    //If the current date minus the table date is greater than zero
                    //Then the table is at least a day in the past.
                    if (todayMinusTable > 0)
                    {
                        tablesToDrop.add(tableName);
                    }
                }catch (NumberFormatException e)
                {
                    e.printStackTrace();
                    continue;
                }
            }
            selectAllListsStatement.close();

            for (String tableName : tablesToDrop)
            {
                System.out.println("REMOVING TABLE " + tableName);
                Statement removeTableStatement = conn.createStatement();
                String removeTableString = "DROP TABLE " + tableName;
                removeTableStatement.executeUpdate(removeTableString);
                removeTableStatement.close();
            }

        }catch (SQLException e) {
            e.printStackTrace();
        }
    }

    static void addRegularEventsToList()
    {
        Date date = new Date();
        String today = "T" + String.format("%1$tY%1$tm%1$td", date);

        //Get all of today's tasks first
        //So that if regular events have already been added they don't get added twice
        LinkedList<String> todaysTasks = new LinkedList<>();

        try {
            Statement smt = conn.createStatement();
            //Find if the table exists
            ResultSet res = smt.executeQuery("SELECT * FROM sqlite_master WHERE type='table' AND name='" + today + "'");

            if (!res.isClosed()) {
                try {
                    Statement getTodaysTasksStatement = conn.createStatement();
                    String getTodaysTasksQuery = "SELECT * FROM " + today;
                    ResultSet result = getTodaysTasksStatement.executeQuery(getTodaysTasksQuery);
                    while (result.next()) {
                        todaysTasks.add(result.getString(2));
                    }
                    getTodaysTasksStatement.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }

        createRegularEventsTableIfNotExists();

        try {
            Statement getRegularEventsStatement = conn.createStatement();
            String getRegularEventsString = "SELECT * FROM RegularEvents";
            ResultSet regularEvents = getRegularEventsStatement.executeQuery(getRegularEventsString);
            while (regularEvents.next())
            {
                String task = regularEvents.getString(2);
                String intervalString = regularEvents.getString(3);
                if (todaysTasks.contains(task))
                {
                    continue;
                }


                char interval = intervalString.charAt(0);

                if (interval == 'D')
                {
                    Controller.addItemToList(today, task, 0);
                }

                else if (interval == 'W')
                {
                    String days = intervalString.substring(1);
                    for (int i = 0; i < days.length() - 1; i += 2)
                    {
                        String day = days.substring(i, i+2);

                        Date dtemp = new Date();
                        String d = String.format("%ta", dtemp);
                        if (d.substring(0,2).equals(day))
                        {
                            Controller.addItemToList(today, task, 0);
                        }
                    }
                }

                else
                {
                    String dayOfMonth = intervalString.substring(1);
                    Date dtemp = new Date();
                    String d = String.format("%td", dtemp);
                    if (d.equals(dayOfMonth))
                    {
                        Controller.addItemToList(today, task, 0);
                    }
                }

            }
        }catch (SQLException e)
        {
            e.printStackTrace();
        }
    }

    public static void createRegularEventsTableIfNotExists()
    {
        try {
            Statement createTableStatement = Main.conn.createStatement();
            String createTableQuery = "CREATE TABLE IF NOT EXISTS RegularEvents"
                    +" (ID            INTEGER            NOT NULL PRIMARY KEY AUTOINCREMENT,"
                    + " TASK          VARCHAR(65535)     NOT NULL,"
                    + " INTERVAL       VARCHAR(20)       NOT NULL)";

            createTableStatement.executeUpdate(createTableQuery);
            createTableStatement.close();
        }catch (SQLException e)
        {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
