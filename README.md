# jfx-todolist-2
Successor to my previous to-do list application written in Java FX

# Requirements
Oracle Java 8

SQLite JDBC 3 (https://github.com/xerial/sqlite-jdbc/releases/tag/3.34.0)

# Installation (IntelliJ IDEA)
1. Download the project anywhere
2. Open the folder in IntelliJ IDEA
3. Set your JDK version as 1.8
4. Add the SQLite JDBC 3 as a library under File > Project Structure > Libraries
5. Create a new Application launch configuration, with sample.Main as the main class.
6. Launch the application.

# Building
1. Go to File > Project Structure > Artifacts
2. Create a new JavaFx application
3. In the Java FX tab, set sample.Main as the application class
4. In the Output Layout tab, add the SQLite JDBC 3 jar as an Extracted Directory
5. Apply your changes and close the window
6. Go to Build > Build Artifacts..., then choose the application you just created and select Build (or rebuild if you've already built it once)
7. The output should be in [project directory]/out/artifacts/[name-of-application]
8. If it doesn't already exist, create a folder called resources in this directory
9. Copy the CSS file to the new resources folder
10. You are free to move the application with the resources folder anywhere you'd like
11. Launch the application using Java 8
