package net.lshift.project.timetracker;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * Created by ceri on 29/11/16.
 */
public class NavBar {
    final static Logger log = LoggerFactory.getLogger(NavBar.class);
    private RemoteWebDriver webDriver;

    public NavBar(RemoteWebDriver webDriver) {
        this.webDriver = webDriver;
    }

    public Map<String, String> itemLabels() {
        return AppDriver.repeatAction(2,
            () -> webDriver.findElements(By.cssSelector(".nav-menu a")).stream()
                         .collect(toMap(
                                 e -> e.getText(),
                                 e -> e.getAttribute("href")
                         ))
                );
    }

    public void visitPage(String label) {
        webDriver.findElements(By.cssSelector(".nav-menu a"))
                .stream()
                .filter(e -> Objects.equals(e.getText().trim(), label))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No such navigation label: " + label))
                .click();
    }
}
