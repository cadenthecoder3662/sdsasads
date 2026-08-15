name: Build YTBlocks plugin

on:
  push:
    branches: [ main, master ]
  workflow_dispatch: {}

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Check out repository
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven

      - name: Build with Maven
        run: mvn -B package
        continue-on-error: true

      - name: Inspect paper-api jar contents (diagnostic)
        if: always()
        run: |
          JAR=$(find ~/.m2/repository/io/papermc/paper/paper-api -name "*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" | head -1)
          echo "Using jar: $JAR"
          mkdir -p /tmp/extracted
          unzip -o -q "$JAR" -d /tmp/extracted
          echo "===== Files matching material/block/logger ====="
          find /tmp/extracted -iname "*material*" -o -iname "*blocktype*" -o -iname "*itemtype*"
          echo "===== JavaPlugin public methods ====="
          javap -public /tmp/extracted/org/bukkit/plugin/java/JavaPlugin.class || echo "JavaPlugin.class not at that path"
          echo "===== CommandExecutor ====="
          javap -public /tmp/extracted/org/bukkit/command/CommandExecutor.class || echo "CommandExecutor.class not at that path"

      - name: Upload built plugin jar
        if: success()
        uses: actions/upload-artifact@v4
        with:
          name: YTBlocks-jar
          path: target/YTBlocks.jar
