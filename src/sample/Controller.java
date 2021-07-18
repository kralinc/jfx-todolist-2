package sample;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.StringConverter;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

public class Controller {

    //List Viewer Elements
    @FXML
    private Pane listViewerGroup;
    @FXML
    private Button addNewItemsButton;
    @FXML
    private ScrollPane listScrollPane;

    //List builder elements
    @FXML
    private Pane listBuilderGroup;
    @FXML
    private ListView selectedListView;
    @FXML
    private DatePicker datePicker;
    @FXML
    private TextArea listTextArea;
    @FXML
    private Button closeListBuilderButton;
    @FXML
    private Button saveButton;
    @FXML
    private ChoiceBox<String> priorityButton;

    //Regular events group
    @FXML
    private Pane regularEventsGroup;
    @FXML
    private ToggleGroup repeatInterval;
    @FXML
    private HBox weeklyRegularEventsContainer;
    @FXML
    private HBox monthlyRegularEventsContainer;
    @FXML
    ChoiceBox monthlyRegularEventsChoiceBox;
    @FXML
    TextField regularEventInput;
    @FXML
    ListView regularEventsList;


    private static VBox checklistItems;
    private static String TODAY;
    private static HashMap<Integer, Boolean> checkedBoxIDs = new HashMap();
    
    public void initialize()
    {

        //This converts the date format to yyyy-mm-dd
        datePicker.setConverter(new StringConverter<LocalDate>() {
            private final DateTimeFormatter dateTimeFormatter;
            {
                this.dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            }


            public String toString(LocalDate localDate)
            {
                if(localDate==null)
                    return "";
                return dateTimeFormatter.format(localDate);
            }

            @Override
            public LocalDate fromString(String dateString)
            {
                if(dateString==null || dateString.trim().isEmpty())
                {
                    return null;
                }
                return LocalDate.parse(dateString,dateTimeFormatter);
            }
        });


        repeatInterval.selectedToggleProperty().addListener(new ChangeListener<Toggle>() {
            public void changed(ObservableValue<? extends Toggle> ov, Toggle toggle, Toggle new_toggle) {
                RadioButton b = (RadioButton) repeatInterval.getSelectedToggle();
                changeRepeatInterval(b.getText());
            }
        });

        //Add items to choicebox
        for (int i = 1; i < 32; i++)
        {
            monthlyRegularEventsChoiceBox.getItems().add(i);
        }

        selectedListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);


        refreshList();
    }


    EventHandler<MouseEvent> checkboxEH = event -> {
        if (event.getSource() instanceof CheckBox) {
            CheckBox chk = (CheckBox)event.getSource();
            Integer boxID = Integer.parseInt(chk.getId().substring(3));
            checkedBoxIDs.put(boxID, chk.isSelected());
        }
    };


    @FXML
    private void handleButtonAction(ActionEvent event)
    {
        if (event.getSource() instanceof Button) {
            Button button = (Button) event.getSource();
            buttonClicked(button.getId());
        }
    }

    private void changeRepeatInterval(String interval)
    {
        boolean weeklyEnabled;
        boolean monthlyEnabled;
        if (interval.equals("Daily"))
        {
            weeklyEnabled = true;
            monthlyEnabled = true;
        }else if (interval.equals("Weekly"))
        {
            weeklyEnabled = false;
            monthlyEnabled = true;
        }else //Monthly
        {
            weeklyEnabled = true;
            monthlyEnabled = false;
        }

        weeklyRegularEventsContainer.setDisable(weeklyEnabled);
        monthlyRegularEventsContainer.setDisable(monthlyEnabled);
    }

    private void buttonClicked(String id) {
        if (id.equals("addNewItemsButton"))
        {
            changeScreen(1);
        }
        else if (id.equals("closeListBuilderButton"))
        {
            changeScreen(0);
        }
        else if (id.equals("saveButton"))
        {
            addTextItemsToList();
        }else if (id.equals("removeItemButton"))
        {
            String date = datePicker.getValue().toString();
            date = "T" + date.replace("-", "");
            removeItemFromList(selectedListView.getSelectionModel().getSelectedItems(), date, selectedListView);
        }else if (id.equals("closeRegularEventsButton"))
        {
            changeScreen(0);
        }else if (id.equals("regButton"))
        {
            changeScreen(2);
        }else if (id.equals("saveButtonReg"))
        {
            saveRegularEvents();
            Main.addRegularEventsToList();
            changeScreen(0);
        }else if (id.equals("removeRegularEventButton"))
        {
            removeItemFromList(regularEventsList.getSelectionModel().getSelectedItems(), "RegularEvents", regularEventsList);
        }
    }

    @FXML
    private void onDatePickerValueChanged(ActionEvent event)
    {
        refreshSelectedListView(datePicker.getValue().toString());
    }

    private void refreshList()
    {
        Date date = new Date();
        //All tables names must begin with a letter, so I start them with T for "Time"
        //The table names follow the convention TYYYYMMDD
        TODAY = "T" + String.format("%1$tY%1$tm%1$td", date);
        //System.out.println(today);

        try {
            Statement smt = Main.conn.createStatement();
            //Find if the table exists
            ResultSet result = smt.executeQuery("SELECT * FROM sqlite_master WHERE type='table' AND name='" + TODAY + "'");

            if (result.isClosed()) {
                showNoTasks();
            }else {
                Statement getListStatement = Main.conn.createStatement();
                String getListQuery = "SELECT * FROM " + TODAY;
                ResultSet getListResult = getListStatement.executeQuery(getListQuery);

                createChecklist(getListResult);


                getListStatement.close();
            }
            smt.close();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

    }

    private void addTextItemsToList()
    {
        String listAreaText = listTextArea.getText(); //.strip()
        if (!listAreaText.equals(""))
        {
            int priorityNum = priorityToInt();
            String today = datePicker.getConverter().toString(datePicker.getValue());
            today = "T" + today.replace("-", "");
            //System.out.println(today);

            String[] listItems = listAreaText.split("\n");

            //Add a new item to the database for each line in the textbox
            for (String item : listItems)
            {
                addItemToList(today, item, priorityNum);
            }


            listTextArea.setText("");
            changeScreen(0);
        }
    }

    static void addItemToList(String list, String item, int priorityNum)
    {
        //Ensure that the list exists before adding to it
        try {

            //Create a new table in the database if one doesn't exist yet
            Statement createTableStatement = Main.conn.createStatement();
            String createTableQuery = "CREATE TABLE IF NOT EXISTS " + list
                    +" (ID            INTEGER            NOT NULL PRIMARY KEY AUTOINCREMENT,"
                    + " TASK          VARCHAR(65535)     NOT NULL,"
                    + " CHECKED       BOOL               NOT NULL DEFAULT 0,"
                    + " PRIORITY      BYTE               NOT NULL)";

            createTableStatement.executeUpdate(createTableQuery);
            createTableStatement.close();
        }catch (SQLException e)
        {
            e.printStackTrace();
        }

        String listItemQuery = "INSERT INTO " + list + " (TASK, CHECKED, PRIORITY) VALUES (?, 0, ?)";
        try {
            PreparedStatement smt = Main.conn.prepareStatement(listItemQuery);
            smt.setString(1, item);
            smt.setByte(2, (byte) priorityNum);
            smt.executeUpdate();
            smt.close();
        }catch (SQLException e)
        {
            e.printStackTrace();
        }
    }

    private void removeItemFromList (ObservableList<String> tasks, String tableName, ListView list) {

        try {
            for (String task : tasks) {
                String[] temp = task.split(" \\| ");
                String idNum = temp[temp.length - 1];
                Statement removeItemStatement = Main.conn.createStatement();
                String removeItemQuery = "DELETE FROM " + tableName + " WHERE ID=" + idNum;
                removeItemStatement.executeUpdate(removeItemQuery);
                removeItemStatement.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        list.getItems().removeAll(tasks);
    }

    private void changeScreen(int screen)
    {
        boolean listviewer;
        boolean listbuilder;
        boolean regevents;
        if (screen == 0)
        {
            listviewer = true;
            listbuilder = false;
            regevents = false;
            refreshList();
        }else if (screen == 1)
        {
            listviewer = false;
            listbuilder = true;
            regevents = false;
            datePicker.setValue(LocalDate.now());
            refreshSelectedListView(datePicker.getValue().toString());
        }else
        {
            listviewer = false;
            listbuilder = false;
            regevents = true;
            refreshRegularEventsListView();
        }

        listViewerGroup.setDisable(!listviewer);
        listViewerGroup.setVisible(listviewer);

        listBuilderGroup.setDisable(!listbuilder);
        listBuilderGroup.setVisible(listbuilder);

        regularEventsGroup.setDisable(!regevents);
        regularEventsGroup.setVisible(regevents);

        saveCheckMarks();
    }

    private void createChecklist (ResultSet result)
    {

        checklistItems = new VBox();
        checklistItems.setId("checklistItems");

        try {
            while (result.next()) {
                int ID = result.getInt(1);
                String TASK = result.getString(2);
                boolean CHECKED = result.getBoolean(3);
                byte PRIORITY = result.getByte(4);


                //Create a new checklist item and arrange its components
                HBox taskContainer = new HBox();
                CheckBox taskCheckbox = new CheckBox();
                //Button taskRemoveButton = new Button();
                TextFlow taskTextflow = new TextFlow();
                Text taskText = new Text();

                taskContainer.setPadding(new Insets(10,0,10,0));
                taskCheckbox.setScaleX(2);
                taskCheckbox.setScaleY(2);
                taskCheckbox.setPadding(new Insets(10,10,10,10));
                taskText.getStyleClass().add("taskText");

                taskContainer.getChildren().addAll(taskCheckbox, taskTextflow);
                taskTextflow.getChildren().add(taskText);

                //Fill the checklist item with the attributes retrieved from the
                taskCheckbox.setSelected(CHECKED);
                taskCheckbox.setId("chk" + ID);
                taskCheckbox.addEventFilter(MouseEvent.MOUSE_CLICKED,checkboxEH);
                taskText.setText(TASK);
                /*taskRemoveButton.setText("X");
                taskRemoveButton.setId("del" + ID);
                taskRemoveButton.addEventFilter(MouseEvent.MOUSE_CLICKED, taskRemoveEventHandler);*/

                switch (PRIORITY)
                {
                    case 1:
                        taskContainer.getStyleClass().add("mediumPriority");
                        break;
                    case 2:
                        taskContainer.getStyleClass().add("highPriority");
                        break;
                }

                checklistItems.getChildren().add(taskContainer);
            }

        }catch (SQLException e)
        {
            e.printStackTrace();
        }

        //Add all items to the list scrollpane
        listScrollPane.setContent(checklistItems);
    }

    private void refreshSelectedListView(String dateString)
    {
        String tableName = "T" + dateString.replace("-", "");
        ObservableList<String> tasks = FXCollections.observableArrayList();

        try {
            Statement getTableStatement = Main.conn.createStatement();
            String getTableQuery = "SELECT * FROM " + tableName;
            ResultSet result = getTableStatement.executeQuery(getTableQuery);

            while (result.next())
            {
                int ID = result.getInt(1);
                String TASK = result.getString(2);

                String listTask = TASK + " | " + ID;
                tasks.add(listTask);
            }

            getTableStatement.close();

        }catch (SQLException e)
        {
            e.printStackTrace();
        }

        selectedListView.setItems(tasks);
    }

    private void refreshRegularEventsListView()
    {
        ObservableList<String> tasks = FXCollections.observableArrayList();

        try {
            Statement getTableStatement = Main.conn.createStatement();
            String getTableQuery = "SELECT * FROM RegularEvents";
            ResultSet result = getTableStatement.executeQuery(getTableQuery);

            while (result.next())
            {
                int ID = result.getInt(1);
                String TASK = result.getString(2);
                String INTERVAL = result.getString(3);

                String listTask = TASK + " | " + INTERVAL + " | " + ID;
                tasks.add(listTask);
            }

            getTableStatement.close();

        }catch (SQLException e)
        {
            e.printStackTrace();
        }

        regularEventsList.setItems(tasks);
    }

    static void saveCheckMarks()
    {
        Iterator i = checkedBoxIDs.keySet().iterator();
        while (i.hasNext())
        {
            Integer boxID = (Integer) i.next();
            String checkedUpdateQuery = "UPDATE " + TODAY + " SET CHECKED=? WHERE ID=?";
            try {
                PreparedStatement checkedUpdateStatement = Main.conn.prepareStatement(checkedUpdateQuery);
                int checkedAsInt = (checkedBoxIDs.get(boxID)) ? 1 : 0;
                checkedUpdateStatement.setInt(1, checkedAsInt);
                checkedUpdateStatement.setInt(2, boxID);
                checkedUpdateStatement.executeUpdate();
                checkedUpdateStatement.close();
            }catch (SQLException e)
            {
                e.printStackTrace();
            }
        }
        //Only update what's in the database if something's been changed
        checkedBoxIDs.clear();
    }

    private void saveRegularEvents()
    {
        RadioButton selectedRadioButton = (RadioButton) repeatInterval.getSelectedToggle();
        String selectedRepeatInterval = selectedRadioButton.getText();
        String taskName = regularEventInput.getText(); //.strip()

        if (taskName.equals(""))
        {
            return;
        }

        String repeatData;
        if (selectedRepeatInterval.equals("Daily"))
        {
            repeatData = "D";
        }else if (selectedRepeatInterval.equals("Monthly"))
        {
            repeatData = "M" + monthlyRegularEventsChoiceBox.getValue();
        }else
        {
            repeatData = "W";
            for (Node wrecChild : weeklyRegularEventsContainer.getChildren())
            {
                CheckBox cbox = (CheckBox) wrecChild;

                if (cbox.isSelected())
                {
                    repeatData += cbox.getText().substring(0,2);
                }

                cbox.setSelected(false);
            }
        }

        if (repeatData.equals("W"))
        {
            return;
        }

        System.out.println(repeatData);

        Main.createRegularEventsTableIfNotExists();

        try {
            Statement addRegularEventStatement = Main.conn.createStatement();
            String addRegularEventQuery = "INSERT INTO RegularEvents (TASK, INTERVAL) VALUES ('" + taskName + "', '" + repeatData + "')";
            addRegularEventStatement.executeUpdate(addRegularEventQuery);
            addRegularEventStatement.close();
        }catch (SQLException e)
        {
            e.printStackTrace();
        }
    }

    private int priorityToInt()
    {
        String priorityValue = priorityButton.getValue();
        if (priorityValue.equals("high"))
        {
            return 2;
        }else if (priorityValue.equals("medium"))
        {
            return 1;
        }else {
            return 0;
        }
    }

    private void showNoTasks()
    {
        Date date = new Date();
        Text txt = new Text();
        String today = String.format("%1$tY-%1$tm-%1$td", date);
        txt.setText("No tasks found for " + today);
        listScrollPane.setContent(txt);
    }

}
